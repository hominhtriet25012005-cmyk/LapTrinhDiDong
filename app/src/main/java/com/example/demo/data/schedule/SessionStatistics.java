package com.example.demo.data.schedule;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.time.LocalDate;
import java.util.Collections;

/** Workload statistics over the real sessions of a semester in a date range (cancelled ones excluded). */
public final class SessionStatistics {
    private static final long MAX_RANGE_DAYS = 3_660L;

    private SessionStatistics() {
    }

    public static final class Result {
        public final long fromEpochDay;
        public final long toEpochDay;
        public final boolean emptyRange;
        public final int sessionCount;
        public final long totalMinutes;
        public final int cancelledCount;
        public final int modifiedCount;
        public final int makeupCount;
        /** Index 0 = Monday ... 6 = Sunday, by the real date of each session. */
        public final int[] sessionsByDay;
        public final long[] minutesByDay;
        public final int weekCount;
        public final double sessionsPerWeek;
        public final double minutesPerWeek;

        Result(long from, long to, boolean emptyRange, int sessionCount, long totalMinutes,
               int cancelled, int modified, int makeup, int[] sessionsByDay, long[] minutesByDay) {
            this.fromEpochDay = from;
            this.toEpochDay = to;
            this.emptyRange = emptyRange;
            this.sessionCount = sessionCount;
            this.totalMinutes = totalMinutes;
            this.cancelledCount = cancelled;
            this.modifiedCount = modified;
            this.makeupCount = makeup;
            this.sessionsByDay = sessionsByDay;
            this.minutesByDay = minutesByDay;
            long days = emptyRange ? 0 : to - from + 1;
            this.weekCount = emptyRange ? 0 : (int) Math.max(1, (days + 6) / 7);
            this.sessionsPerWeek = weekCount == 0 ? 0 : sessionCount / (double) weekCount;
            this.minutesPerWeek = weekCount == 0 ? 0 : totalMinutes / (double) weekCount;
        }
    }

    /**
     * The whole span of a semester: its own dates when set, otherwise the span covered by its schedules.
     * Returns null when nothing can be derived.
     */
    public static long[] semesterSpan(ScheduleSnapshot snapshot, SemesterEntity semester) {
        Long start = semester.startDateEpochDay;
        Long end = semester.endDateEpochDay;
        if (start != null && end != null) {
            return end < start ? null : new long[]{start, end};
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (ScheduleDetails schedule : snapshot.schedules) {
            if (schedule == null || schedule.semesterId != semester.id
                    || schedule.validToEpochDay < schedule.validFromEpochDay) continue;
            min = Math.min(min, schedule.validFromEpochDay);
            max = Math.max(max, schedule.validToEpochDay);
        }
        if (min == Long.MAX_VALUE) {
            return null;
        }
        if (start != null) min = start;
        if (end != null) max = end;
        return max < min ? null : new long[]{min, max};
    }

    public static Result calculate(ScheduleSnapshot snapshot, SemesterEntity semester,
                                   long fromEpochDay, long toEpochDay) {
        if (snapshot == null || semester == null) {
            throw new IllegalArgumentException("Snapshot and semester are required.");
        }
        long from = fromEpochDay;
        long to = toEpochDay;
        if (semester.startDateEpochDay != null) from = Math.max(from, semester.startDateEpochDay);
        if (semester.endDateEpochDay != null) to = Math.min(to, semester.endDateEpochDay);
        if (to < from) {
            return empty(fromEpochDay, toEpochDay);
        }
        if (to - from + 1 > MAX_RANGE_DAYS) {
            to = from + MAX_RANGE_DAYS - 1;
        }

        int[] sessionsByDay = new int[7];
        long[] minutesByDay = new long[7];
        int sessions = 0;
        long minutes = 0;
        int cancelled = 0;
        int modified = 0;
        int makeup = 0;
        for (ClassOccurrence s : ScheduleResolver.resolve(snapshot, from, to, true)) {
            if (s.semesterId != semester.id) continue;
            if (s.isCancelled()) {
                cancelled++;
                continue;
            }
            int day = LocalDate.ofEpochDay(s.epochDay).getDayOfWeek().getValue() - 1;
            int duration = s.durationMinutes();
            sessionsByDay[day]++;
            minutesByDay[day] += duration;
            sessions++;
            minutes += duration;
            if (s.status == ClassOccurrence.STATUS_MODIFIED) modified++;
            if (s.status == ClassOccurrence.STATUS_MAKEUP) makeup++;
        }
        return new Result(from, to, false, sessions, minutes, cancelled, modified, makeup,
                sessionsByDay, minutesByDay);
    }

    private static Result empty(long from, long to) {
        return new Result(from, to, true, 0, 0, 0, 0, 0, new int[7], new long[7]);
    }

    /** Convenience for callers that have no schedules to show. */
    public static ScheduleSnapshot emptySnapshot() {
        return new ScheduleSnapshot(Collections.<ScheduleDetails>emptyList(), null, null);
    }
}