package com.example.demo.settings;

import java.time.LocalDate;

/** Pure date helpers shared by dashboard, timetable and export week calculations. */
public final class WeekCalculator {
    public static final int MONDAY = 1;
    public static final int SUNDAY = 7;

    private WeekCalculator() {
    }

    public static LocalDate startOfWeek(LocalDate date, int firstDayOfWeek) {
        validate(date, firstDayOfWeek);
        int daysSinceStart = Math.floorMod(
                date.getDayOfWeek().getValue() - firstDayOfWeek, 7);
        return date.minusDays(daysSinceStart);
    }

    public static LocalDate endOfWeek(LocalDate date, int firstDayOfWeek) {
        return startOfWeek(date, firstDayOfWeek).plusDays(6);
    }

    public static int weekdayOrder(int isoDayOfWeek, int firstDayOfWeek) {
        if (isoDayOfWeek < MONDAY || isoDayOfWeek > SUNDAY) {
            throw new IllegalArgumentException("Ngày trong tuần phải nằm trong khoảng 1 đến 7.");
        }
        if (firstDayOfWeek < MONDAY || firstDayOfWeek > SUNDAY) {
            throw new IllegalArgumentException("Ngày đầu tuần phải nằm trong khoảng 1 đến 7.");
        }
        return Math.floorMod(isoDayOfWeek - firstDayOfWeek, 7);
    }

    private static void validate(LocalDate date, int firstDayOfWeek) {
        if (date == null) {
            throw new IllegalArgumentException("Ngày cần tính tuần không được để trống.");
        }
        if (firstDayOfWeek < MONDAY || firstDayOfWeek > SUNDAY) {
            throw new IllegalArgumentException("Ngày đầu tuần phải nằm trong khoảng 1 đến 7.");
        }
    }
}
