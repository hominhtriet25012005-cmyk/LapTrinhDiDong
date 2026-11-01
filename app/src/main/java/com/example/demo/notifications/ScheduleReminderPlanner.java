package com.example.demo.notifications;

import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleResolver;
import com.example.demo.data.schedule.ScheduleSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides which concrete class sessions get a reminder alarm. Works on resolved sessions, so
 * cancelled sessions are skipped and changed or makeup sessions use their own date and time.
 */
public final class ScheduleReminderPlanner {
    private ScheduleReminderPlanner() {
    }

    public static List<PlannedReminder> plan(ScheduleSnapshot snapshot, Instant now, ZoneId zone,
                                             int lookaheadDays, boolean remindersEnabled,
                                             int maxCount) {
        if (snapshot == null || now == null || zone == null || !remindersEnabled
                || lookaheadDays < 0 || maxCount <= 0) {
            return Collections.emptyList();
        }
        long today = now.atZone(zone).toLocalDate().toEpochDay();
        List<ClassOccurrence> occurrences = ScheduleResolver.resolve(
                snapshot, today, today + lookaheadDays, false);
        List<PlannedReminder> planned = new ArrayList<>();
        for (ClassOccurrence occurrence : occurrences) {
            // Makeup sessions have no weekly schedule flag; they follow the global setting.
            boolean enabled = occurrence.scheduleId == 0 || occurrence.notificationEnabled;
            if (!enabled || occurrence.reminderMinutesBefore < 0) {
                continue;
            }
            long startsAt = startsAtMillis(occurrence, zone);
            long reminderAt = startsAt - occurrence.reminderMinutesBefore * 60_000L;
            if (reminderAt <= now.toEpochMilli()) {
                continue;
            }
            planned.add(new PlannedReminder(occurrence, startsAt, reminderAt));
        }
        planned.sort((a, b) -> Long.compare(a.reminderAtMillis, b.reminderAtMillis));
        return planned.size() > maxCount ? new ArrayList<>(planned.subList(0, maxCount)) : planned;
    }

    public static long startsAtMillis(ClassOccurrence occurrence, ZoneId zone) {
        return LocalDateTime.of(LocalDate.ofEpochDay(occurrence.epochDay),
                        LocalTime.of(occurrence.startMinute / 60, occurrence.startMinute % 60))
                .atZone(zone).toInstant().toEpochMilli();
    }

    public static String key(long scheduleId, long exceptionId, long epochDay) {
        return scheduleId + "-" + exceptionId + "-" + epochDay;
    }

    public static final class PlannedReminder {
        public final String key;
        public final ClassOccurrence occurrence;
        public final long classStartsAtMillis;
        public final long reminderAtMillis;

        private PlannedReminder(ClassOccurrence occurrence, long classStartsAtMillis,
                                long reminderAtMillis) {
            this.key = ScheduleReminderPlanner.key(
                    occurrence.scheduleId, occurrence.exceptionId, occurrence.epochDay);
            this.occurrence = occurrence;
            this.classStartsAtMillis = classStartsAtMillis;
            this.reminderAtMillis = reminderAtMillis;
        }
    }
}