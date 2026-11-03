package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ClassOccurrence;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/** Calculates semester workload from effective dated occurrences. */
public final class SemesterStatisticsCalculator {
    private SemesterStatisticsCalculator() {
    }

    /** Backward-compatible entry point for callers that have not loaded exception rows yet. */
    public static Statistics calculate(SemesterEntity semester, int subjectCount,
                                       List<ScheduleDetails> schedules) {
        return calculate(semester, subjectCount, schedules,
                Collections.<ScheduleExceptionEntity>emptyList());
    }

    public static Statistics calculate(SemesterEntity semester, int subjectCount,
                                       List<ScheduleDetails> schedules,
                                       List<ScheduleExceptionEntity> exceptions) {
        if (semester == null) {
            throw new IllegalArgumentException("Semester must not be null.");
        }
        Long startEpochDay = semester.startDateEpochDay;
        Long endEpochDay = semester.endDateEpochDay;
        if (schedules != null) {
            for (ScheduleDetails schedule : schedules) {
                if (!isValidSchedule(schedule) || schedule.semesterId != semester.id) {
                    continue;
                }
                if (semester.startDateEpochDay == null
                        && (startEpochDay == null || schedule.validFromEpochDay < startEpochDay)) {
                    startEpochDay = schedule.validFromEpochDay;
                }
                if (semester.endDateEpochDay == null
                        && (endEpochDay == null || schedule.validToEpochDay > endEpochDay)) {
                    endEpochDay = schedule.validToEpochDay;
                }
            }
        }
        if (exceptions != null) {
            for (ScheduleExceptionEntity exception : exceptions) {
                if (exception == null || exception.replacementDateEpochDay == null
                        || semester.startDateEpochDay != null
                        && semester.endDateEpochDay != null) {
                    continue;
                }
                ScheduleDetails source = findSchedule(schedules, exception.scheduleId);
                if (source == null || source.semesterId != semester.id) {
                    continue;
                }
                if (semester.startDateEpochDay == null
                        && (startEpochDay == null
                        || exception.replacementDateEpochDay < startEpochDay)) {
                    startEpochDay = exception.replacementDateEpochDay;
                }
                if (semester.endDateEpochDay == null
                        && (endEpochDay == null
                        || exception.replacementDateEpochDay > endEpochDay)) {
                    endEpochDay = exception.replacementDateEpochDay;
                }
            }
        }
        if (startEpochDay == null || endEpochDay == null) {
            return emptyStatistics(subjectCount);
        }
        try {
            ScheduleSnapshot snapshot = ScheduleResolver.resolve(schedules,
                    exceptions,
                    LocalDate.ofEpochDay(startEpochDay), LocalDate.ofEpochDay(endEpochDay));
            return calculate(semester, subjectCount, snapshot);
        } catch (DateTimeException exception) {
            return emptyStatistics(subjectCount);
        }
    }

    /** Calculates the selected semester's statistics over a user-selected date interval. */
    public static Statistics calculate(SemesterEntity semester, int subjectCount,
                                       List<ScheduleDetails> schedules,
                                       List<ScheduleExceptionEntity> exceptions,
                                       LocalDate fromInclusive, LocalDate toInclusive) {
        if (semester == null) {
            throw new IllegalArgumentException("Semester must not be null.");
        }
        if (fromInclusive == null || toInclusive == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và kết thúc là bắt buộc.");
        }
        if (fromInclusive.isAfter(toInclusive)) {
            return emptyStatistics(subjectCount);
        }
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(
                schedules, exceptions, fromInclusive, toInclusive);
        return calculate(semester, subjectCount, snapshot);
    }

    /** Calculates exact total hours and per-week averages from a shared snapshot. */
    public static Statistics calculate(SemesterEntity semester, int subjectCount,
                                       ScheduleSnapshot snapshot) {
        if (semester == null) {
            throw new IllegalArgumentException("Semester must not be null.");
        }
        int[] classesByDay = new int[7];
        if (snapshot == null || snapshot.occurrences.isEmpty()) {
            return new Statistics(Math.max(0, subjectCount), 0, 0L,
                    classesByDay, 0L, 0L);
        }

        LocalDate rangeStart = snapshot.startDate;
        LocalDate rangeEnd = snapshot.endDate;
        try {
            if (semester.startDateEpochDay != null) {
                LocalDate semesterStart = LocalDate.ofEpochDay(semester.startDateEpochDay);
                if (semesterStart.isAfter(rangeStart)) rangeStart = semesterStart;
            }
            if (semester.endDateEpochDay != null) {
                LocalDate semesterEnd = LocalDate.ofEpochDay(semester.endDateEpochDay);
                if (semesterEnd.isBefore(rangeEnd)) rangeEnd = semesterEnd;
            }
        } catch (DateTimeException exception) {
            return emptyStatistics(subjectCount);
        }
        if (rangeStart.isAfter(rangeEnd)) {
            return new Statistics(Math.max(0, subjectCount), 0, 0L,
                    classesByDay, 0L, 0L);
        }

        List<ClassOccurrence> occurrences = snapshot.between(rangeStart, rangeEnd);
        long totalMinutes = 0L;
        long totalOccurrences = 0L;
        long[] occurrenceCountsByDay = new long[7];
        for (ClassOccurrence occurrence : occurrences) {
            if (occurrence.schedule.semesterId != semester.id) {
                continue;
            }
            totalMinutes += occurrence.endMinute - occurrence.startMinute;
            totalOccurrences++;
            occurrenceCountsByDay[occurrence.date.getDayOfWeek().getValue() - 1]++;
        }

        long totalDays = rangeEnd.toEpochDay() - rangeStart.toEpochDay() + 1L;
        long weekCount = Math.max(1L, (totalDays + 6L) / 7L);
        long classesPerWeek = roundedAverage(totalOccurrences, weekCount);
        long weeklyMinutes = roundedAverage(totalMinutes, weekCount);
        for (int day = 0; day < classesByDay.length; day++) {
            classesByDay[day] = (int) Math.min(Integer.MAX_VALUE,
                    roundedAverage(occurrenceCountsByDay[day], weekCount));
        }
        return new Statistics(Math.max(0, subjectCount),
                (int) Math.min(Integer.MAX_VALUE, classesPerWeek), weeklyMinutes,
                classesByDay, totalMinutes, totalOccurrences);
    }

    private static long roundedAverage(long total, long divisor) {
        return total / divisor + (((total % divisor) * 2L >= divisor) ? 1L : 0L);
    }

    private static Statistics emptyStatistics(int subjectCount) {
        return new Statistics(Math.max(0, subjectCount), 0, 0L,
                new int[7], 0L, 0L);
    }

    private static ScheduleDetails findSchedule(List<ScheduleDetails> schedules, long scheduleId) {
        if (schedules == null) return null;
        for (ScheduleDetails schedule : schedules) {
            if (schedule != null && schedule.scheduleId == scheduleId) return schedule;
        }
        return null;
    }

    private static boolean isValidSchedule(ScheduleDetails schedule) {
        return schedule != null && schedule.scheduleId > 0
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
        public final long totalClassOccurrences;

        private Statistics(int subjectCount, int classesPerWeek, long weeklyMinutes,
                           int[] classesByDay, long totalSemesterMinutes,
                           long totalClassOccurrences) {
            this.subjectCount = subjectCount;
            this.classesPerWeek = classesPerWeek;
            this.weeklyMinutes = weeklyMinutes;
            this.classesByDay = classesByDay.clone();
            this.totalSemesterMinutes = totalSemesterMinutes;
            this.totalClassOccurrences = totalClassOccurrences;
        }
    }
}
