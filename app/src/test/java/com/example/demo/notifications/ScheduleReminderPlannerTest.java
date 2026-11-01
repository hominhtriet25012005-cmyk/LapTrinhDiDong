package com.example.demo.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.schedule.ScheduleSnapshot;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScheduleReminderPlannerTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final Instant NOW = LocalDateTime.of(2026, 10, 19, 12, 0).atZone(ZONE).toInstant();

    @Test
    public void plansEachFutureWeeklyOccurrenceWithinLookahead() {
        ScheduleSnapshot data = snapshot(Collections.emptyList(),
                schedule(1, 3, 8 * 60, 9 * 60, true));

        List<ScheduleReminderPlanner.PlannedReminder> plan =
                ScheduleReminderPlanner.plan(data, NOW, ZONE, 14, true, 100);

        // Wednesdays Oct 21 and Oct 28 (reminder 24h before start still ahead of now).
        assertEquals(2, plan.size());
        assertEquals(MONDAY.plusDays(2).toEpochDay(), plan.get(0).occurrence.epochDay);
        assertEquals(MONDAY.plusDays(9).toEpochDay(), plan.get(1).occurrence.epochDay);
    }

    @Test
    public void cancelledOccurrenceGetsNoReminder() {
        long wednesday = MONDAY.plusDays(2).toEpochDay();
        ScheduleSnapshot data = snapshot(
                Collections.singletonList(ScheduleExceptionEntity.cancel(1, 10, wednesday, "nghi")),
                schedule(1, 3, 8 * 60, 9 * 60, true));

        List<ScheduleReminderPlanner.PlannedReminder> plan =
                ScheduleReminderPlanner.plan(data, NOW, ZONE, 14, true, 100);

        assertEquals(1, plan.size());
        assertEquals(MONDAY.plusDays(9).toEpochDay(), plan.get(0).occurrence.epochDay);
    }

    @Test
    public void modifiedOccurrenceUsesNewDateAndTime() {
        long wednesday = MONDAY.plusDays(2).toEpochDay();
        long friday = MONDAY.plusDays(4).toEpochDay();
        ScheduleSnapshot data = snapshot(
                Collections.singletonList(ScheduleExceptionEntity.modify(
                        1, 10, wednesday, friday, 14 * 60, 15 * 60, "B2", "")),
                schedule(1, 3, 8 * 60, 9 * 60, true));

        List<ScheduleReminderPlanner.PlannedReminder> plan =
                ScheduleReminderPlanner.plan(data, NOW, ZONE, 6, true, 100);

        assertEquals(1, plan.size());
        assertEquals(friday, plan.get(0).occurrence.epochDay);
        long expectedStart = LocalDateTime.of(2026, 10, 23, 14, 0).atZone(ZONE).toInstant().toEpochMilli();
        assertEquals(expectedStart, plan.get(0).classStartsAtMillis);
        assertEquals(expectedStart - 24 * 60 * 60_000L, plan.get(0).reminderAtMillis);
    }

    @Test
    public void makeupSessionIsRemindedEvenThoughItHasNoWeeklyFlag() {
        long friday = MONDAY.plusDays(4).toEpochDay();
        ScheduleSnapshot data = snapshot(
                Collections.singletonList(ScheduleExceptionEntity.makeup(
                        10, friday, 9 * 60, 11 * 60, "C3", "hoc bu")));

        List<ScheduleReminderPlanner.PlannedReminder> plan =
                ScheduleReminderPlanner.plan(data, NOW, ZONE, 14, true, 100);

        assertEquals(1, plan.size());
        assertEquals(0, plan.get(0).occurrence.scheduleId);
        assertTrue(plan.get(0).key.startsWith("0-"));
    }

    @Test
    public void disabledScheduleOrGlobalSwitchGetsNoReminder() {
        ScheduleSnapshot off = snapshot(Collections.emptyList(),
                schedule(1, 3, 8 * 60, 9 * 60, false));
        ScheduleSnapshot on = snapshot(Collections.emptyList(),
                schedule(1, 3, 8 * 60, 9 * 60, true));

        assertTrue(ScheduleReminderPlanner.plan(off, NOW, ZONE, 14, true, 100).isEmpty());
        assertTrue(ScheduleReminderPlanner.plan(on, NOW, ZONE, 14, false, 100).isEmpty());
    }

    @Test
    public void reminderAlreadyInThePastIsSkippedAndCountIsCapped() {
        // Class tomorrow 08:00 -> 24h reminder is today 08:00, already past at 12:00.
        ScheduleSnapshot data = snapshot(Collections.emptyList(),
                schedule(1, 2, 8 * 60, 9 * 60, true),
                schedule(2, 3, 8 * 60, 9 * 60, true),
                schedule(3, 4, 8 * 60, 9 * 60, true));

        List<ScheduleReminderPlanner.PlannedReminder> all =
                ScheduleReminderPlanner.plan(data, NOW, ZONE, 14, true, 100);
        for (ScheduleReminderPlanner.PlannedReminder reminder : all) {
            assertTrue(reminder.reminderAtMillis > NOW.toEpochMilli());
        }
        assertEquals(2, ScheduleReminderPlanner.plan(data, NOW, ZONE, 14, true, 2).size());
    }

    private static ScheduleSnapshot snapshot(List<ScheduleExceptionEntity> exceptions,
                                             ScheduleDetails... schedules) {
        ClassSectionDetails section = new ClassSectionDetails();
        section.classSectionId = 10;
        section.subjectName = "Mon hoc";
        return new ScheduleSnapshot(Arrays.asList(schedules), exceptions,
                Collections.singletonList(section));
    }

    private static ScheduleDetails schedule(long id, int dayOfWeek, int start, int end, boolean notify) {
        ScheduleDetails d = new ScheduleDetails();
        d.scheduleId = id;
        d.classSectionId = 10;
        d.dayOfWeek = dayOfWeek;
        d.startMinute = start;
        d.endMinute = end;
        d.validFromEpochDay = MONDAY.toEpochDay() - 30;
        d.validToEpochDay = MONDAY.toEpochDay() + 60;
        d.notificationEnabled = notify;
        d.reminderMinutesBefore = 1440;
        return d;
    }
}