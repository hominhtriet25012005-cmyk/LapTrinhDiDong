package com.example.demo.settings;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class WeekCalculatorTest {
    @Test
    public void resolvesMondayThroughSundayWeek() {
        LocalDate selected = LocalDate.of(2026, 3, 18);

        assertEquals(LocalDate.of(2026, 3, 16),
                WeekCalculator.startOfWeek(selected, WeekCalculator.MONDAY));
        assertEquals(LocalDate.of(2026, 3, 22),
                WeekCalculator.endOfWeek(selected, WeekCalculator.MONDAY));
    }

    @Test
    public void resolvesSundayThroughSaturdayWeekAcrossMonthBoundary() {
        LocalDate selected = LocalDate.of(2026, 3, 1);

        assertEquals(LocalDate.of(2026, 3, 1),
                WeekCalculator.startOfWeek(selected, WeekCalculator.SUNDAY));
        assertEquals(LocalDate.of(2026, 3, 7),
                WeekCalculator.endOfWeek(selected, WeekCalculator.SUNDAY));
        assertEquals(LocalDate.of(2026, 2, 22),
                WeekCalculator.startOfWeek(LocalDate.of(2026, 2, 28), WeekCalculator.SUNDAY));
    }

    @Test
    public void rejectsInvalidFirstDayAndNullDate() {
        assertThrows(IllegalArgumentException.class,
                () -> WeekCalculator.startOfWeek(LocalDate.now(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> WeekCalculator.startOfWeek(null, WeekCalculator.MONDAY));
    }
}
