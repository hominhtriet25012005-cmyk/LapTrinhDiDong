package com.example.demo;

import android.app.Application;

import com.example.demo.data.settings.AppSettings;

public class UniScheduleApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppSettings.applySaved(this);
    }
}