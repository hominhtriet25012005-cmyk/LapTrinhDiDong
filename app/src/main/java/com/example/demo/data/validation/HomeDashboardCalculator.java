package com.example.demo.data.validation;

import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleResolver;
import com.example.demo.data.schedule.ScheduleSnapshot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.example.demo.data.settings.WeekSettings;

/** Calculates the current-day and current-week schedule data shown on the home screen. */
public final class HomeDashboardCalculator {
    private HomeDashboardCalculator() {
    }

    public static DashboardSummary calculate(ScheduleSnapshot snapshot,
                                             LocalDate selectedDate,
                                             int currentMinute) {
        if (selectedDate == null) {
            throw new IllegalArgumentException("Selected date must not be null.");
        }
        ScheduleSnapshot safeSnapshot = snapshot == null
                ? new ScheduleSnapshot(null, null, null) : snapshot;
        int safeCurrentMinute = Math.max(0, Math.min(24 * 60 - 1, currentMinute));

        List<ClassOccurrence> todaySchedules =
                ScheduleResolver.resolveDay(safeSnapshot, selectedDate.toEpochDay(), false);
        LocalDate weekStart = WeekSettings.weekStart(selectedDate);
        List<ClassOccurrence> weekSchedules = ScheduleResolver.resolve(
                safeSnapshot, weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay(), false);

        ClassOccurrence nextClass = null;
        boolean nextClassOngoing = false;
        for (ClassOccurrence occurrence : todaySchedules) {
            if (occurrence.endMinute > safeCurrentMinute) {
                nextClass = occurrence;
                nextClassOngoing = occurrence.startMinute <= safeCurrentMinute;
                break;
            }
        }

        return new DashboardSummary(todaySchedules, weekSchedules,
                findFreeTimeGaps(todaySchedules),
                sumDuration(todaySchedules), sumDuration(weekSchedules),
                nextClass, nextClassOngoing);
    }

    /** Gaps between consecutive sessions of the day; sessions are assumed sorted by start. */
    private static List<FreeTimeGap> findFreeTimeGaps(List<ClassOccurrence> sessions) {
        List<FreeTimeGap> gaps = new ArrayList<>();
        if (sessions.size() < 2) {
            return gaps;
        }
        int coveredUntil = sessions.get(0).endMinute;
        for (int i = 1; i < sessions.size(); i++) {
            ClassOccurrence current = sessions.get(i);
            if (current.startMinute > coveredUntil) {
                gaps.add(new FreeTimeGap(coveredUntil, current.startMinute));
            }
            coveredUntil = Math.max(coveredUntil, current.endMinute);
        }
        return gaps;
    }

    private static int sumDuration(List<ClassOccurrence> occurrences) {
        int total = 0;
        for (ClassOccurrence occurrence : occurrences) {
            total += occurrence.durationMinutes();
        }
        return total;
    }

    public static final class DashboardSummary {
        public final List<ClassOccurrence> todaySchedules;
        public final List<ClassOccurrence> weekSchedules;
        public final List<FreeTimeGap> freeTimeGaps;
        public final int todayMinutes;
        public final int weekMinutes;
        public final ClassOccurrence nextClass;
        public final boolean nextClassOngoing;

        private DashboardSummary(List<ClassOccurrence> todaySchedules,
                                 List<ClassOccurrence> weekSchedules,
                                 List<FreeTimeGap> freeTimeGaps,
                                 int todayMinutes, int weekMinutes,
                                 ClassOccurrence nextClass, boolean nextClassOngoing) {
            this.todaySchedules = todaySchedules;
            this.weekSchedules = weekSchedules;
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