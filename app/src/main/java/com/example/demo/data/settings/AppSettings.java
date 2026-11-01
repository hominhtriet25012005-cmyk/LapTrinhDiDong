package com.example.demo.data.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Persistent look-and-feel choices: theme and first day of the week. */
public final class AppSettings {
    private static final String FILE_NAME = "app_settings";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_FIRST_DAY = "first_day_of_week";

    private AppSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /** One of AppCompatDelegate.MODE_NIGHT_NO / MODE_NIGHT_YES / MODE_NIGHT_FOLLOW_SYSTEM. */
    public static int nightMode(Context context) {
        int mode = prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (mode == AppCompatDelegate.MODE_NIGHT_NO || mode == AppCompatDelegate.MODE_NIGHT_YES) {
            return mode;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public static void setNightMode(Context context, int mode) {
        if (mode != AppCompatDelegate.MODE_NIGHT_NO && mode != AppCompatDelegate.MODE_NIGHT_YES) {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** ISO day of week: 1 = Monday, 6 = Saturday, 7 = Sunday. */
    public static int firstDayOfWeek(Context context) {
        return WeekSettings.fromIso(prefs(context).getInt(KEY_FIRST_DAY, 1)).getValue();
    }

    public static void setFirstDayOfWeek(Context context, int isoDayOfWeek) {
        int value = WeekSettings.fromIso(isoDayOfWeek).getValue();
        prefs(context).edit().putInt(KEY_FIRST_DAY, value).apply();
        WeekSettings.setFirstDay(value);
    }

    /** Called once when the process starts. */
    public static void applySaved(Context context) {
        AppCompatDelegate.setDefaultNightMode(nightMode(context));
        WeekSettings.setFirstDay(firstDayOfWeek(context));
    }
}