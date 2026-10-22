package com.example.demo.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ScheduleNotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        PendingResult pendingResult = goAsync();
        NotificationScheduler.handleAlarm(context, intent, pendingResult::finish);
    }
}
