package com.example.demo.notifications;

import com.example.demo.data.model.ClassOccurrence;

import java.util.List;
import java.util.Locale;

/** Formats every effective class meeting for the daily notification body. */
public final class DailySummaryFormatter {
    private DailySummaryFormatter() {
    }

    public static String formatAgenda(List<ClassOccurrence> occurrences, String unknownRoom) {
        if (occurrences == null || occurrences.isEmpty()) {
            return "";
        }
        StringBuilder agenda = new StringBuilder();
        for (ClassOccurrence occurrence : occurrences) {
            if (occurrence == null || occurrence.schedule == null) {
                continue;
            }
            if (agenda.length() > 0) {
                agenda.append('\n');
            }
            agenda.append(formatTime(occurrence.startMinute))
                    .append('–').append(formatTime(occurrence.endMinute))
                    .append(" · ").append(safe(occurrence.schedule.subjectName))
                    .append(" · ").append(roomOrFallback(occurrence.room, unknownRoom));
        }
        return agenda.toString();
    }

    private static String formatTime(int minuteOfDay) {
        if (minuteOfDay == 1_440) {
            return "24:00";
        }
        return String.format(Locale.ROOT, "%02d:%02d",
                minuteOfDay / 60, minuteOfDay % 60);
    }

    private static String roomOrFallback(String room, String unknownRoom) {
        return room == null || room.trim().isEmpty() ? safe(unknownRoom) : room.trim();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }
}
