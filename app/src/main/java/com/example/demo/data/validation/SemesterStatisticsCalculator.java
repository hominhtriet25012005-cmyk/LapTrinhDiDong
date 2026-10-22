package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/** Calculates weekly and whole-semester workload from recurring schedule rows. */
public final class SemesterStatisticsCalculator {

    private SemesterStatisticsCalculator() {
    }

    public static Statistics calculate(SemesterEntity semester, int subjectCount,
                                       List<ScheduleDetails> schedules) {
        if (semester == null) {
            throw new IllegalArgumentException("Semester must not be null.");
        }

        int[] classesByDay = new int[7];
        int classesPerWeek = 0;
        long weeklyMinutes = 0L;
        long totalSemesterMinutes = 0L;
        List<ScheduleDetails> safeSchedules = schedules == null
                ? Collections.emptyList() : schedules;

        for (ScheduleDetails schedule : safeSchedules) {
            if (!isValidSchedule(schedule) || schedule.semesterId != semester.id) {
                continue;
            }

            long startEpochDay = schedule.validFromEpochDay;
            long endEpochDay = schedule.validToEpochDay;
            if (semester.startDateEpochDay != null) {
                startEpochDay = Math.max(startEpochDay, semester.startDateEpochDay);
            }
            if (semester.endDateEpochDay != null) {
                endEpochDay = Math.min(endEpochDay, semester.endDateEpochDay);
            }
            if (endEpochDay < startEpochDay) {
                continue;
            }

            long occurrenceCount = countOccurrences(startEpochDay, endEpochDay, schedule.dayOfWeek);
            if (occurrenceCount == 0) {
                continue;
            }

            int durationMinutes = schedule.endMinute - schedule.startMinute;
            classesByDay[schedule.dayOfWeek - 1]++;
            classesPerWeek++;
            weeklyMinutes += durationMinutes;
            totalSemesterMinutes += (long) durationMinutes * occurrenceCount;
        }

        return new Statistics(Math.max(0, subjectCount), classesPerWeek, weeklyMinutes,
                classesByDay, totalSemesterMinutes);
    }

    private static long countOccurrences(long startEpochDay, long endEpochDay, int dayOfWeek) {
        int startDayOfWeek = LocalDate.ofEpochDay(startEpochDay).getDayOfWeek().getValue();
        int daysUntilOccurrence = Math.floorMod(dayOfWeek - startDayOfWeek, 7);
        long firstOccurrence = startEpochDay + daysUntilOccurrence;
        if (firstOccurrence > endEpochDay) {
            return 0L;
        }
        return ((endEpochDay - firstOccurrence) / 7L) + 1L;
    }

    private static boolean isValidSchedule(ScheduleDetails schedule) {
        return schedule != null
                && schedule.dayOfWeek >= 1 && schedule.dayOfWeek <= 7
                && schedule.startMinute >= 0
                && schedule.startMinute < schedule.endMinute
                && schedule.endMinute <= 24 * 60
                && schedule.validToEpochDay >= schedule.validFromEpochDay;
    }

    public static final class Statistics {
        public final int subjectCount;
        public final int classesPerWeek;
        public final long weeklyMinutes;
        public final int[] classesByDay;
        public final long totalSemesterMinutes;

        private Statistics(int subjectCount, int classesPerWeek, long weeklyMinutes,
                           int[] classesByDay, long totalSemesterMinutes) {
            this.subjectCount = subjectCount;
            this.classesPerWeek = classesPerWeek;
            this.weeklyMinutes = weeklyMinutes;
            this.classesByDay = classesByDay.clone();
            this.totalSemesterMinutes = totalSemesterMinutes;
        }
    }
}
