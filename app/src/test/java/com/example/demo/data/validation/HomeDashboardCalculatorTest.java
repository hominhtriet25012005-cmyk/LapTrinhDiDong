package com.example.demo.data.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleSnapshot;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeDashboardCalculatorTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Test
    public void calculatesTodayAndWeekOnlyForActualRecurringOccurrences() {
        ScheduleSnapshot data = snapshot(
                schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY.plusDays(4)),
                schedule(2, 3, 10 * 60, 11 * 60, MONDAY, MONDAY.plusDays(4)),
                schedule(3, 3, 12 * 60, 14 * 60, MONDAY.plusDays(2), MONDAY.plusDays(2)),
                schedule(4, 5, 15 * 60, 16 * 60, MONDAY, MONDAY.plusDays(4)),
                schedule(5, 2, 9 * 60, 10 * 60, MONDAY.plusDays(8), MONDAY.plusDays(12))
        );

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(data, MONDAY.plusDays(2), 8 * 60);

        assertEquals(2, summary.todaySchedules.size());
        assertEquals(4, summary.weekSchedules.size());
        assertEquals(180, summary.todayMinutes);
        assertEquals(300, summary.weekMinutes);
    }

    @Test
    public void selectsOngoingClassBeforeLaterUpcomingClass() {
        ScheduleSnapshot data = snapshot(
                schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY),
                schedule(2, 1, 10 * 60, 11 * 60, MONDAY, MONDAY),
                schedule(3, 1, 12 * 60, 13 * 60, MONDAY, MONDAY)
        );

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(data, MONDAY, 10 * 60 + 30);

        assertEquals(2, summary.nextClass.scheduleId);
        assertTrue(summary.nextClassOngoing);

        HomeDashboardCalculator.DashboardSummary atClassEnd =
                HomeDashboardCalculator.calculate(data, MONDAY, 11 * 60);
        assertEquals(3, atClassEnd.nextClass.scheduleId);
        assertFalse(atClassEnd.nextClassOngoing);
    }

    @Test
    public void emptyDayHasNoNextClass() {
        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(snapshot(), MONDAY, 8 * 60);

        assertTrue(summary.todaySchedules.isEmpty());
        assertTrue(summary.weekSchedules.isEmpty());
        assertEquals(0, summary.todayMinutes);
        assertEquals(0, summary.weekMinutes);
        assertNull(summary.nextClass);
    }

    @Test
    public void cancelledSessionIsNotCountedOrChosenAsNextClass() {
        ScheduleSnapshot data = new ScheduleSnapshot(
                Arrays.asList(
                        schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY),
                        schedule(2, 1, 10 * 60, 11 * 60, MONDAY, MONDAY)),
                Collections.singletonList(
                        ScheduleExceptionEntity.cancel(1, 10, MONDAY.toEpochDay(), "GV nghi")),
                Collections.singletonList(section()));

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(data, MONDAY, 7 * 60);

        assertEquals(1, summary.todaySchedules.size());
        assertEquals(60, summary.todayMinutes);
        assertEquals(2, summary.nextClass.scheduleId);
    }

    @Test
    public void makeupAndModifiedSessionsAreIncluded() {
        ScheduleSnapshot data = new ScheduleSnapshot(
                Collections.singletonList(schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY)),
                Arrays.asList(
                        ScheduleExceptionEntity.modify(1, 10, MONDAY.toEpochDay(), null,
                                9 * 60, 11 * 60, "B2", ""),
                        ScheduleExceptionEntity.makeup(10, MONDAY.toEpochDay(),
                                13 * 60, 14 * 60, "C3", "Hoc bu")),
                Collections.singletonList(section()));

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(data, MONDAY, 7 * 60);

        assertEquals(2, summary.todaySchedules.size());
        assertEquals(ClassOccurrence.STATUS_MODIFIED, summary.todaySchedules.get(0).status);
        assertEquals(9 * 60, summary.nextClass.startMinute);
        assertEquals(ClassOccurrence.STATUS_MAKEUP, summary.todaySchedules.get(1).status);
        assertEquals(180, summary.todayMinutes);
    }

    @Test
    public void returnsOnlyGapsBetweenConsecutiveClasses() {
        ScheduleSnapshot data = snapshot(
                schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY),
                schedule(2, 1, 10 * 60 + 30, 11 * 60, MONDAY, MONDAY),
                schedule(3, 1, 11 * 60, 12 * 60, MONDAY, MONDAY)
        );

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(data, MONDAY, 7 * 60);

        assertEquals(1, summary.freeTimeGaps.size());
        assertEquals(9 * 60, summary.freeTimeGaps.get(0).startMinute);
        assertEquals(10 * 60 + 30, summary.freeTimeGaps.get(0).endMinute);
    }

    @Test
    public void cancelledSessionOpensUpFreeTime() {
        ScheduleSnapshot data = new ScheduleSnapshot(
                Arrays.asList(
                        schedule(1, 1, 8 * 60, 9 * 60, MONDAY, MONDAY),
                        schedule(2, 1, 9 * 60, 10 * 60, MONDAY, MONDAY),
                        schedule(3, 1, 11 * 60, 12 * 60, MONDAY, MONDAY)),
                Collections.singletonList(
                        ScheduleExceptionEntity.cancel(2, 10, MONDAY.toEpochDay(), "")),
                Collections.singletonList(section()));

        HomeDashboardCalculator.DashboardSummary summary =
                HomeDashboardCalculator.calculate(data, MONDAY, 7 * 60);

        assertEquals(1, summary.freeTimeGaps.size());
        assertEquals(9 * 60, summary.freeTimeGaps.get(0).startMinute);
        assertEquals(11 * 60, summary.freeTimeGaps.get(0).endMinute);
    }

    private static ScheduleSnapshot snapshot(ScheduleDetails... schedules) {
        List<ScheduleDetails> list = Arrays.asList(schedules);
        return new ScheduleSnapshot(list, Collections.emptyList(), Collections.singletonList(section()));
    }

    private static ClassSectionDetails section() {
        ClassSectionDetails section = new ClassSectionDetails();
        section.classSectionId = 10;
        section.subjectName = "Mon hoc";
        return section;
    }

    private static ScheduleDetails schedule(long id, int dayOfWeek, int startMinute, int endMinute,
                                            LocalDate from, LocalDate to) {
        ScheduleDetails schedule = new ScheduleDetails();
        schedule.scheduleId = id;
        schedule.classSectionId = 10;
        schedule.dayOfWeek = dayOfWeek;
        schedule.startMinute = startMinute;
        schedule.endMinute = endMinute;
        schedule.validFromEpochDay = from.toEpochDay();
        schedule.validToEpochDay = to.toEpochDay();
        return schedule;
    }
}