package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ClassOccurrence;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;
import com.example.demo.settings.WeekCalculator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Calculates dashboard values from the same effective dated occurrences used by other modules. */
public final class HomeDashboardCalculator {
    private static final Comparator<ClassOccurrence> TIME_ORDER = Comparator
            .comparingInt((ClassOccurrence item) -> item.startMinute)
            .thenComparingInt(item -> item.endMinute)
            .thenComparingLong(item -> item.schedule.scheduleId)
            .thenComparing(item -> item.originalDate);

    private HomeDashboardCalculator() {
    }

    public static DashboardSummary calculate(List<ScheduleDetails> schedules,
                                             LocalDate selectedDate,
                                             int currentMinute) {
        return calculate(schedules, selectedDate, currentMinute, WeekCalculator.MONDAY);
    }

    /** Backward-compatible entry point for callers that do not yet load exception rows. */
    public static DashboardSummary calculate(List<ScheduleDetails> schedules,
                                             LocalDate selectedDate,
                                             int currentMinute,
                                             int firstDayOfWeek) {
        return calculate(schedules, Collections.<ScheduleExceptionEntity>emptyList(),
                selectedDate, currentMinute, firstDayOfWeek);
    }

    public static DashboardSummary calculate(List<ScheduleDetails> schedules,
                                             List<ScheduleExceptionEntity> exceptions,
                                             LocalDate selectedDate,
                                             int currentMinute,
                                             int firstDayOfWeek) {
        if (selectedDate == null) {
            throw new IllegalArgumentException("Selected date must not be null.");
        }
        LocalDate weekStart = WeekCalculator.startOfWeek(selectedDate, firstDayOfWeek);
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(schedules, exceptions,
                weekStart, weekStart.plusDays(6));
        return calculate(snapshot, selectedDate, currentMinute, firstDayOfWeek);
    }

    public static DashboardSummary calculate(ScheduleSnapshot snapshot,
                                             LocalDate selectedDate,
                                             int currentMinute,
                                             int firstDayOfWeek) {
        if (selectedDate == null) {
            throw new IllegalArgumentException("Selected date must not be null.");
        }
        LocalDate weekStart = WeekCalculator.startOfWeek(selectedDate, firstDayOfWeek);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<ClassOccurrence> todayOccurrences = snapshot == null
                ? new ArrayList<>() : new ArrayList<>(snapshot.on(selectedDate));
        List<ClassOccurrence> weekOccurrences = snapshot == null
                ? new ArrayList<>() : new ArrayList<>(snapshot.between(weekStart, weekEnd));
        todayOccurrences.sort(TIME_ORDER);
        weekOccurrences.sort(Comparator
                .comparingInt((ClassOccurrence item) ->
                        WeekCalculator.weekdayOrder(item.date.getDayOfWeek().getValue(), firstDayOfWeek))
                .thenComparing(TIME_ORDER));

        int todayMinutes = sumDuration(todayOccurrences);
        int weekMinutes = sumDuration(weekOccurrences);
        int safeCurrentMinute = Math.max(0, Math.min(24 * 60 - 1, currentMinute));
        ClassOccurrence nextOccurrence = null;
        boolean nextClassOngoing = false;
        for (ClassOccurrence occurrence : todayOccurrences) {
            if (occurrence.endMinute > safeCurrentMinute) {
                nextOccurrence = occurrence;
                nextClassOngoing = occurrence.startMinute <= safeCurrentMinute;
                break;
            }
        }

        return new DashboardSummary(todayOccurrences, weekOccurrences,
                findFreeTimeGaps(todayOccurrences), todayMinutes, weekMinutes,
                nextOccurrence, nextClassOngoing);
    }

    private static int sumDuration(List<ClassOccurrence> occurrences) {
        int total = 0;
        for (ClassOccurrence occurrence : occurrences) {
            total += occurrence.endMinute - occurrence.startMinute;
        }
        return total;
    }

    private static List<FreeTimeGap> findFreeTimeGaps(List<ClassOccurrence> occurrences) {
        List<FreeTimeGap> gaps = new ArrayList<>();
        if (occurrences.size() < 2) {
            return gaps;
        }
        int previousEnd = occurrences.get(0).endMinute;
        for (int index = 1; index < occurrences.size(); index++) {
            ClassOccurrence current = occurrences.get(index);
            if (current.startMinute > previousEnd) {
                gaps.add(new FreeTimeGap(previousEnd, current.startMinute));
            }
            previousEnd = Math.max(previousEnd, current.endMinute);
        }
        return gaps;
    }

    private static List<ScheduleDetails> asScheduleDetails(List<ClassOccurrence> occurrences) {
        List<ScheduleDetails> result = new ArrayList<>(occurrences.size());
        for (ClassOccurrence occurrence : occurrences) {
            result.add(occurrence.toScheduleDetails());
        }
        return result;
    }

    public static final class DashboardSummary {
        public final List<ClassOccurrence> todayOccurrences;
        public final List<ClassOccurrence> weekOccurrences;
        public final List<ScheduleDetails> todaySchedules;
        public final List<ScheduleDetails> weekSchedules;
        public final List<FreeTimeGap> freeTimeGaps;
        public final int todayMinutes;
        public final int weekMinutes;
        public final ClassOccurrence nextOccurrence;
        /** Compatibility projection used by the current home view. */
        public final ScheduleDetails nextClass;
        public final boolean nextClassOngoing;

        private DashboardSummary(List<ClassOccurrence> todayOccurrences,
                                 List<ClassOccurrence> weekOccurrences,
                                 List<FreeTimeGap> freeTimeGaps,
                                 int todayMinutes, int weekMinutes,
                                 ClassOccurrence nextOccurrence, boolean nextClassOngoing) {
            this.todayOccurrences = Collections.unmodifiableList(new ArrayList<>(todayOccurrences));
            this.weekOccurrences = Collections.unmodifiableList(new ArrayList<>(weekOccurrences));
            this.todaySchedules = Collections.unmodifiableList(asScheduleDetails(todayOccurrences));
            this.weekSchedules = Collections.unmodifiableList(asScheduleDetails(weekOccurrences));
            this.freeTimeGaps = Collections.unmodifiableList(new ArrayList<>(freeTimeGaps));
            this.todayMinutes = todayMinutes;
            this.weekMinutes = weekMinutes;
            this.nextOccurrence = nextOccurrence;
            this.nextClass = nextOccurrence == null ? null : nextOccurrence.toScheduleDetails();
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
