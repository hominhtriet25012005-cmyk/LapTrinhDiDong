package com.example.demo.data.schedule;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns weekly schedules plus exceptions into the concrete sessions of a date range.
 * Every screen (home, day/week/month, statistics, notifications, export) should use this
 * so they all agree on what happens on a given day.
 */
public final class ScheduleResolver {
    private static final Comparator<ClassOccurrence> ORDER = Comparator
            .comparingLong((ClassOccurrence item) -> item.epochDay)
            .thenComparingInt(item -> item.startMinute)
            .thenComparingInt(item -> item.endMinute)
            .thenComparingLong(item -> item.scheduleId)
            .thenComparingLong(item -> item.exceptionId);

    private ScheduleResolver() {
    }

    public static List<ClassOccurrence> resolveDay(ScheduleSnapshot snapshot, long epochDay,
                                                   boolean includeCancelled) {
        return resolve(snapshot, epochDay, epochDay, includeCancelled);
    }

    /** Resolves every session from fromEpochDay to toEpochDay inclusive, sorted by date and time. */
    public static List<ClassOccurrence> resolve(ScheduleSnapshot snapshot, long fromEpochDay,
                                                long toEpochDay, boolean includeCancelled) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot must not be null.");
        }
        if (toEpochDay < fromEpochDay) {
            throw new IllegalArgumentException("End date must not be before start date.");
        }

        Map<Long, ScheduleDetails> schedulesById = new HashMap<>();
        for (ScheduleDetails schedule : snapshot.schedules) {
            if (isValid(schedule)) {
                schedulesById.put(schedule.scheduleId, schedule);
            }
        }
        Map<Long, ClassSectionDetails> sectionsById = new HashMap<>();
        for (ClassSectionDetails section : snapshot.classSections) {
            sectionsById.put(section.classSectionId, section);
        }
        Map<String, ScheduleExceptionEntity> byOccurrence = new HashMap<>();
        for (ScheduleExceptionEntity exception : snapshot.exceptions) {
            if (exception.scheduleId != null && exception.originalEpochDay != null) {
                byOccurrence.put(key(exception.scheduleId, exception.originalEpochDay), exception);
            }
        }

        List<ClassOccurrence> result = new ArrayList<>();

        for (ScheduleDetails schedule : schedulesById.values()) {
            long first = Math.max(fromEpochDay, schedule.validFromEpochDay);
            long last = Math.min(toEpochDay, schedule.validToEpochDay);
            if (first > last) {
                continue;
            }
            long day = first + Math.floorMod(schedule.dayOfWeek - isoDay(first), 7);
            for (; day <= last; day += 7) {
                ScheduleExceptionEntity exception = byOccurrence.get(key(schedule.scheduleId, day));
                if (exception == null) {
                    result.add(ClassOccurrence.regular(schedule, day));
                } else if (ScheduleExceptionEntity.TYPE_CANCEL.equals(exception.type)) {
                    if (includeCancelled) {
                        result.add(ClassOccurrence.cancelled(schedule, exception, day));
                    }
                } else if (ScheduleExceptionEntity.TYPE_MODIFY.equals(exception.type)) {
                    boolean moved = exception.newEpochDay != null && exception.newEpochDay != day;
                    if (!moved) {
                        result.add(ClassOccurrence.modified(schedule, exception, day));
                    }
                } else {
                    result.add(ClassOccurrence.regular(schedule, day));
                }
            }
        }

        for (ScheduleExceptionEntity exception : snapshot.exceptions) {
            if (exception.newEpochDay == null || exception.newEpochDay < fromEpochDay
                    || exception.newEpochDay > toEpochDay) {
                continue;
            }
            if (ScheduleExceptionEntity.TYPE_MODIFY.equals(exception.type)
                    && exception.scheduleId != null && exception.originalEpochDay != null
                    && !exception.newEpochDay.equals(exception.originalEpochDay)) {
                ScheduleDetails schedule = schedulesById.get(exception.scheduleId);
                if (schedule != null) {
                    result.add(ClassOccurrence.modified(schedule, exception, exception.newEpochDay));
                }
            } else if (ScheduleExceptionEntity.TYPE_MAKEUP.equals(exception.type)) {
                ClassSectionDetails section = sectionsById.get(exception.classSectionId);
                if (section != null && exception.newStartMinute != null
                        && exception.newEndMinute != null) {
                    result.add(ClassOccurrence.makeup(section, exception));
                }
            }
        }

        result.sort(ORDER);
        return Collections.unmodifiableList(result);
    }

    /**
     * Sessions of one day that overlap [startMinute, endMinute). Cancelled sessions are ignored,
     * and so is the session being edited (pass its scheduleId and/or exceptionId, or 0 for none).
     */
    public static List<ClassOccurrence> findOverlaps(List<ClassOccurrence> dayOccurrences,
                                                     int startMinute, int endMinute,
                                                     long ignoreScheduleId, long ignoreExceptionId) {
        List<ClassOccurrence> overlaps = new ArrayList<>();
        if (dayOccurrences == null) {
            return overlaps;
        }
        for (ClassOccurrence item : dayOccurrences) {
            if (item.isCancelled()) {
                continue;
            }
            if (ignoreScheduleId > 0 && item.scheduleId == ignoreScheduleId) {
                continue;
            }
            if (ignoreExceptionId > 0 && item.exceptionId == ignoreExceptionId) {
                continue;
            }
            if (startMinute < item.endMinute && item.startMinute < endMinute) {
                overlaps.add(item);
            }
        }
        return overlaps;
    }

    private static boolean isValid(ScheduleDetails schedule) {
        return schedule != null
                && schedule.dayOfWeek >= 1 && schedule.dayOfWeek <= 7
                && schedule.startMinute >= 0 && schedule.startMinute < schedule.endMinute
                && schedule.endMinute <= 24 * 60;
    }

    /** ISO weekday of an epoch day: Monday = 1 ... Sunday = 7 (1970-01-01 was a Thursday). */
    private static int isoDay(long epochDay) {
        return (int) Math.floorMod(epochDay + 3L, 7L) + 1;
    }

    private static String key(long scheduleId, long epochDay) {
        return scheduleId + ":" + epochDay;
    }
}