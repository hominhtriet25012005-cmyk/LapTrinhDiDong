package com.example.demo.notifications;

import com.example.demo.data.model.ClassOccurrence;
import com.example.demo.data.model.ScheduleSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plans the next reminder for each schedule from the resolved, dated class occurrences. */
public final class ScheduleReminderPlanner {
    private static final Comparator<Reminder> REMINDER_ORDER = Comparator
            .comparingLong((Reminder reminder) -> reminder.reminderAtMillis)
            .thenComparingLong(reminder -> reminder.classStartsAtMillis)
            .thenComparingLong(reminder -> reminder.scheduleId);

    private ScheduleReminderPlanner() {
    }

    /**
     * Returns at most one pending reminder per recurring schedule. Canceled occurrences are
     * absent from the snapshot, while modified and makeup occurrences carry their effective
     * date, time and room into the returned reminder.
     */
    public static List<Reminder> planNextPerSchedule(ScheduleSnapshot snapshot, Instant now,
                                                     ZoneId zone, boolean remindersEnabled) {
        if (!remindersEnabled || snapshot == null || now == null || zone == null
                || snapshot.occurrences.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Reminder> nextBySchedule = new HashMap<>();
        for (ClassOccurrence occurrence : snapshot.occurrences) {
            if (occurrence == null || occurrence.schedule == null
                    || occurrence.schedule.scheduleId <= 0
                    || !occurrence.schedule.notificationEnabled
                    || occurrence.schedule.reminderMinutesBefore < 0
                    || occurrence.startMinute < 0 || occurrence.startMinute >= 1_440) {
                continue;
            }

            try {
                Instant classStartsAt = atZone(occurrence.date, occurrence.startMinute, zone);
                long reminderSeconds = occurrence.schedule.reminderMinutesBefore * 60L;
                Instant reminderAt = classStartsAt.minusSeconds(reminderSeconds);
                if (!reminderAt.isAfter(now)) {
                    continue;
                }

                Reminder candidate = new Reminder(occurrence, reminderAt, classStartsAt);
                Reminder current = nextBySchedule.get(occurrence.schedule.scheduleId);
                if (current == null || REMINDER_ORDER.compare(candidate, current) < 0) {
                    nextBySchedule.put(occurrence.schedule.scheduleId, candidate);
                }
            } catch (DateTimeException | ArithmeticException ignored) {
                // Ignore invalid/extreme date values instead of preventing other reminders.
            }
        }

        List<Reminder> reminders = new ArrayList<>(nextBySchedule.values());
        reminders.sort(REMINDER_ORDER);
        return Collections.unmodifiableList(reminders);
    }

    private static Instant atZone(LocalDate date, int minuteOfDay, ZoneId zone) {
        return LocalDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
                .atZone(zone).toInstant();
    }

    public static final class Reminder {
        public final ClassOccurrence occurrence;
        public final long scheduleId;
        public final long occurrenceDateEpochDay;
        public final long originalDateEpochDay;
        public final long exceptionId;
        public final long reminderAtMillis;
        public final long classStartsAtMillis;

        private Reminder(ClassOccurrence occurrence, Instant reminderAt, Instant classStartsAt) {
            this.occurrence = occurrence;
            this.scheduleId = occurrence.schedule.scheduleId;
            this.occurrenceDateEpochDay = occurrence.date.toEpochDay();
            this.originalDateEpochDay = occurrence.originalDate.toEpochDay();
            this.exceptionId = occurrence.exceptionId;
            this.reminderAtMillis = reminderAt.toEpochMilli();
            this.classStartsAtMillis = classStartsAt.toEpochMilli();
        }
    }
}
