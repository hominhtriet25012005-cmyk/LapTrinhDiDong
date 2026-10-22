package com.example.demo.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rebuilds AlarmManager entries lost on reboot and recalculates them after clock changes. */
public final class NotificationRescheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        PendingResult pendingResult = goAsync();
        NotificationScheduler.rescheduleAfterSystemChange(context, pendingResult::finish);
    }
}
