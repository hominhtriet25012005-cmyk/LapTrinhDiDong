package com.example.demo.data.schedule;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filters resolved class sessions by text, semester, weekday and room (accent-insensitive). */
public final class SessionSearch {
    public static final int MAX_RANGE_DAYS = 366;

    private SessionSearch() {
    }

    /**
     * @param semesterId 0 = all semesters
     * @param dayOfWeek  ISO 1-7 of the session's real date, 0 = any
     */
    public static List<ClassOccurrence> filter(List<ClassOccurrence> sessions, String query,
                                               long semesterId, int dayOfWeek, String roomQuery) {
        String text = normalize(query);
        String room = normalize(roomQuery);
        List<ClassOccurrence> result = new ArrayList<>();
        for (ClassOccurrence s : sessions) {
            if (semesterId != 0L && s.semesterId != semesterId) continue;
            if (dayOfWeek != 0
                    && LocalDate.ofEpochDay(s.epochDay).getDayOfWeek().getValue() != dayOfWeek) continue;
            if (!room.isEmpty() && !normalize(s.room).contains(room)) continue;
            if (!text.isEmpty() && !matchesText(s, text)) continue;
            result.add(s);
        }
        return result;
    }

    private static boolean matchesText(ClassOccurrence s, String text) {
        return normalize(s.subjectName).contains(text)
                || normalize(s.subjectCode).contains(text)
                || normalize(s.classCode).contains(text)
                || normalize(s.lecturer).contains(text)
                || normalize(s.room).contains(text);
    }

    static String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return decomposed.toLowerCase(Locale.ROOT);
    }
}