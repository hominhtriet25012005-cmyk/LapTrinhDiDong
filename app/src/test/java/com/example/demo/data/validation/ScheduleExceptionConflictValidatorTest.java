package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduleExceptionConflictValidatorTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 16);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    @Test
    public void detectsOverlapWithRecurringClassOnReplacementDate() {
        ScheduleExceptionEntity candidate = makeup(1L, MONDAY, TUESDAY, 600, 660);
        List<ScheduleEntity> schedules = Arrays.asList(
                schedule(1L, 1, 540, 600, MONDAY.minusDays(7), MONDAY.plusDays(21)),
                schedule(2L, 2, 630, 690, MONDAY.minusDays(7), MONDAY.plusDays(21))
        );

        assertTrue(ScheduleExceptionConflictValidator.hasConflict(
                candidate, schedules, Collections.emptyList()));
    }

    @Test
    public void allowsAdjacentClassesAndIgnoresCancelledRecurringOccurrence() {
        ScheduleExceptionEntity candidate = makeup(1L, MONDAY, TUESDAY, 600, 660);
        ScheduleEntity tuesday = schedule(2L, 2, 660, 720,
                MONDAY.minusDays(7), MONDAY.plusDays(21));
        ScheduleExceptionEntity cancellation = new ScheduleExceptionEntity();
        cancellation.scheduleId = 2L;
        cancellation.originalDateEpochDay = TUESDAY.toEpochDay();
        cancellation.exceptionType = ScheduleExceptionEntity.TYPE_CANCEL;

        assertFalse(ScheduleExceptionConflictValidator.hasConflict(candidate,
                Arrays.asList(schedule(1L, 1, 540, 600, MONDAY, MONDAY), tuesday),
                Collections.singletonList(cancellation)));
    }

    @Test
    public void includesAnotherMovedClassOnTheReplacementDate() {
        ScheduleExceptionEntity candidate = makeup(1L, MONDAY, TUESDAY, 600, 660);
        ScheduleExceptionEntity moved = new ScheduleExceptionEntity();
        moved.scheduleId = 2L;
        moved.originalDateEpochDay = MONDAY.plusDays(7).toEpochDay();
        moved.exceptionType = ScheduleExceptionEntity.TYPE_MODIFY;
        moved.replacementDateEpochDay = TUESDAY.toEpochDay();
        moved.startMinute = 630;
        moved.endMinute = 690;
        List<ScheduleEntity> schedules = Arrays.asList(
                schedule(1L, 1, 540, 600, MONDAY, MONDAY),
                schedule(2L, 1, 540, 600, MONDAY, MONDAY.plusDays(21))
        );

        assertTrue(ScheduleExceptionConflictValidator.hasConflict(
                candidate, schedules, Collections.singletonList(moved)));
    }

    @Test
    public void sameDayModificationReplacesItsOriginalOccurrenceWithoutSelfConflict() {
        ScheduleExceptionEntity candidate = new ScheduleExceptionEntity();
        candidate.scheduleId = 1L;
        candidate.originalDateEpochDay = MONDAY.toEpochDay();
        candidate.exceptionType = ScheduleExceptionEntity.TYPE_MODIFY;
        candidate.startMinute = 550;
        candidate.endMinute = 570;
        ScheduleEntity original = schedule(1L, 1, 540, 600, MONDAY, MONDAY);

        assertFalse(ScheduleExceptionConflictValidator.hasConflict(candidate,
                Collections.singletonList(original), Collections.emptyList()));
    }

    @Test
    public void detectsRecurringScheduleAgainstMovedOccurrenceFromAnotherSchedule() {
        ScheduleEntity candidate = schedule(3L, 2, 570, 630,
                MONDAY.minusDays(7), MONDAY.plusDays(21));
        ScheduleEntity source = schedule(2L, 1, 540, 600,
                MONDAY.minusDays(7), MONDAY.plusDays(21));
        ScheduleExceptionEntity moved = new ScheduleExceptionEntity();
        moved.scheduleId = 2L;
        moved.originalDateEpochDay = MONDAY.plusDays(7).toEpochDay();
        moved.exceptionType = ScheduleExceptionEntity.TYPE_MAKEUP;
        moved.replacementDateEpochDay = TUESDAY.toEpochDay();

        assertTrue(ScheduleExceptionConflictValidator.hasConflictWithRecurringSchedule(
                candidate, Arrays.asList(candidate, source), Collections.singletonList(moved)));
    }

    @Test
    public void ignoresOriginalOccurrenceReplacedByItsOwnModification() {
        ScheduleEntity candidate = schedule(1L, 1, 540, 600, MONDAY, MONDAY);
        ScheduleExceptionEntity moved = new ScheduleExceptionEntity();
        moved.scheduleId = 1L;
        moved.originalDateEpochDay = MONDAY.toEpochDay();
        moved.exceptionType = ScheduleExceptionEntity.TYPE_MODIFY;
        moved.replacementDateEpochDay = TUESDAY.toEpochDay();

        assertFalse(ScheduleExceptionConflictValidator.hasConflictWithRecurringSchedule(
                candidate, Collections.singletonList(candidate), Collections.singletonList(moved)));
    }

    private static ScheduleExceptionEntity makeup(long scheduleId, LocalDate original,
                                                  LocalDate replacement, int start, int end) {
        ScheduleExceptionEntity result = new ScheduleExceptionEntity();
        result.scheduleId = scheduleId;
        result.originalDateEpochDay = original.toEpochDay();
        result.exceptionType = ScheduleExceptionEntity.TYPE_MAKEUP;
        result.replacementDateEpochDay = replacement.toEpochDay();
        result.startMinute = start;
        result.endMinute = end;
        return result;
    }

    private static ScheduleEntity schedule(long id, int day, int start, int end,
                                           LocalDate from, LocalDate to) {
        ScheduleEntity result = new ScheduleEntity();
        result.id = id;
        result.dayOfWeek = day;
        result.startMinute = start;
        result.endMinute = end;
        result.validFromEpochDay = from.toEpochDay();
        result.validToEpochDay = to.toEpochDay();
        return result;
    }
}
