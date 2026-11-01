package com.example.demo.settings;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.demo.ScheduleApplication;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class AppSettingsTest {
    @Test
    public void applicationAppliesStoredThemeBeforeActivitiesStart() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getApplicationContext() instanceof ScheduleApplication);
        assertEquals(AppSettings.isDarkModeEnabled(context)
                        ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void persistsThemeAndFirstDaySettingsInSharedPreferences() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean previousTheme = AppSettings.isDarkModeEnabled(context);
        int previousWeekStart = WeekSettings.getFirstDayOfWeek(context);
        int previousRuntimeMode = AppCompatDelegate.getDefaultNightMode();
        try {
            AppSettings.setDarkModeEnabled(context, true);
            assertTrue(AppSettings.isDarkModeEnabled(context));
            AppSettings.applySavedTheme(context);
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES,
                    AppCompatDelegate.getDefaultNightMode());
            AppSettings.setDarkModeEnabled(context, false);
            assertFalse(AppSettings.isDarkModeEnabled(context));
            AppSettings.applySavedTheme(context);
            assertEquals(AppCompatDelegate.MODE_NIGHT_NO,
                    AppCompatDelegate.getDefaultNightMode());

            WeekSettings.setFirstDayOfWeek(context, WeekSettings.SUNDAY);
            assertEquals(WeekSettings.SUNDAY, WeekSettings.getFirstDayOfWeek(context));
            WeekSettings.setFirstDayOfWeek(context, WeekSettings.MONDAY);
            assertEquals(WeekSettings.MONDAY, WeekSettings.getFirstDayOfWeek(context));
            assertThrows(IllegalArgumentException.class,
                    () -> WeekSettings.setFirstDayOfWeek(context, 0));
        } finally {
            AppSettings.setDarkModeEnabled(context, previousTheme);
            WeekSettings.setFirstDayOfWeek(context, previousWeekStart);
            AppCompatDelegate.setDefaultNightMode(previousRuntimeMode);
        }
    }
}
