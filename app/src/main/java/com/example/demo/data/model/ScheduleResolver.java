package com.example.demo.data.model;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Expands weekly rows and applies one-off exceptions into actual dated class meetings. */
public final class ScheduleResolver {
    private static final int MAX_OCCURRENCES = 50_000;

    private ScheduleResolver() {
    }

    public static ScheduleSnapshot resolve(List<ScheduleDetails> schedules,
                                           List<ScheduleExceptionEntity> exceptions,
                                           LocalDate fromInclusive,
                                           LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và kết thúc là bắt buộc.");
        }
        if (fromInclusive.isAfter(toInclusive)) {
            return new ScheduleSnapshot(fromInclusive, toInclusive, new ArrayList<>());
        }

        Map<Long, ScheduleDetails> schedulesById = indexSchedules(schedules);
        Map<OccurrenceKey, ClassOccurrence> resolved = new LinkedHashMap<>();
        for (ScheduleDetails schedule : schedulesById.values()) {
            addRecurringOccurrences(schedule, fromInclusive, toInclusive, resolved);
        }

        List<ScheduleExceptionEntity> orderedExceptions = exceptions == null
                ? new ArrayList<>() : new ArrayList<>(exceptions);
        orderedExceptions.sort(Comparator
                .comparingLong((ScheduleExceptionEntity item) -> item == null ? Long.MAX_VALUE : item.scheduleId)
                .thenComparingLong(item -> item == null ? Long.MAX_VALUE : item.originalDateEpochDay)
                .thenComparingLong(item -> item == null ? Long.MAX_VALUE : item.id));
        for (ScheduleExceptionEntity exception : orderedExceptions) {
            applyException(exception, schedulesById, fromInclusive, toInclusive, resolved);
        }

