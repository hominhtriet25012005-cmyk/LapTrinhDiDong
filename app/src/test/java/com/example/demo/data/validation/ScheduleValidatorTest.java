package com.example.demo.data.validation;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class ScheduleValidatorTest {

    @Test
    public void acceptsValidWeeklySchedule() {
        ScheduleValidator.validateSchedule(1, 450, 570, 20_000L, 20_100L);
    }

    @Test
    public void acceptsClassEndingAtMidnight() {
        ScheduleValidator.validateSchedule(7, 1_380, 1_440, 20_000L, 20_000L);
    }

    @Test
    public void rejectsDayOutsideIsoWeekRange() {
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSchedule(0, 450, 570, 20_000L, 20_100L));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSchedule(8, 450, 570, 20_000L, 20_100L));
    }

    @Test
    public void rejectsInvalidOrNonPositiveTimeRange() {
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSchedule(1, -1, 570, 20_000L, 20_100L));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSchedule(1, 1_440, 1_500, 20_000L, 20_100L));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSchedule(1, 570, 570, 20_000L, 20_100L));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSchedule(1, 600, 570, 20_000L, 20_100L));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSchedule(1, 1_000, 1_441, 20_000L, 20_100L));
    }

    @Test
    public void rejectsEndDateBeforeStartDate() {
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSchedule(1, 450, 570, 20_100L, 20_000L));
    }

    @Test
    public void allowsOptionalSemesterDatesButChecksTheirOrder() {
        ScheduleValidator.validateSemesterDates(null, null);
        ScheduleValidator.validateSemesterDates(20_000L, null);
        ScheduleValidator.validateSemesterDates(null, 20_100L);

        assertThrows(IllegalArgumentException.class,
                () -> ScheduleValidator.validateSemesterDates(20_100L, 20_000L));
    }
}
