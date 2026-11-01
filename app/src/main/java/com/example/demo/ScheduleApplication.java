package com.example.demo;

import android.app.Application;

import com.example.demo.settings.AppSettings;

/** Applies saved app settings before Android creates the launcher Activity. */
public class ScheduleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppSettings.applySavedTheme(this);
    }
}
