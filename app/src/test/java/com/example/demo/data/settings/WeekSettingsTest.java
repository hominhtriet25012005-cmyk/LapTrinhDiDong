package com.example.demo.data.settings;

import static org.junit.Assert.assertEquals;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.After;
import org.junit.Test;

public class WeekSettingsTest {
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 21);

    @After
    public void reset() {
        WeekSettings.setFirstDay(1);
    }

    @Test
    public void mondayIsDefault() {
        assertEquals(LocalDate.of(2026, 10, 19), WeekSettings.weekStart(WEDNESDAY));
        assertEquals(LocalDate.of(2026, 10, 25), WeekSettings.weekEnd(WEDNESDAY));
    }

    @Test
    public void sundayStartsPreviousSunday() {
        WeekSettings.setFirstDay(7);
        assertEquals(LocalDate.of(2026, 10, 18), WeekSettings.weekStart(WEDNESDAY));
        assertEquals(LocalDate.of(2026, 10, 24), WeekSettings.weekEnd(WEDNESDAY));
        assertEquals(LocalDate.of(2026, 10, 25), WeekSettings.weekStart(LocalDate.of(2026, 10, 25)));
    }

    @Test
    public void saturdayStartAndInvalidFallback() {
        WeekSettings.setFirstDay(6);
        assertEquals(LocalDate.of(2026, 10, 17), WeekSettings.weekStart(WEDNESDAY));
        WeekSettings.setFirstDay(99);
        assertEquals(DayOfWeek.MONDAY, WeekSettings.getFirstDay());
    }
}