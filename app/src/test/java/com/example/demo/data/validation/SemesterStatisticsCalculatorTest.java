package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class SemesterStatisticsCalculatorTest {

    @Test
    public void calculatesWeeklyKpisAndDayCountsForSelectedSemester() {
        SemesterEntity semester = semester(7L, "Fall", LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 11));
        ScheduleDetails monday = schedule(7L, 1, 540, 600,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11));
        ScheduleDetails thursday = schedule(7L, 4, 810, 900,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11));
        ScheduleDetails anotherSemester = schedule(8L, 1, 600, 660,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11));

        SemesterStatisticsCalculator.Statistics result = SemesterStatisticsCalculator.calculate(
                semester, 3, Arrays.asList(monday, thursday, anotherSemester));

        assertEquals(3, result.subjectCount);
        assertEquals(2, result.classesPerWeek);
        assertEquals(150, result.weeklyMinutes);
        assertEquals(1, result.classesByDay[0]);
        assertEquals(1, result.classesByDay[3]);
        assertEquals(0, result.classesByDay[6]);
        assertEquals(150, result.totalSemesterMinutes);
    }

    @Test
    public void countsOnlyActualWeekdayOccurrencesWithinSemesterAndScheduleDates() {
        SemesterEntity semester = semester(2L, "Spring", LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 15));
        ScheduleDetails monday = schedule(2L, 1, 540, 630,
                LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15));
        ScheduleDetails wednesday = schedule(2L, 3, 600, 660,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 15));

        SemesterStatisticsCalculator.Statistics result = SemesterStatisticsCalculator.calculate(
                semester, 2, Arrays.asList(monday, wednesday));

        assertEquals(2, result.classesPerWeek);
        assertEquals(105, result.weeklyMinutes);
        assertEquals(210, result.totalSemesterMinutes);
    }

    @Test
    public void ignoresSchedulesWithNoOccurrenceOrInvalidTime() {
        SemesterEntity semester = semester(4L, "Summer", LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30));
        ScheduleDetails outsideRange = schedule(4L, 1, 540, 600,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        ScheduleDetails noMondayOccurrence = schedule(4L, 1, 700, 650,
                LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 7));
        ScheduleDetails invalidWeekday = schedule(4L, 8, 540, 600,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        SemesterStatisticsCalculator.Statistics result = SemesterStatisticsCalculator.calculate(
                semester, 1, Arrays.asList(outsideRange, noMondayOccurrence, invalidWeekday));

        assertEquals(0, result.classesPerWeek);
        assertEquals(0, result.weeklyMinutes);
        assertEquals(0, result.totalSemesterMinutes);
    }

    @Test
    public void supportsMissingSemesterDateBoundsUsingScheduleValidity() {
        SemesterEntity semester = new SemesterEntity("Fall", "2026-2027", null, null, true, "");
        semester.id = 9L;
        ScheduleDetails schedule = schedule(9L, 5, 480, 570,
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 18));

        SemesterStatisticsCalculator.Statistics result = SemesterStatisticsCalculator.calculate(
                semester, 1, Collections.singletonList(schedule));

        assertEquals(1, result.classesPerWeek);
        assertEquals(90, result.weeklyMinutes);
        assertEquals(270, result.totalSemesterMinutes);
    }

    @Test
    public void statisticsUseCancelledAndMakeupOccurrencesFromSharedSnapshot() {
        LocalDate start = LocalDate.of(2026, 3, 2);
        LocalDate end = LocalDate.of(2026, 3, 15);
        SemesterEntity semester = semester(12L, "Spring", start, end);
        ScheduleDetails monday = schedule(12L, 1, 540, 600, start, end);
        ScheduleExceptionEntity cancellation = exception(
                monday.scheduleId, start.plusDays(7), ScheduleExceptionEntity.TYPE_CANCEL,
                null, null, null);
        ScheduleExceptionEntity makeup = exception(
                monday.scheduleId, start, ScheduleExceptionEntity.TYPE_MAKEUP,
                start.plusDays(2), 660, 720);
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Collections.singletonList(monday),
                Arrays.asList(cancellation, makeup), start, end);

        SemesterStatisticsCalculator.Statistics result = SemesterStatisticsCalculator.calculate(
                semester, 1, snapshot);

        assertEquals(1, result.totalClassOccurrences);
        assertEquals(60, result.totalSemesterMinutes);
        assertEquals(30, result.weeklyMinutes);
        assertEquals(1, result.classesPerWeek);
        assertEquals(0, result.classesByDay[0]);
        assertEquals(1, result.classesByDay[2]);
    }

    @Test
    public void fallbackSemesterBoundsExpandToIncludeReplacementDates() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        SemesterEntity semester = new SemesterEntity("HK1", "2025-2026", null, null, true, "");
        semester.id = 13L;
        ScheduleDetails schedule = schedule(13L, 1, 540, 600, monday, monday);
        ScheduleExceptionEntity makeup = exception(schedule.scheduleId, monday,
                ScheduleExceptionEntity.TYPE_MAKEUP, monday.plusDays(2), 660, 720);

        SemesterStatisticsCalculator.Statistics result = SemesterStatisticsCalculator.calculate(
                semester, 1, Collections.singletonList(schedule), Collections.singletonList(makeup));

        assertEquals(1, result.totalClassOccurrences);
        assertEquals(60, result.totalSemesterMinutes);
        assertEquals(1, result.classesByDay[2]);
    }

    private static SemesterEntity semester(long id, String name, LocalDate start, LocalDate end) {
        SemesterEntity semester = new SemesterEntity(name, "2026-2027",
                start.toEpochDay(), end.toEpochDay(), true, "");
        semester.id = id;
        return semester;
    }

    private static ScheduleDetails schedule(long semesterId, int dayOfWeek, int startMinute,
                                            int endMinute, LocalDate validFrom, LocalDate validTo) {
        ScheduleDetails schedule = new ScheduleDetails();
        schedule.scheduleId = semesterId * 100L + dayOfWeek;
        schedule.semesterId = semesterId;
        schedule.dayOfWeek = dayOfWeek;
        schedule.startMinute = startMinute;
        schedule.endMinute = endMinute;
        schedule.validFromEpochDay = validFrom.toEpochDay();
        schedule.validToEpochDay = validTo.toEpochDay();
        return schedule;
    }

    private static ScheduleExceptionEntity exception(long scheduleId, LocalDate original,
                                                      String type, LocalDate replacement,
                                                      Integer start, Integer end) {
        ScheduleExceptionEntity exception = new ScheduleExceptionEntity();
        exception.scheduleId = scheduleId;
        exception.originalDateEpochDay = original.toEpochDay();
        exception.exceptionType = type;
        exception.replacementDateEpochDay = replacement == null ? null : replacement.toEpochDay();
        exception.startMinute = start;
        exception.endMinute = end;
        return exception;
    }
}
