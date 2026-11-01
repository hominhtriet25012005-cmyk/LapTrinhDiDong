package com.example.demo.data.settings;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Pure-Java holder of the user's first day of the week; the app loads it at start-up and on change. */
public final class WeekSettings {
    private static volatile DayOfWeek firstDay = DayOfWeek.MONDAY;

    private WeekSettings() {
    }

    public static DayOfWeek getFirstDay() {
        return firstDay;
    }

    /** Only Monday, Saturday and Sunday are offered; anything else falls back to Monday. */
    public static void setFirstDay(int isoDayOfWeek) {
        firstDay = fromIso(isoDayOfWeek);
    }

    public static DayOfWeek fromIso(int isoDayOfWeek) {
        if (isoDayOfWeek == DayOfWeek.SUNDAY.getValue()) return DayOfWeek.SUNDAY;
        if (isoDayOfWeek == DayOfWeek.SATURDAY.getValue()) return DayOfWeek.SATURDAY;
        return DayOfWeek.MONDAY;
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(firstDay));
    }

    public static LocalDate weekEnd(LocalDate date) {
        return weekStart(date).plusDays(6);
    }
}