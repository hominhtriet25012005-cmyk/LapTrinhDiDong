package com.example.demo.data.export;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves export dates and expands recurring Room schedules into dated occurrences. */
public final class ScheduleExportCalculator {
    private static final int MAX_OCCURRENCES = 10_000;

    private ScheduleExportCalculator() {
    }

    public enum Scope {
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

        private Occurrence(LocalDate date, ScheduleDetails schedule) {
            this.date = date;
            this.schedule = schedule;
        }
    }

    public static DateRange resolveRange(Scope scope, LocalDate today, SemesterEntity semester,
                                         List<ScheduleDetails> schedules) {
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

        long start;
        long end;
        if (scope == Scope.SEMESTER) {
            if (semesterStart == null || semesterEnd == null) {
                return emptyRange();
            }
            start = semesterStart;
            end = semesterEnd;
        } else if (scope == Scope.CURRENT_WEEK) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            start = monday.toEpochDay();
            end = monday.plusDays(6).toEpochDay();
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
        List<Occurrence> result = new ArrayList<>();
        if (schedules == null || range == null || range.isEmpty()) return result;

        for (ScheduleDetails schedule : schedules) {
            if (schedule == null || schedule.semesterId != semesterId
                    || schedule.dayOfWeek < 1 || schedule.dayOfWeek > 7
                    || schedule.validFromEpochDay > schedule.validToEpochDay) {
                continue;
            }
            long firstDay = Math.max(range.startEpochDay, schedule.validFromEpochDay);
            long lastDay = Math.min(range.endEpochDay, schedule.validToEpochDay);
            if (firstDay > lastDay) continue;

            LocalDate firstDate = LocalDate.ofEpochDay(firstDay);
            int offset = (schedule.dayOfWeek - firstDate.getDayOfWeek().getValue() + 7) % 7;
            for (long epochDay = firstDay + offset; epochDay <= lastDay; epochDay += 7) {
                result.add(new Occurrence(LocalDate.ofEpochDay(epochDay), schedule));
                if (result.size() > MAX_OCCURRENCES) {
                    throw new IllegalArgumentException("Phạm vi có quá nhiều buổi học để xuất an toàn.");
                }
            }
        }
        result.sort(Comparator.comparing((Occurrence item) -> item.date)
                .thenComparingInt(item -> item.schedule.startMinute)
                .thenComparingLong(item -> item.schedule.scheduleId));
        return result;
    }

    private static DateRange emptyRange() {
        return new DateRange(1, 0);
    }
}
