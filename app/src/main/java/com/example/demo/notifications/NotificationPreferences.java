package com.example.demo.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** Persistent user choices shown by the existing notification settings screen. */
public final class NotificationPreferences {
    private static final String FILE_NAME = "notification_settings";
    private static final String KEY_REMINDERS_ENABLED = "reminders_enabled";
    private static final String KEY_REMINDERS_INITIALIZED = "reminders_initialized";
    private static final String KEY_DAILY_SUMMARY_ENABLED = "daily_summary_enabled";
    private static final String KEY_SUMMARY_HOUR = "summary_hour";
    private static final String KEY_SUMMARY_MINUTE = "summary_minute";

    private NotificationPreferences() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public static boolean remindersEnabled(Context context) {
        return preferences(context).getBoolean(KEY_REMINDERS_ENABLED, true);
    }

    public static boolean remindersInitialized(Context context) {
        return preferences(context).getBoolean(KEY_REMINDERS_INITIALIZED, false);
    }

    public static void setRemindersEnabled(Context context, boolean enabled) {
        preferences(context).edit()
                .putBoolean(KEY_REMINDERS_ENABLED, enabled)
                .putBoolean(KEY_REMINDERS_INITIALIZED, true)
                .apply();
    }

    public static boolean dailySummaryEnabled(Context context) {
        return preferences(context).getBoolean(KEY_DAILY_SUMMARY_ENABLED, true);
    }

    public static void setDailySummaryEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_DAILY_SUMMARY_ENABLED, enabled).apply();
    }

    public static int summaryHour(Context context) {
        return preferences(context).getInt(KEY_SUMMARY_HOUR, 6);
    }

    public static int summaryMinute(Context context) {
        return preferences(context).getInt(KEY_SUMMARY_MINUTE, 0);
    }

    public static void setSummaryTime(Context context, int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Giờ tóm tắt không hợp lệ.");
        }
        preferences(context).edit()
                .putInt(KEY_SUMMARY_HOUR, hour)
                .putInt(KEY_SUMMARY_MINUTE, minute)
                .apply();
    }

    public static String formatSummaryTime(Context context) {
        return String.format(Locale.ROOT, "%02d:%02d", summaryHour(context), summaryMinute(context));
    }
}
