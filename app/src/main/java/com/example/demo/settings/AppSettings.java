package com.example.demo.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Persists app-wide preferences that must be available before the first Activity is created. */
public final class AppSettings {
    static final String PREFERENCES_FILE = "personal_schedule_settings";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";

    private AppSettings() {
    }

    public static boolean isDarkModeEnabled(Context context) {
        return preferences(context).getBoolean(KEY_DARK_MODE, false);
    }

    public static void setDarkModeEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply();
        AppCompatDelegate.setDefaultNightMode(enabled
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** Call from Application.onCreate so the saved theme is set before any Activity is inflated. */
    public static void applySavedTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(isDarkModeEnabled(context)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    static SharedPreferences preferences(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES_FILE, Context.MODE_PRIVATE);
    }
}
