package com.example.demo.notifications;

import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.notifications.ScheduleReminderCalculator;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ScheduleReminderCalculatorTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    public void returnsUpcomingReminderWithinValidityRange() {
        ScheduleEntity schedule = schedule(LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 19), 1, 8 * 60, 30);
        Instant now = LocalDateTime.of(2026, 10, 19, 7, 0).atZone(ZONE).toInstant();

        ScheduleReminderCalculator.Occurrence occurrence =
                ScheduleReminderCalculator.nextOccurrence(schedule, now, ZONE);

        assertEquals(LocalDateTime.of(2026, 10, 19, 7, 30).atZone(ZONE).toInstant().toEpochMilli(),
                occurrence.reminderAtMillis);
        assertEquals(LocalDateTime.of(2026, 10, 19, 8, 0).atZone(ZONE).toInstant().toEpochMilli(),
                occurrence.classStartsAtMillis);
    }

    @Test
    public void skipsReminderTimeThatHasPassedAndFindsFollowingWeek() {
        ScheduleEntity schedule = schedule(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 30), 1, 8 * 60, 1_440);
        Instant now = LocalDateTime.of(2026, 10, 18, 10, 0).atZone(ZONE).toInstant();

        ScheduleReminderCalculator.Occurrence occurrence =
                ScheduleReminderCalculator.nextOccurrence(schedule, now, ZONE);

        assertEquals(LocalDateTime.of(2026, 10, 25, 8, 0).atZone(ZONE).toInstant().toEpochMilli(),
                occurrence.reminderAtMillis);
        assertEquals(LocalDateTime.of(2026, 10, 26, 8, 0).atZone(ZONE).toInstant().toEpochMilli(),
                occurrence.classStartsAtMillis);
    }

    @Test
    public void skipsOccurrencesBeforeStartDateAndReturnsNullAfterLastOccurrence() {
        ScheduleEntity startsLater = schedule(LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 8), 4, 9 * 60, 0);
        Instant now = LocalDateTime.of(2026, 10, 6, 9, 0).atZone(ZONE).toInstant();

        ScheduleReminderCalculator.Occurrence occurrence =
                ScheduleReminderCalculator.nextOccurrence(startsLater, now, ZONE);
        assertEquals(LocalDateTime.of(2026, 10, 8, 9, 0).atZone(ZONE).toInstant().toEpochMilli(),
                occurrence.classStartsAtMillis);

        ScheduleEntity ended = schedule(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 8), 4, 9 * 60, 0);
        Instant afterLastClass = LocalDateTime.of(2026, 10, 8, 9, 1).atZone(ZONE).toInstant();
        assertNull(ScheduleReminderCalculator.nextOccurrence(ended, afterLastClass, ZONE));
    }

    private static ScheduleEntity schedule(LocalDate from, LocalDate to, int weekday,
                                           int startMinute, int reminderMinutes) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.id = 1;
        schedule.dayOfWeek = weekday;
        schedule.startMinute = startMinute;
        schedule.validFromEpochDay = from.toEpochDay();
        schedule.validToEpochDay = to.toEpochDay();
        schedule.reminderMinutesBefore = reminderMinutes;
        schedule.notificationEnabled = true;
        return schedule;
    }
}
