package com.example.demo.notifications;

import com.example.demo.data.local.entity.ScheduleEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Calculates the next reminder for a weekly class occurrence in the device's time zone. */
public final class ScheduleReminderCalculator {
    private static final int MAX_SEARCH_DAYS = 14;

    private ScheduleReminderCalculator() {
    }

    public static Occurrence nextOccurrence(ScheduleEntity schedule, Instant now, ZoneId zone) {
        if (schedule == null || now == null || zone == null
                || schedule.dayOfWeek < 1 || schedule.dayOfWeek > 7
                || schedule.startMinute < 0 || schedule.startMinute >= 24 * 60
                || schedule.reminderMinutesBefore < 0
                || schedule.validFromEpochDay > schedule.validToEpochDay) {
            return null;
        }

        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate firstDate = LocalDate.ofEpochDay(schedule.validFromEpochDay);
        LocalDate lastDate = LocalDate.ofEpochDay(schedule.validToEpochDay);
        if (today.isAfter(lastDate)) {
            return null;
        }
        if (firstDate.isBefore(today)) {
            firstDate = today;
        }

        LocalDate searchEnd = firstDate.plusDays(MAX_SEARCH_DAYS);
        if (searchEnd.isAfter(lastDate)) {
            searchEnd = lastDate;
        }
        for (LocalDate classDate = firstDate; !classDate.isAfter(searchEnd);
             classDate = classDate.plusDays(1)) {
            if (classDate.getDayOfWeek().getValue() != schedule.dayOfWeek) {
                continue;
            }
            LocalTime startTime = LocalTime.of(schedule.startMinute / 60,
                    schedule.startMinute % 60);
            Instant classStartsAt = LocalDateTime.of(classDate, startTime)
                    .atZone(zone).toInstant();
            Instant reminderAt = classStartsAt.minusSeconds(schedule.reminderMinutesBefore * 60L);
            if (reminderAt.isAfter(now)) {
                return new Occurrence(reminderAt.toEpochMilli(), classStartsAt.toEpochMilli());
            }
        }
        return null;
    }

    public static final class Occurrence {
        public final long reminderAtMillis;
        public final long classStartsAtMillis;

        private Occurrence(long reminderAtMillis, long classStartsAtMillis) {
            this.reminderAtMillis = reminderAtMillis;
            this.classStartsAtMillis = classStartsAtMillis;
        }
    }
}
