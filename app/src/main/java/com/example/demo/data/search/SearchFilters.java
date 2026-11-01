package com.example.demo.data.search;

import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ClassOccurrence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Applies accent-insensitive search and the app's semester, weekday and room filters. */
public final class SearchFilters {
    private SearchFilters() {
    }

    public static List<SubjectEntity> filterSubjects(List<SubjectEntity> subjects, String query) {
        if (subjects == null || subjects.isEmpty()) {
            return Collections.emptyList();
        }
        List<SubjectEntity> result = new ArrayList<>();
        for (SubjectEntity subject : subjects) {
            if (subject != null && SearchTextNormalizer.matchesAny(
                    query, subject.code, subject.name)) {
                result.add(subject);
            }
        }
        return result;
    }

    public static List<ScheduleDetails> filterScheduleDetails(List<ScheduleDetails> schedules,
                                                               String query,
                                                               long semesterId,
                                                               int dayOfWeek,
                                                               String roomQuery) {
        return filterScheduleDetails(schedules, query, semesterId, dayOfWeek, roomQuery,
                null, null);
    }

    /** Filters recurring schedules to those with at least one class date in the inclusive range. */
    public static List<ScheduleDetails> filterScheduleDetails(List<ScheduleDetails> schedules,
                                                               String query,
                                                               long semesterId,
                                                               int dayOfWeek,
                                                               String roomQuery,
                                                               Long fromEpochDay,
                                                               Long toEpochDay) {
        if (fromEpochDay != null && toEpochDay != null && fromEpochDay > toEpochDay) {
            throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc.");
        }
        if (schedules == null || schedules.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScheduleDetails> result = new ArrayList<>();
        for (ScheduleDetails schedule : schedules) {
            if (schedule == null
                    || (semesterId > 0 && schedule.semesterId != semesterId)
                    || (dayOfWeek > 0 && schedule.dayOfWeek != dayOfWeek)
                    || !hasOccurrenceInRange(schedule, fromEpochDay, toEpochDay)
                    || !SearchTextNormalizer.contains(schedule.room, roomQuery)
                    || !SearchTextNormalizer.matchesAny(query,
                    schedule.subjectCode, schedule.subjectName, schedule.classCode, schedule.room)) {
                continue;
            }
            result.add(schedule);
        }
        return result;
    }

    /** Filters effective dated meetings, including their actual moved date and override room. */
    public static List<ClassOccurrence> filterOccurrences(List<ClassOccurrence> occurrences,
                                                           String query,
                                                           long semesterId,
                                                           int dayOfWeek,
                                                           String roomQuery,
                                                           Long fromEpochDay,
                                                           Long toEpochDay) {
        if (fromEpochDay != null && toEpochDay != null && fromEpochDay > toEpochDay) {
            throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc.");
        }
        if (occurrences == null || occurrences.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClassOccurrence> result = new ArrayList<>();
        for (ClassOccurrence occurrence : occurrences) {
            if (occurrence == null) continue;
            long occurrenceEpochDay = occurrence.date.toEpochDay();
            if ((semesterId > 0 && occurrence.schedule.semesterId != semesterId)
                    || (dayOfWeek > 0
                    && occurrence.date.getDayOfWeek().getValue() != dayOfWeek)
                    || (fromEpochDay != null && occurrenceEpochDay < fromEpochDay)
                    || (toEpochDay != null && occurrenceEpochDay > toEpochDay)
                    || !SearchTextNormalizer.contains(occurrence.room, roomQuery)
                    || !SearchTextNormalizer.matchesAny(query,
                    occurrence.schedule.subjectCode, occurrence.schedule.subjectName,
                    occurrence.schedule.classCode, occurrence.room,
                    occurrence.schedule.lecturer)) {
                continue;
            }
            result.add(occurrence);
        }
        return result;
    }

    private static boolean hasOccurrenceInRange(ScheduleDetails schedule,
                                                  Long fromEpochDay,
                                                  Long toEpochDay) {
        if (fromEpochDay == null && toEpochDay == null) {
            return true;
        }
        if (schedule.dayOfWeek < 1 || schedule.dayOfWeek > 7) {
            return false;
        }

        long start = schedule.validFromEpochDay;
        long end = schedule.validToEpochDay;
        if (fromEpochDay != null) {
            start = Math.max(start, fromEpochDay);
        }
        if (toEpochDay != null) {
            end = Math.min(end, toEpochDay);
        }
        if (start > end) {
            return false;
        }

        // Epoch day 0 is Thursday; app weekday values follow ISO (Monday=1 ... Sunday=7).
        int weekdayAtStart = (int) Math.floorMod(Math.floorMod(start, 7) + 3, 7) + 1;
        int daysUntilClass = Math.floorMod(schedule.dayOfWeek - weekdayAtStart, 7);
        return start <= Long.MAX_VALUE - daysUntilClass
                && start + daysUntilClass <= end;
    }
}
