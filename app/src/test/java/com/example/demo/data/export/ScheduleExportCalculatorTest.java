package com.example.demo.data.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScheduleExportCalculatorTest {

    @Test
    public void currentWeekUsesMondayThroughSunday() {
        ScheduleExportCalculator.DateRange range = ScheduleExportCalculator.resolveRange(
                ScheduleExportCalculator.Scope.CURRENT_WEEK,
                LocalDate.of(2026, 3, 18),
                semester(1, null, null),
                Collections.emptyList()
        );

        assertEquals(LocalDate.of(2026, 3, 16).toEpochDay(), range.startEpochDay);
        assertEquals(LocalDate.of(2026, 3, 22).toEpochDay(), range.endEpochDay);
    }

    @Test
    public void currentMonthUsesWholeCalendarMonth() {
        ScheduleExportCalculator.DateRange range = ScheduleExportCalculator.resolveRange(
                ScheduleExportCalculator.Scope.CURRENT_MONTH,
                LocalDate.of(2026, 3, 18),
                semester(1, null, null),
                Collections.emptyList()
        );

        assertEquals(LocalDate.of(2026, 3, 1).toEpochDay(), range.startEpochDay);
        assertEquals(LocalDate.of(2026, 3, 31).toEpochDay(), range.endEpochDay);
    }

    @Test
    public void currentRangeIsClippedToSelectedSemester() {
        ScheduleExportCalculator.DateRange range = ScheduleExportCalculator.resolveRange(
                ScheduleExportCalculator.Scope.CURRENT_WEEK,
                LocalDate.of(2026, 3, 18),
                semester(1, LocalDate.of(2026, 3, 18), LocalDate.of(2026, 3, 20)),
                Collections.emptyList()
        );

        assertEquals(LocalDate.of(2026, 3, 18).toEpochDay(), range.startEpochDay);
        assertEquals(LocalDate.of(2026, 3, 20).toEpochDay(), range.endEpochDay);
    }

    @Test
    public void semesterRangeFallsBackToScheduleValidityWhenDatesAreMissing() {
        ScheduleDetails row = schedule(1, 1, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 5, 25));

        ScheduleExportCalculator.DateRange range = ScheduleExportCalculator.resolveRange(
                ScheduleExportCalculator.Scope.SEMESTER,
                LocalDate.of(2026, 3, 18),
                semester(1, null, null),
                Collections.singletonList(row)
        );

        assertEquals(row.validFromEpochDay, range.startEpochDay);
        assertEquals(row.validToEpochDay, range.endEpochDay);
    }

    @Test
    public void occurrencesRespectWeekdaySemesterAndScheduleValidity() {
        ScheduleDetails activeMonday = schedule(1, 1, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 30));
        ScheduleDetails expiredMonday = schedule(1, 1, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 15));
        ScheduleDetails otherSemester = schedule(2, 1, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 30));
        ScheduleExportCalculator.DateRange range = new ScheduleExportCalculator.DateRange(
                LocalDate.of(2026, 3, 16).toEpochDay(), LocalDate.of(2026, 3, 29).toEpochDay());

        List<ScheduleExportCalculator.Occurrence> occurrences = ScheduleExportCalculator.occurrences(
                1, Arrays.asList(activeMonday, expiredMonday, otherSemester), range);

        assertEquals(2, occurrences.size());
        assertEquals(LocalDate.of(2026, 3, 16), occurrences.get(0).date);
        assertEquals(LocalDate.of(2026, 3, 23), occurrences.get(1).date);
    }

    @Test
    public void semesterWithoutDatesOrSchedulesHasEmptyRange() {
        ScheduleExportCalculator.DateRange range = ScheduleExportCalculator.resolveRange(
                ScheduleExportCalculator.Scope.SEMESTER,
                LocalDate.now(),
                semester(1, null, null),
                Collections.emptyList()
        );

        assertTrue(range.isEmpty());
    }

    private static SemesterEntity semester(long id, LocalDate start, LocalDate end) {
        SemesterEntity semester = new SemesterEntity();
        semester.id = id;
        semester.name = "Học kỳ kiểm thử";
        semester.academicYear = "2025–2026";
        semester.startDateEpochDay = start == null ? null : start.toEpochDay();
        semester.endDateEpochDay = end == null ? null : end.toEpochDay();
        return semester;
    }

    private static ScheduleDetails schedule(long semesterId, int weekday, LocalDate start, LocalDate end) {
        ScheduleDetails row = new ScheduleDetails();
        row.semesterId = semesterId;
        row.scheduleId = semesterId * 100 + weekday;
        row.dayOfWeek = weekday;
        row.startMinute = 8 * 60;
        row.endMinute = 9 * 60;
        row.validFromEpochDay = start.toEpochDay();
        row.validToEpochDay = end.toEpochDay();
        return row;
    }
}
