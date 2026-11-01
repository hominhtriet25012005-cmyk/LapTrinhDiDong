package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Checks one changed occurrence against recurring and already moved occurrences on its date. */
public final class ScheduleExceptionConflictValidator {
    private ScheduleExceptionConflictValidator() {
    }

    public static boolean hasConflict(ScheduleExceptionEntity candidate,
                                      List<ScheduleEntity> schedules,
                                      List<ScheduleExceptionEntity> existingExceptions) {
        if (candidate == null || schedules == null || schedules.isEmpty()
                || ScheduleExceptionEntity.TYPE_CANCEL.equals(candidate.exceptionType)) {
            return false;
        }

        ScheduleEntity source = findSchedule(schedules, candidate.scheduleId);
        if (source == null || !isValidTime(candidate.startMinute == null
                ? source.startMinute : candidate.startMinute,
                candidate.endMinute == null ? source.endMinute : candidate.endMinute)) {
            return false;
        }
        long candidateDate = candidate.replacementDateEpochDay == null
                ? candidate.originalDateEpochDay : candidate.replacementDateEpochDay;
        LocalDate resolvedDate;
        try {
            resolvedDate = LocalDate.ofEpochDay(candidateDate);
        } catch (RuntimeException exception) {
            return false;
        }
        int candidateStart = candidate.startMinute == null ? source.startMinute : candidate.startMinute;
        int candidateEnd = candidate.endMinute == null ? source.endMinute : candidate.endMinute;

        List<ScheduleExceptionEntity> exceptions = new ArrayList<>();
        if (existingExceptions != null) {
            for (ScheduleExceptionEntity exception : existingExceptions) {
                if (exception != null && (candidate.id <= 0 || exception.id != candidate.id)) {
                    exceptions.add(exception);
                }
            }
        }
        exceptions.add(candidate);

        long dateEpochDay = resolvedDate.toEpochDay();
        for (ScheduleEntity schedule : schedules) {
            if (!isValidSchedule(schedule)) {
                continue;
            }
            ScheduleExceptionEntity sourceException = findException(
                    exceptions, schedule.id, dateEpochDay);
            if (isRecurringOccurrence(schedule, resolvedDate)) {
                if (sourceException == null) {
                    if (overlaps(candidateStart, candidateEnd,
                            schedule.startMinute, schedule.endMinute)) {
                        return true;
                    }
                } else if (isEffectiveOnOriginalDate(sourceException, dateEpochDay)) {
                    if (sourceException != candidate && overlaps(candidateStart, candidateEnd,
                            effectiveStart(sourceException, schedule),
                            effectiveEnd(sourceException, schedule))) {
                        return true;
                    }
                }
            }

            for (ScheduleExceptionEntity exception : exceptions) {
                if (exception.scheduleId != schedule.id
                        || exception.replacementDateEpochDay == null
                        || exception.replacementDateEpochDay != dateEpochDay
                        || exception.originalDateEpochDay == dateEpochDay
                        || exception == candidate
                        || !(ScheduleExceptionEntity.TYPE_MODIFY.equals(exception.exceptionType)
                        || ScheduleExceptionEntity.TYPE_MAKEUP.equals(exception.exceptionType))) {
                    continue;
                }
                if (overlaps(candidateStart, candidateEnd,
                        effectiveStart(exception, schedule), effectiveEnd(exception, schedule))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Checks a recurring schedule against moved or makeup occurrences already in the database. */
    public static boolean hasConflictWithRecurringSchedule(
            ScheduleEntity recurringCandidate,
            List<ScheduleEntity> schedules,
            List<ScheduleExceptionEntity> exceptions) {
        if (!isValidPattern(recurringCandidate) || schedules == null || exceptions == null) {
            return false;
        }

        for (ScheduleExceptionEntity exception : exceptions) {
            if (exception == null
                    || ScheduleExceptionEntity.TYPE_CANCEL.equals(exception.exceptionType)
                    || !(ScheduleExceptionEntity.TYPE_MODIFY.equals(exception.exceptionType)
                    || ScheduleExceptionEntity.TYPE_MAKEUP.equals(exception.exceptionType))) {
                continue;
            }

            long occurrenceEpochDay = exception.replacementDateEpochDay == null
                    ? exception.originalDateEpochDay : exception.replacementDateEpochDay;
            LocalDate occurrenceDate;
            try {
                occurrenceDate = LocalDate.ofEpochDay(occurrenceEpochDay);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!isRecurringOccurrence(recurringCandidate, occurrenceDate)) {
                continue;
            }

            // A same-day MODIFY replaces this schedule's own original occurrence.
            if (exception.scheduleId == recurringCandidate.id
                    && occurrenceEpochDay == exception.originalDateEpochDay) {
                continue;
            }

            ScheduleEntity source = findSchedule(schedules, exception.scheduleId);
            if (source == null) {
                continue;
            }
            int exceptionStart = effectiveStart(exception, source);
            int exceptionEnd = effectiveEnd(exception, source);
            if (isValidTime(exceptionStart, exceptionEnd)
                    && overlaps(recurringCandidate.startMinute, recurringCandidate.endMinute,
                    exceptionStart, exceptionEnd)) {
                return true;
            }
        }
        return false;
    }

    private static ScheduleEntity findSchedule(List<ScheduleEntity> schedules, long scheduleId) {
        for (ScheduleEntity schedule : schedules) {
            if (schedule != null && schedule.id == scheduleId) {
                return schedule;
            }
        }
        return null;
    }

    private static ScheduleExceptionEntity findException(List<ScheduleExceptionEntity> exceptions,
                                                          long scheduleId, long dateEpochDay) {
        for (ScheduleExceptionEntity exception : exceptions) {
            if (exception.scheduleId == scheduleId
                    && exception.originalDateEpochDay == dateEpochDay) {
                return exception;
            }
        }
        return null;
    }

    private static boolean isEffectiveOnOriginalDate(ScheduleExceptionEntity exception,
                                                       long dateEpochDay) {
        return ScheduleExceptionEntity.TYPE_MODIFY.equals(exception.exceptionType)
                && (exception.replacementDateEpochDay == null
                || exception.replacementDateEpochDay == dateEpochDay);
    }

    private static boolean isRecurringOccurrence(ScheduleEntity schedule, LocalDate date) {
        long dateEpochDay = date.toEpochDay();
        return date.getDayOfWeek().getValue() == schedule.dayOfWeek
                && dateEpochDay >= schedule.validFromEpochDay
                && dateEpochDay <= schedule.validToEpochDay;
    }

    private static boolean isValidSchedule(ScheduleEntity schedule) {
        return schedule != null && schedule.id > 0
                && isValidPattern(schedule);
    }

    private static boolean isValidPattern(ScheduleEntity schedule) {
        return schedule != null && schedule.dayOfWeek >= 1 && schedule.dayOfWeek <= 7
                && schedule.validFromEpochDay <= schedule.validToEpochDay
                && isValidTime(schedule.startMinute, schedule.endMinute);
    }

    private static boolean isValidTime(int startMinute, int endMinute) {
        return startMinute >= 0 && startMinute < 1_440
                && endMinute > startMinute && endMinute <= 1_440;
    }

    private static int effectiveStart(ScheduleExceptionEntity exception, ScheduleEntity schedule) {
        return exception.startMinute == null ? schedule.startMinute : exception.startMinute;
    }

    private static int effectiveEnd(ScheduleExceptionEntity exception, ScheduleEntity schedule) {
        return exception.endMinute == null ? schedule.endMinute : exception.endMinute;
    }

    private static boolean overlaps(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }
}
