package com.example.demo.data.export;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ClassOccurrence;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;
import com.example.demo.settings.WeekCalculator;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves export dates and expands recurring Room schedules into dated occurrences. */
public final class ScheduleExportCalculator {
    private static final int MAX_OCCURRENCES = 10_000;

    private ScheduleExportCalculator() {
    }

    public enum Scope {
        CURRENT_DAY,
        CURRENT_WEEK,
        CURRENT_MONTH,
        SEMESTER
    }

    public static final class DateRange {
        public final long startEpochDay;
        public final long endEpochDay;

        public DateRange(long startEpochDay, long endEpochDay) {
            this.startEpochDay = startEpochDay;
            this.endEpochDay = endEpochDay;
        }

        public boolean isEmpty() {
            return startEpochDay > endEpochDay;
        }
    }

    public static final class Occurrence {
        public final LocalDate date;
        public final ScheduleDetails schedule;
        public final ClassOccurrence classOccurrence;

        private Occurrence(ClassOccurrence classOccurrence) {
            this.date = classOccurrence.date;
            this.schedule = classOccurrence.toScheduleDetails();
            this.classOccurrence = classOccurrence;
        }
    }

    public static DateRange resolveRange(Scope scope, LocalDate today, SemesterEntity semester,
                                         List<ScheduleDetails> schedules) {
        return resolveRange(scope, today, semester, schedules, WeekCalculator.MONDAY);
    }

    public static DateRange resolveRange(Scope scope, LocalDate today, SemesterEntity semester,
                                         List<ScheduleDetails> schedules, int firstDayOfWeek) {
        return resolveRange(scope, today, semester, schedules,
                Collections.<ScheduleExceptionEntity>emptyList(), firstDayOfWeek);
    }

    public static DateRange resolveRange(Scope scope, LocalDate today, SemesterEntity semester,
                                         List<ScheduleDetails> schedules,
                                         List<ScheduleExceptionEntity> exceptions,
                                         int firstDayOfWeek) {
        if (scope == null || today == null || semester == null) {
            throw new IllegalArgumentException("Scope, date and semester are required.");
        }

        Long semesterStart = semester.startDateEpochDay;
        Long semesterEnd = semester.endDateEpochDay;
        if (schedules != null) {
            for (ScheduleDetails schedule : schedules) {
                if (schedule == null || schedule.semesterId != semester.id
                        || schedule.validFromEpochDay > schedule.validToEpochDay) {
                    continue;
                }
                if (semester.startDateEpochDay == null
                        && (semesterStart == null || schedule.validFromEpochDay < semesterStart)) {
                    semesterStart = schedule.validFromEpochDay;
                }
                if (semester.endDateEpochDay == null
                        && (semesterEnd == null || schedule.validToEpochDay > semesterEnd)) {
                    semesterEnd = schedule.validToEpochDay;
                }
            }
        }
        if (exceptions != null) {
            for (ScheduleExceptionEntity exception : exceptions) {
                if (exception == null || exception.replacementDateEpochDay == null
                        || (semester.startDateEpochDay != null
                        && semester.endDateEpochDay != null)) {
                    continue;
                }
                ScheduleDetails source = findSchedule(schedules, exception.scheduleId);
                if (source == null || source.semesterId != semester.id
                        || !isValidEpochDay(exception.replacementDateEpochDay)) {
                    continue;
                }
                if (semester.startDateEpochDay == null
                        && (semesterStart == null
                        || exception.replacementDateEpochDay < semesterStart)) {
                    semesterStart = exception.replacementDateEpochDay;
                }
                if (semester.endDateEpochDay == null
                        && (semesterEnd == null
                        || exception.replacementDateEpochDay > semesterEnd)) {
                    semesterEnd = exception.replacementDateEpochDay;
                }
            }
        }

        long start;
        long end;
        if (scope == Scope.SEMESTER) {
            if (semesterStart == null || semesterEnd == null) {
                return emptyRange();
            }
            start = semesterStart;
            end = semesterEnd;
        } else if (scope == Scope.CURRENT_DAY) {
            start = today.toEpochDay();
            end = start;
            if (semesterStart != null) start = Math.max(start, semesterStart);
            if (semesterEnd != null) end = Math.min(end, semesterEnd);
        } else if (scope == Scope.CURRENT_WEEK) {
            LocalDate weekStart = WeekCalculator.startOfWeek(today, firstDayOfWeek);
            start = weekStart.toEpochDay();
            end = weekStart.plusDays(6).toEpochDay();
            if (semesterStart != null) start = Math.max(start, semesterStart);
            if (semesterEnd != null) end = Math.min(end, semesterEnd);
        } else {
            LocalDate firstDay = today.withDayOfMonth(1);
            start = firstDay.toEpochDay();
            end = firstDay.with(TemporalAdjusters.lastDayOfMonth()).toEpochDay();
            if (semesterStart != null) start = Math.max(start, semesterStart);
            if (semesterEnd != null) end = Math.min(end, semesterEnd);
        }
        return new DateRange(start, end);
    }

    public static List<Occurrence> occurrences(long semesterId, List<ScheduleDetails> schedules,
                                                DateRange range) {
        if (range == null || range.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ScheduleSnapshot snapshot = ScheduleResolver.resolve(schedules,
                    Collections.<ScheduleExceptionEntity>emptyList(),
                    LocalDate.ofEpochDay(range.startEpochDay),
                    LocalDate.ofEpochDay(range.endEpochDay));
            return occurrences(semesterId, snapshot, range);
        } catch (DateTimeException exception) {
            return new ArrayList<>();
        }
    }

    public static List<Occurrence> occurrences(long semesterId, List<ScheduleDetails> schedules,
                                                List<ScheduleExceptionEntity> exceptions,
                                                DateRange range) {
        if (range == null || range.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ScheduleSnapshot snapshot = ScheduleResolver.resolve(schedules, exceptions,
                    LocalDate.ofEpochDay(range.startEpochDay),
                    LocalDate.ofEpochDay(range.endEpochDay));
            return occurrences(semesterId, snapshot, range);
        } catch (DateTimeException exception) {
            return new ArrayList<>();
        }
    }

    public static List<Occurrence> occurrences(long semesterId, ScheduleSnapshot snapshot,
                                                DateRange range) {
        List<Occurrence> result = new ArrayList<>();
        if (snapshot == null || range == null || range.isEmpty()) return result;
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.ofEpochDay(range.startEpochDay);
            to = LocalDate.ofEpochDay(range.endEpochDay);
        } catch (DateTimeException exception) {
            return result;
        }
        for (ClassOccurrence occurrence : snapshot.between(from, to)) {
            if (occurrence.schedule.semesterId == semesterId) {
                result.add(new Occurrence(occurrence));
                if (result.size() > MAX_OCCURRENCES) {
                    throw new IllegalArgumentException("Phạm vi có quá nhiều buổi học để xuất an toàn.");
                }
            }
        }
        return result;
    }

    private static DateRange emptyRange() {
        return new DateRange(1, 0);
    }

    private static ScheduleDetails findSchedule(List<ScheduleDetails> schedules, long scheduleId) {
        if (schedules == null) return null;
        for (ScheduleDetails schedule : schedules) {
            if (schedule != null && schedule.scheduleId == scheduleId) return schedule;
        }
        return null;
    }

    private static boolean isValidEpochDay(long epochDay) {
        try {
            LocalDate.ofEpochDay(epochDay);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }
}