        if (resolved.size() > MAX_OCCURRENCES) {
            throw new IllegalArgumentException("Khoảng ngày có quá nhiều buổi học để xử lý an toàn.");
        }
        return new ScheduleSnapshot(fromInclusive, toInclusive,
                new ArrayList<>(resolved.values()));
    }

    private static Map<Long, ScheduleDetails> indexSchedules(List<ScheduleDetails> schedules) {
        Map<Long, ScheduleDetails> result = new LinkedHashMap<>();
        if (schedules == null) {
            return result;
        }
        for (ScheduleDetails schedule : schedules) {
            if (isValidSchedule(schedule)) {
                result.putIfAbsent(schedule.scheduleId, schedule);
            }
        }
        return result;
    }

    private static void addRecurringOccurrences(ScheduleDetails schedule,
                                                LocalDate fromInclusive,
                                                LocalDate toInclusive,
                                                Map<OccurrenceKey, ClassOccurrence> resolved) {
        long firstEpochDay = Math.max(fromInclusive.toEpochDay(), schedule.validFromEpochDay);
        long lastEpochDay = Math.min(toInclusive.toEpochDay(), schedule.validToEpochDay);
        if (firstEpochDay > lastEpochDay) {
            return;
        }
        LocalDate firstDate = LocalDate.ofEpochDay(firstEpochDay);
        int offset = Math.floorMod(schedule.dayOfWeek - firstDate.getDayOfWeek().getValue(), 7);
        long occurrenceEpochDay = firstEpochDay + offset;
        while (occurrenceEpochDay <= lastEpochDay) {
            LocalDate date = LocalDate.ofEpochDay(occurrenceEpochDay);
            OccurrenceKey key = new OccurrenceKey(schedule.scheduleId, occurrenceEpochDay);
            resolved.put(key, new ClassOccurrence(schedule, date, date,
                    schedule.startMinute, schedule.endMinute, schedule.room, schedule.note,
                    ClassOccurrence.Kind.RECURRING, 0L));
            if (resolved.size() > MAX_OCCURRENCES) {
                throw new IllegalArgumentException(
                        "Khoảng ngày có quá nhiều buổi học để xử lý an toàn.");
            }
            if (lastEpochDay - occurrenceEpochDay < 7L) {
                break;
            }
            occurrenceEpochDay += 7L;
        }
    }

    private static void applyException(ScheduleExceptionEntity exception,
                                       Map<Long, ScheduleDetails> schedulesById,
                                       LocalDate fromInclusive, LocalDate toInclusive,
                                       Map<OccurrenceKey, ClassOccurrence> resolved) {
        if (exception == null) {
            return;
        }
        ScheduleDetails schedule = schedulesById.get(exception.scheduleId);
        if (schedule == null) {
            return;
        }

        String type = exception.exceptionType == null
                ? "" : exception.exceptionType.trim().toUpperCase(Locale.ROOT);
        if (!ScheduleExceptionEntity.TYPE_CANCEL.equals(type)
                && !ScheduleExceptionEntity.TYPE_MODIFY.equals(type)
                && !ScheduleExceptionEntity.TYPE_MAKEUP.equals(type)) {
            return;
        }

        LocalDate originalDate = toDate(exception.originalDateEpochDay);
        if (originalDate == null || !isOccurrence(schedule, originalDate)) {
            return;
        }
        OccurrenceKey originalKey = new OccurrenceKey(
                schedule.scheduleId, exception.originalDateEpochDay);
        if (ScheduleExceptionEntity.TYPE_CANCEL.equals(type)) {
            resolved.remove(originalKey);
            return;
        }

        if ((exception.startMinute == null) != (exception.endMinute == null)) {
            return;
        }
        int startMinute = exception.startMinute == null
                ? schedule.startMinute : exception.startMinute;
        int endMinute = exception.endMinute == null
                ? schedule.endMinute : exception.endMinute;
        if (!isValidTime(startMinute, endMinute)) {
            return;
        }

        LocalDate actualDate = exception.replacementDateEpochDay == null
                ? originalDate : toDate(exception.replacementDateEpochDay);
        if (actualDate == null || (ScheduleExceptionEntity.TYPE_MAKEUP.equals(type)
                && actualDate.equals(originalDate))) {
            return;
        }

        resolved.remove(originalKey);
        if (actualDate.isBefore(fromInclusive) || actualDate.isAfter(toInclusive)) {
            return;
        }
        String room = exception.room == null ? schedule.room : exception.room;
        String exceptionNote = exception.note == null ? "" : exception.note.trim();
        String note = exceptionNote.isEmpty() ? schedule.note : exceptionNote;
        ClassOccurrence.Kind kind = ScheduleExceptionEntity.TYPE_MAKEUP.equals(type)
                ? ClassOccurrence.Kind.MAKEUP : ClassOccurrence.Kind.MODIFIED;
        OccurrenceKey actualKey = new OccurrenceKey(
                schedule.scheduleId, exception.originalDateEpochDay);
        resolved.put(actualKey, new ClassOccurrence(schedule, actualDate, originalDate,
                startMinute, endMinute, room, note, kind, exception.id));
    }

    private static boolean isOccurrence(ScheduleDetails schedule, LocalDate date) {
        long epochDay = date.toEpochDay();
        return date.getDayOfWeek().getValue() == schedule.dayOfWeek
                && epochDay >= schedule.validFromEpochDay
                && epochDay <= schedule.validToEpochDay;
    }

    private static boolean isValidSchedule(ScheduleDetails schedule) {
        return schedule != null && schedule.scheduleId > 0
                && schedule.dayOfWeek >= 1 && schedule.dayOfWeek <= 7
                && schedule.validFromEpochDay <= schedule.validToEpochDay
                && isValidTime(schedule.startMinute, schedule.endMinute);
    }

    private static boolean isValidTime(int startMinute, int endMinute) {
        return startMinute >= 0 && startMinute < 1_440
                && endMinute > startMinute && endMinute <= 1_440;
    }

    private static LocalDate toDate(Long epochDay) {
        if (epochDay == null) {
            return null;
        }
        try {
            return LocalDate.ofEpochDay(epochDay);
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private static final class OccurrenceKey {
        private final long scheduleId;
        private final long originalDateEpochDay;

        private OccurrenceKey(long scheduleId, long originalDateEpochDay) {
            this.scheduleId = scheduleId;
            this.originalDateEpochDay = originalDateEpochDay;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof OccurrenceKey)) return false;
            OccurrenceKey other = (OccurrenceKey) value;
            return scheduleId == other.scheduleId
                    && originalDateEpochDay == other.originalDateEpochDay;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(scheduleId);
            return 31 * result + Long.hashCode(originalDateEpochDay);
        }
    }
}
