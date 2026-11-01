package com.example.demo.notifications;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduleReminderPlannerTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate ORIGINAL_DATE = LocalDate.of(2026, 11, 2);

    @Test
    public void cancellationIsSkippedAndNextRealOccurrenceIsPlanned() {
        ScheduleDetails schedule = schedule();
        ScheduleExceptionEntity cancellation = exception(
                ScheduleExceptionEntity.TYPE_CANCEL, ORIGINAL_DATE, null);
        ScheduleSnapshot snapshot = snapshot(schedule, Collections.singletonList(cancellation),
                ORIGINAL_DATE, ORIGINAL_DATE.plusDays(14));
        Instant now = LocalDateTime.of(2026, 11, 1, 8, 0).atZone(ZONE).toInstant();

        List<ScheduleReminderPlanner.Reminder> reminders =
                ScheduleReminderPlanner.planNextPerSchedule(snapshot, now, ZONE, true);

        assertEquals(1, reminders.size());
        assertEquals(ORIGINAL_DATE.plusDays(7).toEpochDay(),
                reminders.get(0).occurrenceDateEpochDay);
    }

    @Test
    public void modifiedOccurrenceUsesReplacementDateTimeAndRoom() {
        ScheduleDetails schedule = schedule();
        LocalDate replacementDate = ORIGINAL_DATE.plusDays(1);
        ScheduleExceptionEntity modification = exception(
                ScheduleExceptionEntity.TYPE_MODIFY, ORIGINAL_DATE, replacementDate);
        modification.id = 41;
        modification.startMinute = 11 * 60 + 30;
        modification.endMinute = 13 * 60;
        modification.room = "B204";
        ScheduleSnapshot snapshot = snapshot(schedule, Collections.singletonList(modification),
                ORIGINAL_DATE, ORIGINAL_DATE.plusDays(14));
        Instant now = LocalDateTime.of(2026, 11, 1, 8, 0).atZone(ZONE).toInstant();

        List<ScheduleReminderPlanner.Reminder> reminders =
                ScheduleReminderPlanner.planNextPerSchedule(snapshot, now, ZONE, true);

        assertEquals(1, reminders.size());
        ScheduleReminderPlanner.Reminder reminder = reminders.get(0);
        assertEquals(replacementDate.toEpochDay(), reminder.occurrenceDateEpochDay);
        assertEquals(41L, reminder.exceptionId);
        assertEquals("B204", reminder.occurrence.room);
        assertEquals(LocalDateTime.of(2026, 11, 2, 11, 30).atZone(ZONE).toInstant().toEpochMilli(),
                reminder.reminderAtMillis);
    }

    @Test
    public void makeupOccurrenceIsPlannedOnItsActualDate() {
        ScheduleDetails schedule = schedule();
        LocalDate makeupDate = ORIGINAL_DATE.plusDays(2);
        ScheduleExceptionEntity makeup = exception(
                ScheduleExceptionEntity.TYPE_MAKEUP, ORIGINAL_DATE, makeupDate);
        makeup.id = 52;
        makeup.startMinute = 15 * 60;
        makeup.endMinute = 16 * 60;
        makeup.room = "Lab 3";
        ScheduleSnapshot snapshot = snapshot(schedule, Collections.singletonList(makeup),
                ORIGINAL_DATE, ORIGINAL_DATE.plusDays(14));
        Instant now = LocalDateTime.of(2026, 11, 1, 8, 0).atZone(ZONE).toInstant();

        List<ScheduleReminderPlanner.Reminder> reminders =
                ScheduleReminderPlanner.planNextPerSchedule(snapshot, now, ZONE, true);

        assertEquals(1, reminders.size());
        assertEquals(makeupDate.toEpochDay(), reminders.get(0).occurrenceDateEpochDay);
        assertEquals(52L, reminders.get(0).exceptionId);
        assertEquals("Lab 3", reminders.get(0).occurrence.room);
    }

    @Test
    public void disablesGlobalAndPerScheduleRemindersAndSkipsPastReminderTime() {
        ScheduleDetails schedule = schedule();
        ScheduleSnapshot snapshot = snapshot(schedule, Collections.emptyList(),
                ORIGINAL_DATE, ORIGINAL_DATE.plusDays(14));
        Instant afterFirstReminder = LocalDateTime.of(2026, 11, 1, 10, 0)
                .atZone(ZONE).toInstant();

        assertTrue(ScheduleReminderPlanner.planNextPerSchedule(
                snapshot, afterFirstReminder, ZONE, false).isEmpty());

        ScheduleDetails disabledSchedule = schedule();
        disabledSchedule.notificationEnabled = false;
        ScheduleSnapshot disabledSnapshot = snapshot(disabledSchedule, Collections.emptyList(),
                ORIGINAL_DATE, ORIGINAL_DATE.plusDays(14));
        assertTrue(ScheduleReminderPlanner.planNextPerSchedule(
                disabledSnapshot, afterFirstReminder, ZONE, true).isEmpty());
        ScheduleSnapshot futureSnapshot = snapshot(schedule, Collections.emptyList(),
                ORIGINAL_DATE, ORIGINAL_DATE.plusDays(14));
        List<ScheduleReminderPlanner.Reminder> next =
                ScheduleReminderPlanner.planNextPerSchedule(
                        futureSnapshot, afterFirstReminder, ZONE, true);
        assertFalse(next.isEmpty());
        assertEquals(ORIGINAL_DATE.plusDays(7).toEpochDay(), next.get(0).occurrenceDateEpochDay);
    }

    @Test
    public void dailySummaryContainsEveryResolvedMeetingAndEffectiveRoom() {
        ScheduleDetails movedSchedule = schedule();
        ScheduleExceptionEntity modification = exception(
                ScheduleExceptionEntity.TYPE_MODIFY, ORIGINAL_DATE, ORIGINAL_DATE.plusDays(1));
        modification.id = 73;
        modification.startMinute = 10 * 60 + 30;
        modification.endMinute = 13 * 60;
        modification.room = "B204";

        ScheduleDetails secondSchedule = schedule();
        secondSchedule.scheduleId = 13L;
        secondSchedule.dayOfWeek = ORIGINAL_DATE.plusDays(1).getDayOfWeek().getValue();
        secondSchedule.startMinute = 12 * 60;
        secondSchedule.endMinute = 14 * 60;
        secondSchedule.room = "C301";
        secondSchedule.subjectName = "Mạng máy tính";

        ScheduleSnapshot snapshot = ScheduleResolver.resolve(
                java.util.Arrays.asList(movedSchedule, secondSchedule),
                Collections.singletonList(modification),
                ORIGINAL_DATE.plusDays(1), ORIGINAL_DATE.plusDays(1));

        assertEquals("10:30–13:00 · Cơ sở dữ liệu · B204\n"
                        + "12:00–14:00 · Mạng máy tính · C301",
                DailySummaryFormatter.formatAgenda(snapshot.on(ORIGINAL_DATE.plusDays(1)), "—"));
    }

    private static ScheduleSnapshot snapshot(ScheduleDetails schedule,
                                             List<ScheduleExceptionEntity> exceptions,
                                             LocalDate from, LocalDate to) {
        return ScheduleResolver.resolve(Collections.singletonList(schedule), exceptions, from, to);
    }

    private static ScheduleDetails schedule() {
        ScheduleDetails schedule = new ScheduleDetails();
        schedule.scheduleId = 12L;
        schedule.dayOfWeek = ORIGINAL_DATE.getDayOfWeek().getValue();
        schedule.startMinute = 9 * 60;
        schedule.endMinute = 10 * 60;
        schedule.room = "A101";
        schedule.validFromEpochDay = ORIGINAL_DATE.toEpochDay();
        schedule.validToEpochDay = ORIGINAL_DATE.plusDays(90).toEpochDay();
        schedule.notificationEnabled = true;
        schedule.reminderMinutesBefore = 1_440;
        schedule.subjectName = "Cơ sở dữ liệu";
        schedule.classCode = "CTK44";
        return schedule;
    }

    private static ScheduleExceptionEntity exception(String type, LocalDate originalDate,
                                                     LocalDate replacementDate) {
        ScheduleExceptionEntity exception = new ScheduleExceptionEntity();
        exception.scheduleId = 12L;
        exception.originalDateEpochDay = originalDate.toEpochDay();
        exception.exceptionType = type;
        exception.replacementDateEpochDay = replacementDate == null
                ? null : replacementDate.toEpochDay();
        return exception;
    }
}
