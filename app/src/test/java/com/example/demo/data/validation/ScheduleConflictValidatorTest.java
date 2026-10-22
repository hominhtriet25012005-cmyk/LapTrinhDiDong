package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleEntity;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduleConflictValidatorTest {

    @Test
    public void reportsOverlappingTimeOnSameWeekdayAndAnActualSharedOccurrence() {
        ScheduleEntity candidate = schedule(0, 1, 540, 660,
                LocalDate.of(2026, 3, 16), LocalDate.of(2026, 6, 30));
        ScheduleEntity existing = schedule(2, 1, 630, 720,
                LocalDate.of(2026, 3, 20), LocalDate.of(2026, 7, 1));

        assertTrue(ScheduleConflictValidator.hasConflict(candidate, Arrays.asList(existing)));
    }

    @Test
    public void allowsAdjacentTimesDifferentDaysAndNonMatchingWeekdayDateWindows() {
        ScheduleEntity candidate = schedule(0, 1, 540, 600,
                LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 16));

        ScheduleEntity adjacent = schedule(2, 1, 600, 660,
                LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 16));
        ScheduleEntity otherDay = schedule(3, 2, 550, 590,
                LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 30));
        ScheduleEntity noOccurrence = schedule(4, 1, 550, 590,
                LocalDate.of(2026, 3, 17), LocalDate.of(2026, 3, 17));

        assertFalse(ScheduleConflictValidator.hasConflict(candidate,
                Arrays.asList(adjacent, otherDay, noOccurrence)));
    }

    @Test
    public void ignoresTheScheduleBeingEdited() {
        ScheduleEntity candidate = schedule(9, 1, 540, 600,
                LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 30));

        assertFalse(ScheduleConflictValidator.hasConflict(candidate,
                Collections.singletonList(candidate)));
    }

    private static ScheduleEntity schedule(long id, int weekday, int start, int end,
                                           LocalDate from, LocalDate to) {
        ScheduleEntity schedule = new ScheduleEntity(1L, weekday, start, end, "", 
                from.toEpochDay(), to.toEpochDay(), "");
        schedule.id = id;
        return schedule;
    }
}
