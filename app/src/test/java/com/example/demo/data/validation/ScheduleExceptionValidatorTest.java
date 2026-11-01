package com.example.demo.data.validation;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.LocalDate;

public class ScheduleExceptionValidatorTest {
    private static final long MONDAY = LocalDate.of(2026, 9, 7).toEpochDay();
    private static final long TUESDAY = MONDAY + 1;

    @Test
    public void occurrenceMatchesWeekdayAndRange() {
        ScheduleExceptionValidator.validateOccurrence(1, MONDAY - 70, MONDAY + 70, MONDAY);
        ScheduleExceptionValidator.validateOccurrence(2, MONDAY - 70, MONDAY + 70, TUESDAY);
        ScheduleExceptionValidator.validateOccurrence(7, MONDAY - 70, MONDAY + 70, MONDAY + 6);
    }

    @Test
    public void occurrenceRejectsWrongWeekday() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validateOccurrence(2, MONDAY - 70, MONDAY + 70, MONDAY));
    }

    @Test
    public void occurrenceRejectsOutsideRange() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validateOccurrence(1, MONDAY + 7, MONDAY + 70, MONDAY));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validateOccurrence(1, MONDAY - 70, MONDAY - 7, MONDAY));
    }

    @Test
    public void cancelRequiresScheduleAndDate() {
        ScheduleExceptionValidator.validate("CANCEL", 5L, 2, MONDAY, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("CANCEL", null, 2, MONDAY, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("CANCEL", 5L, 2, null, null, null, null, null));
    }

    @Test
    public void cancelRejectsNewValues() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("CANCEL", 5L, 2, MONDAY, TUESDAY, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("CANCEL", 5L, 2, MONDAY, null, null, null, "A1"));
    }

    @Test
    public void modifyAcceptsRoomOnly() {
        ScheduleExceptionValidator.validate("MODIFY", 5L, 2, MONDAY, null, null, null, "A101");
    }

    @Test
    public void modifyAcceptsTimeRangeOrMovedDate() {
        ScheduleExceptionValidator.validate("MODIFY", 5L, 2, MONDAY, null, 480, 570, null);
        ScheduleExceptionValidator.validate("MODIFY", 5L, 2, MONDAY, TUESDAY, null, null, null);
    }

    @Test
    public void modifyRejectsNothingChanged() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("MODIFY", 5L, 2, MONDAY, null, null, null, "  "));
    }

    @Test
    public void modifyRejectsHalfOrInvertedTime() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("MODIFY", 5L, 2, MONDAY, null, 480, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("MODIFY", 5L, 2, MONDAY, null, 600, 600, null));
    }

    @Test
    public void makeupAcceptsFullDetails() {
        ScheduleExceptionValidator.validate("MAKEUP", null, 2, null, TUESDAY, 480, 570, "B2");
    }

    @Test
    public void makeupRejectsOriginalReference() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("MAKEUP", 5L, 2, null, TUESDAY, 480, 570, null));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("MAKEUP", null, 2, MONDAY, TUESDAY, 480, 570, null));
    }

    @Test
    public void makeupRequiresDateAndValidTime() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("MAKEUP", null, 2, null, null, 480, 570, null));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("MAKEUP", null, 2, null, TUESDAY, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("MAKEUP", null, 2, null, TUESDAY, 480, 1500, null));
    }

    @Test
    public void rejectsUnknownTypeAndMissingClassSection() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("XXX", 5L, 2, MONDAY, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate(null, 5L, 2, MONDAY, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleExceptionValidator.validate("CANCEL", 5L, 0, MONDAY, null, null, null, null));
    }
}