package com.example.demo.settings;

import android.content.SharedPreferences;
import android.content.Context;

/** Stores the ISO weekday (Monday=1 through Sunday=7) used as the start of a calendar week. */
public final class WeekSettings {
    public static final int MONDAY = 1;
    public static final int SUNDAY = 7;
    static final String KEY_FIRST_DAY_OF_WEEK = "first_day_of_week";

    private WeekSettings() {
    }

    public static int getFirstDayOfWeek(Context context) {
        int firstDay = AppSettings.preferences(context)
                .getInt(KEY_FIRST_DAY_OF_WEEK, MONDAY);
        return isValidFirstDay(firstDay) ? firstDay : MONDAY;
    }

    public static void setFirstDayOfWeek(Context context, int firstDay) {
        if (!isValidFirstDay(firstDay)) {
            throw new IllegalArgumentException("Ngày đầu tuần phải nằm trong khoảng 1 đến 7.");
        }
        AppSettings.preferences(context).edit()
                .putInt(KEY_FIRST_DAY_OF_WEEK, firstDay)
                .apply();
    }

    private static boolean isValidFirstDay(int firstDay) {
        return firstDay >= MONDAY && firstDay <= SUNDAY;
    }
}
