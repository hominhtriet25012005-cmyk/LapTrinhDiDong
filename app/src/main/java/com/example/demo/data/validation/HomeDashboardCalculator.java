package com.example.demo.data.validation;

import com.example.demo.data.local.projection.ScheduleDetails;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Calculates the current-day and current-week schedule data shown on the home screen. */
public final class HomeDashboardCalculator {
    private static final Comparator<ScheduleDetails> DAY_ORDER = Comparator
            .comparingInt((ScheduleDetails item) -> item.startMinute)
            .thenComparingInt(item -> item.endMinute)
            .thenComparingLong(item -> item.scheduleId);
    private static final Comparator<ScheduleDetails> WEEK_ORDER = Comparator
            .comparingInt((ScheduleDetails item) -> item.dayOfWeek)
            .thenComparing(DAY_ORDER);

    private HomeDashboardCalculator() {
    }

    public static DashboardSummary calculate(List<ScheduleDetails> schedules,
                                             LocalDate selectedDate,
                                             int currentMinute) {
        if (selectedDate == null) {
            throw new IllegalArgumentException("Selected date must not be null.");
        }
        List<ScheduleDetails> safeSchedules = schedules == null
                ? Collections.emptyList() : schedules;
        int safeCurrentMinute = Math.max(0, Math.min(24 * 60 - 1, currentMinute));

        List<ScheduleDetails> todaySchedules = new ArrayList<>();
        for (ScheduleDetails schedule : safeSchedules) {
            if (occursOn(schedule, selectedDate)) {
                todaySchedules.add(schedule);
            }
        }
        todaySchedules.sort(DAY_ORDER);

        LocalDate weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        List<ScheduleDetails> weekSchedules = new ArrayList<>();
        for (ScheduleDetails schedule : safeSchedules) {
            if (occursDuringWeek(schedule, weekStart, weekEnd)) {
                weekSchedules.add(schedule);
            }
        }
        weekSchedules.sort(WEEK_ORDER);

        int todayMinutes = sumDuration(todaySchedules);
        int weekMinutes = sumDuration(weekSchedules);
        ScheduleDetails nextClass = null;
        boolean nextClassOngoing = false;
        for (ScheduleDetails schedule : todaySchedules) {
            if (schedule.endMinute > safeCurrentMinute) {
                nextClass = schedule;
                nextClassOngoing = schedule.startMinute <= safeCurrentMinute;
                break;
            }
        }

        return new DashboardSummary(todaySchedules, weekSchedules,
                findFreeTimeGaps(todaySchedules), todayMinutes, weekMinutes,
                nextClass, nextClassOngoing);
    }

    private static boolean occursOn(ScheduleDetails schedule, LocalDate date) {
        if (!isValidSchedule(schedule) || date.getDayOfWeek().getValue() != schedule.dayOfWeek) {
            return false;
        }
        long epochDay = date.toEpochDay();
        return epochDay >= schedule.validFromEpochDay && epochDay <= schedule.validToEpochDay;
    }

    private static boolean occursDuringWeek(ScheduleDetails schedule,
                                             LocalDate weekStart, LocalDate weekEnd) {
        if (!isValidSchedule(schedule)) {
            return false;
        }
        LocalDate occurrence = weekStart.plusDays(schedule.dayOfWeek - 1L);
        long epochDay = occurrence.toEpochDay();
        return !occurrence.isBefore(weekStart) && !occurrence.isAfter(weekEnd)
                && epochDay >= schedule.validFromEpochDay && epochDay <= schedule.validToEpochDay;
    }

    private static boolean isValidSchedule(ScheduleDetails schedule) {
        return schedule != null
                && schedule.dayOfWeek >= 1 && schedule.dayOfWeek <= 7
                && schedule.startMinute >= 0 && schedule.startMinute < schedule.endMinute
                && schedule.endMinute <= 24 * 60;
    }

    private static int sumDuration(List<ScheduleDetails> schedules) {
        int total = 0;
        for (ScheduleDetails schedule : schedules) {
            total += schedule.endMinute - schedule.startMinute;
        }
        return total;
    }

    private static List<FreeTimeGap> findFreeTimeGaps(List<ScheduleDetails> schedules) {
        List<FreeTimeGap> gaps = new ArrayList<>();
        if (schedules.size() < 2) {
            return gaps;
        }
        int previousEnd = schedules.get(0).endMinute;
        for (int index = 1; index < schedules.size(); index++) {
            ScheduleDetails current = schedules.get(index);
            if (current.startMinute > previousEnd) {
                gaps.add(new FreeTimeGap(previousEnd, current.startMinute));
            }
            previousEnd = Math.max(previousEnd, current.endMinute);
        }
        return gaps;
    }

    public static final class DashboardSummary {
        public final List<ScheduleDetails> todaySchedules;
        public final List<ScheduleDetails> weekSchedules;
        public final List<FreeTimeGap> freeTimeGaps;
        public final int todayMinutes;
        public final int weekMinutes;
        public final ScheduleDetails nextClass;
        public final boolean nextClassOngoing;

        private DashboardSummary(List<ScheduleDetails> todaySchedules,
                                 List<ScheduleDetails> weekSchedules,
                                 List<FreeTimeGap> freeTimeGaps,
                                 int todayMinutes, int weekMinutes,
                                 ScheduleDetails nextClass, boolean nextClassOngoing) {
            this.todaySchedules = Collections.unmodifiableList(new ArrayList<>(todaySchedules));
            this.weekSchedules = Collections.unmodifiableList(new ArrayList<>(weekSchedules));
            this.freeTimeGaps = Collections.unmodifiableList(new ArrayList<>(freeTimeGaps));
            this.todayMinutes = todayMinutes;
            this.weekMinutes = weekMinutes;
            this.nextClass = nextClass;
            this.nextClassOngoing = nextClassOngoing;
        }
    }

    public static final class FreeTimeGap {
        public final int startMinute;
        public final int endMinute;

        private FreeTimeGap(int startMinute, int endMinute) {
            this.startMinute = startMinute;
            this.endMinute = endMinute;
        }

        public int durationMinutes() {
            return endMinute - startMinute;
        }
    }
}
