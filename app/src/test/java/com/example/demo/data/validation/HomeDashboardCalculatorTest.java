package com.example.demo.data.validation;

import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;
import com.example.demo.settings.WeekCalculator;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HomeDashboardCalculatorTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Test
    public void calculatesTodayAndWeekOnlyForActualRecurringOccurrences() {
        List<ScheduleDetails> schedules = Arrays.asList(
                schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY.plusDays(4)),
                schedule(2, 3, 10 * 60, 11 * 60, MONDAY, MONDAY.plusDays(4)),
                schedule(3, 3, 12 * 60, 14 * 60, MONDAY.plusDays(2), MONDAY.plusDays(2)),
                schedule(4, 5, 15 * 60, 16 * 60, MONDAY, MONDAY.plusDays(4)),
                schedule(5, 2, 9 * 60, 10 * 60, MONDAY.plusDays(8), MONDAY.plusDays(12))
        );

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(schedules, MONDAY.plusDays(2), 8 * 60);

        assertEquals(2, summary.todaySchedules.size());
        assertEquals(4, summary.weekSchedules.size());
        assertEquals(180, summary.todayMinutes);
        assertEquals(300, summary.weekMinutes);
    }

    @Test
    public void selectsOngoingClassBeforeLaterUpcomingClass() {
        List<ScheduleDetails> schedules = Arrays.asList(
                schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY),
                schedule(2, 1, 10 * 60, 11 * 60, MONDAY, MONDAY),
                schedule(3, 1, 12 * 60, 13 * 60, MONDAY, MONDAY)
        );

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(schedules, MONDAY, 10 * 60 + 30);

        assertEquals(2, summary.nextClass.scheduleId);
        assertTrue(summary.nextClassOngoing);

        HomeDashboardCalculator.DashboardSummary atClassEnd =
                HomeDashboardCalculator.calculate(schedules, MONDAY, 11 * 60);
        assertEquals(3, atClassEnd.nextClass.scheduleId);
        assertFalse(atClassEnd.nextClassOngoing);
    }

    @Test
    public void returnsOnlyGapsBetweenConsecutiveClasses() {
        List<ScheduleDetails> schedules = Arrays.asList(
                schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY),
                schedule(2, 1, 10 * 60 + 30, 11 * 60 + 30, MONDAY, MONDAY),
                schedule(3, 1, 11 * 60 + 30, 12 * 60, MONDAY, MONDAY)
        );

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(schedules, MONDAY, 7 * 60);

        assertEquals(1, summary.freeTimeGaps.size());
        assertEquals(9 * 60, summary.freeTimeGaps.get(0).startMinute);
        assertEquals(10 * 60 + 30, summary.freeTimeGaps.get(0).endMinute);
    }

    @Test
    public void emptyDayHasNoNextClassOrFreeTime() {
        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(Collections.emptyList(), MONDAY, 8 * 60);

        assertTrue(summary.todaySchedules.isEmpty());
        assertTrue(summary.weekSchedules.isEmpty());
        assertTrue(summary.freeTimeGaps.isEmpty());
        assertEquals(0, summary.todayMinutes);
        assertEquals(0, summary.weekMinutes);
        assertNull(summary.nextClass);
    }

    @Test
    public void weekSummaryUsesConfiguredSundayStartAndOrdersFromSunday() {
        LocalDate sunday = LocalDate.of(2026, 3, 15);
        List<ScheduleDetails> schedules = Arrays.asList(
                schedule(1, 1, 8 * 60, 9 * 60, sunday.plusDays(1), sunday.plusDays(1)),
                schedule(2, 7, 10 * 60, 11 * 60, sunday, sunday),
                schedule(3, 7, 12 * 60, 13 * 60, sunday.plusDays(7), sunday.plusDays(7))
        );

        HomeDashboardCalculator.DashboardSummary summary = HomeDashboardCalculator.calculate(
                schedules, sunday.plusDays(3), 8 * 60, WeekCalculator.SUNDAY);

        assertEquals(2, summary.weekSchedules.size());
        assertEquals(2, summary.weekSchedules.get(0).scheduleId);
        assertEquals(1, summary.weekSchedules.get(1).scheduleId);
    }

    @Test
    public void dashboardUsesEffectiveMovedOccurrenceForCountsAndFreeTime() {
        ScheduleDetails monday = schedule(1, 1, 9 * 60, 10 * 60,
                MONDAY, MONDAY.plusDays(7));
        ScheduleDetails wednesday = schedule(2, 3, 8 * 60, 9 * 60,
                MONDAY, MONDAY.plusDays(7));
        ScheduleExceptionEntity moved = new ScheduleExceptionEntity();
        moved.scheduleId = monday.scheduleId;
        moved.originalDateEpochDay = MONDAY.toEpochDay();
        moved.exceptionType = ScheduleExceptionEntity.TYPE_MODIFY;
        moved.replacementDateEpochDay = MONDAY.plusDays(2).toEpochDay();
        moved.startMinute = 10 * 60;
        moved.endMinute = 11 * 60;
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(
                Arrays.asList(monday, wednesday), Collections.singletonList(moved),
                MONDAY, MONDAY.plusDays(6));

        HomeDashboardCalculator.DashboardSummary summary = HomeDashboardCalculator.calculate(
                snapshot, MONDAY.plusDays(2), 7 * 60, WeekCalculator.MONDAY);

        assertEquals(2, summary.todayOccurrences.size());
        assertEquals(2, summary.todaySchedules.size());
        assertEquals(120, summary.todayMinutes);
        assertEquals(2, summary.weekOccurrences.size());
        assertEquals(1, summary.freeTimeGaps.size());
        assertEquals(9 * 60, summary.freeTimeGaps.get(0).startMinute);
        assertEquals(10 * 60, summary.freeTimeGaps.get(0).endMinute);
    }

    private static ScheduleDetails schedule(long id, int dayOfWeek, int startMinute, int endMinute,
                                            LocalDate from, LocalDate to) {
        ScheduleDetails schedule = new ScheduleDetails();
        schedule.scheduleId = id;
        schedule.dayOfWeek = dayOfWeek;
        schedule.startMinute = startMinute;
        schedule.endMinute = endMinute;
        schedule.validFromEpochDay = from.toEpochDay();
        schedule.validToEpochDay = to.toEpochDay();
        return schedule;
    }
}
