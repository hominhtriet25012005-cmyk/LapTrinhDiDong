package com.example.demo.notifications;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.demo.MainActivity;
import com.example.demo.R;
import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns durable schedule and daily-summary alarms and their notifications. */
public final class NotificationScheduler {
    public static final String ACTION_SCHEDULE_REMINDER =
            "com.example.demo.action.SCHEDULE_REMINDER";
    public static final String ACTION_DAILY_SUMMARY =
            "com.example.demo.action.DAILY_SUMMARY";
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_CLASS_STARTS_AT = "class_starts_at";

    private static final String CHANNEL_ID = "schedule_reminders";
    private static final String CHANNEL_NAME = "Nhắc lịch học";
    private static final String PREF_ALARMS = "scheduled_notification_alarms";
    private static final String KEY_SCHEDULE_IDS = "schedule_ids";
    private static final String KEY_SUMMARY_ALARM_DATE = "summary_alarm_date";
    private static final String KEY_SUMMARY_ALARM_HOUR = "summary_alarm_hour";
    private static final String KEY_SUMMARY_ALARM_MINUTE = "summary_alarm_minute";
    private static final String SUMMARY_ALARM_URI = "schedule-reminder://daily-summary";
    private static final int SUMMARY_NOTIFICATION_ID = 0x534348;
    private static final String TAG = "NotificationScheduler";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "schedule-notification-sync");
        thread.setDaemon(true);
        return thread;
    });

    private NotificationScheduler() {
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        manager.createNotificationChannel(channel);
    }

    public static boolean notificationsAllowed(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            NotificationChannel channel = manager == null ? null : manager.getNotificationChannel(CHANNEL_ID);
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                return false;
            }
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void syncAll(Context context) {
        syncAll(context, null);
    }

    public static void syncAll(Context context, Runnable completion) {
        Context appContext = context.getApplicationContext();
        createNotificationChannel(appContext);
        EXECUTOR.execute(() -> {
            try {
                syncAllOnWorker(appContext);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Không thể đồng bộ thông báo lịch học.", exception);
            } finally {
                if (completion != null) {
                    completion.run();
                }
            }
        });
    }

    public static void rescheduleAfterSystemChange(Context context, Runnable completion) {
        Context appContext = context.getApplicationContext();
        appContext.getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE).edit()
                .remove(KEY_SUMMARY_ALARM_DATE)
                .apply();
        syncAll(appContext, completion);
    }

    public static void setRemindersEnabled(Context context, boolean enabled) {
        Context appContext = context.getApplicationContext();
        NotificationPreferences.setRemindersEnabled(appContext, enabled);
        EXECUTOR.execute(() -> {
            try {
                AppDatabase.getInstance(appContext).scheduleDao()
                        .setNotificationEnabledForAll(enabled);
                syncAllOnWorker(appContext);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Không thể cập nhật cài đặt nhắc lịch.", exception);
            }
        });
    }

    public static void setDailySummaryEnabled(Context context, boolean enabled) {
        Context appContext = context.getApplicationContext();
        NotificationPreferences.setDailySummaryEnabled(appContext, enabled);
        syncAll(appContext);
    }

    public static void setDailySummaryTime(Context context, int hour, int minute) {
        Context appContext = context.getApplicationContext();
        NotificationPreferences.setSummaryTime(appContext, hour, minute);
        syncAll(appContext);
    }

    static void handleAlarm(Context context, Intent intent, Runnable completion) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                if (ACTION_DAILY_SUMMARY.equals(intent.getAction())) {
                    showDailySummary(appContext);
                } else if (ACTION_SCHEDULE_REMINDER.equals(intent.getAction())) {
                    long id = intent.getLongExtra(EXTRA_SCHEDULE_ID, 0L);
                    long classStartsAt = intent.getLongExtra(EXTRA_CLASS_STARTS_AT, 0L);
                    showScheduleReminder(appContext, id, classStartsAt);
                }
            } catch (RuntimeException exception) {
                Log.e(TAG, "Không thể xử lý thông báo đến hạn.", exception);
            } finally {
                if (completion != null) {
                    completion.run();
                }
            }
        });
    }

    private static void syncAllOnWorker(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        boolean remindersEnabled = NotificationPreferences.remindersEnabled(context);
        if (!NotificationPreferences.remindersInitialized(context)) {
            database.scheduleDao().setNotificationEnabledForAll(remindersEnabled);
            NotificationPreferences.setRemindersEnabled(context, remindersEnabled);
        }

        List<ScheduleEntity> schedules = database.scheduleDao().getAll();
        Set<String> previouslyScheduled = new HashSet<>(context
                .getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE)
                .getStringSet(KEY_SCHEDULE_IDS, new HashSet<>()));
        Set<String> scheduledNow = new HashSet<>();
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        for (ScheduleEntity schedule : schedules) {
            if (!remindersEnabled || !schedule.notificationEnabled) {
                continue;
            }
            ScheduleReminderCalculator.Occurrence occurrence =
                    ScheduleReminderCalculator.nextOccurrence(schedule, now, zone);
            if (occurrence == null) {
                continue;
            }
            scheduleReminder(context, schedule.id, occurrence);
            scheduledNow.add(Long.toString(schedule.id));
        }

        for (String oldId : previouslyScheduled) {
            if (!scheduledNow.contains(oldId)) {
                try {
                    cancelScheduleAlarm(context, Long.parseLong(oldId));
                    NotificationManagerCompat.from(context).cancel(notificationId(oldId));
                } catch (NumberFormatException ignored) {
                    // Ignore corrupt/stale preference entries and repair the set below.
                }
            }
        }
        context.getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_SCHEDULE_IDS, scheduledNow)
                .apply();

        if (NotificationPreferences.dailySummaryEnabled(context)) {
            ensureSummaryAlarm(context);
        } else {
            cancelSummaryAlarm(context);
            NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID);
            clearSummaryAlarmMetadata(context);
        }
    }

    private static void ensureSummaryAlarm(Context context) {
        android.content.SharedPreferences preferences = context
                .getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE);
        int hour = NotificationPreferences.summaryHour(context);
        int minute = NotificationPreferences.summaryMinute(context);
        long scheduledDay = preferences.getLong(KEY_SUMMARY_ALARM_DATE, Long.MIN_VALUE);
        boolean settingsMatch = preferences.getInt(KEY_SUMMARY_ALARM_HOUR, -1) == hour
                && preferences.getInt(KEY_SUMMARY_ALARM_MINUTE, -1) == minute;
        PendingIntent existingAlarm = summaryPendingIntent(context, PendingIntent.FLAG_NO_CREATE);
        long today = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        if (existingAlarm != null && settingsMatch && scheduledDay >= today) {
            return;
        }
        scheduleNextSummary(context);
    }

    private static void scheduleReminder(Context context, long scheduleId,
                                         ScheduleReminderCalculator.Occurrence occurrence) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) {
            return;
        }
        PendingIntent pendingIntent = reminderPendingIntent(context, scheduleId,
                occurrence.classStartsAtMillis, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    occurrence.reminderAtMillis, pendingIntent);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Không thể lên lịch nhắc cho lịch " + scheduleId, exception);
        }
    }

    private static void scheduleNextSummary(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) {
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalTime summaryTime = LocalTime.of(NotificationPreferences.summaryHour(context),
                NotificationPreferences.summaryMinute(context));
        LocalDateTime next = now.toLocalDate().atTime(summaryTime);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                next.atZone(zone).toInstant().toEpochMilli(),
                summaryPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT));
        context.getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_SUMMARY_ALARM_DATE, next.toLocalDate().toEpochDay())
                .putInt(KEY_SUMMARY_ALARM_HOUR, NotificationPreferences.summaryHour(context))
                .putInt(KEY_SUMMARY_ALARM_MINUTE, NotificationPreferences.summaryMinute(context))
                .apply();
    }

    private static void showScheduleReminder(Context context, long scheduleId,
                                             long classStartsAtMillis) {
        AppDatabase database = AppDatabase.getInstance(context);
        ScheduleEntity schedule = null;
        for (ScheduleEntity item : database.scheduleDao().getAll()) {
            if (item.id == scheduleId) {
                schedule = item;
                break;
            }
        }
        if (schedule == null || !NotificationPreferences.remindersEnabled(context)
                || !schedule.notificationEnabled) {
            cancelScheduleAlarm(context, scheduleId);
            return;
        }

        ScheduleDetails details = null;
        for (ScheduleDetails item : database.scheduleDao().getAllDetails()) {
            if (item.scheduleId == scheduleId) {
                details = item;
                break;
            }
        }
        if (details == null) {
            cancelScheduleAlarm(context, scheduleId);
            return;
        }
        if (notificationsAllowed(context)) {
            String title = context.getString(R.string.notification_reminder_title,
                    safe(details.subjectName), safe(details.classCode));
            String body = context.getString(R.string.notification_reminder_body,
                    formatTime(classStartsAtMillis, ZoneId.systemDefault()),
                    details.room == null || details.room.trim().isEmpty()
                            ? context.getString(R.string.notification_room_unknown) : details.room);
            postNotification(context, notificationId(scheduleId), title, body);
        }

        ScheduleReminderCalculator.Occurrence next = ScheduleReminderCalculator.nextOccurrence(
                schedule, Instant.now(), ZoneId.systemDefault());
        if (next == null) {
            cancelScheduleAlarm(context, scheduleId);
        } else {
            scheduleReminder(context, scheduleId, next);
        }
    }

    private static void showDailySummary(Context context) {
        if (!NotificationPreferences.dailySummaryEnabled(context)) {
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        List<ScheduleDetails> todaysSchedules = new ArrayList<>();
        for (ScheduleDetails schedule : AppDatabase.getInstance(context)
                .scheduleDao().getAllDetails()) {
            if (schedule.dayOfWeek == today.getDayOfWeek().getValue()
                    && today.toEpochDay() >= schedule.validFromEpochDay
                    && today.toEpochDay() <= schedule.validToEpochDay) {
                todaysSchedules.add(schedule);
            }
        }

        if (notificationsAllowed(context)) {
            String title = context.getString(R.string.notification_summary_title);
            String body;
            if (todaysSchedules.isEmpty()) {
                body = context.getString(R.string.notification_summary_empty);
            } else {
                ScheduleDetails first = todaysSchedules.get(0);
                body = context.getString(R.string.notification_summary_body,
                        todaysSchedules.size(), formatTime(first.startMinute),
                        safe(first.subjectName), safe(first.classCode));
            }
            postNotification(context, SUMMARY_NOTIFICATION_ID, title, body);
        }
        scheduleNextSummary(context);
    }

    private static void postNotification(Context context, int id, String title, String body) {
        createNotificationChannel(context);
        Intent openApp = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, id, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
            // The user can revoke notification access while an alarm is being delivered.
        }
    }

    private static PendingIntent reminderPendingIntent(Context context, long scheduleId,
                                                       long classStartsAt, int flag) {
        Intent intent = new Intent(context, ScheduleNotificationReceiver.class)
                .setAction(ACTION_SCHEDULE_REMINDER)
                .setData(Uri.parse("schedule-reminder://schedule/" + scheduleId))
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                .putExtra(EXTRA_CLASS_STARTS_AT, classStartsAt);
        return PendingIntent.getBroadcast(context, 0, intent,
                flag | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent summaryPendingIntent(Context context, int flag) {
        Intent intent = new Intent(context, ScheduleNotificationReceiver.class)
                .setAction(ACTION_DAILY_SUMMARY)
                .setData(Uri.parse(SUMMARY_ALARM_URI));
        return PendingIntent.getBroadcast(context, 0, intent,
                flag | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelScheduleAlarm(Context context, long scheduleId) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = reminderPendingIntent(context, scheduleId, 0L,
                PendingIntent.FLAG_NO_CREATE);
        if (manager != null && pendingIntent != null) {
            manager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private static void cancelSummaryAlarm(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = summaryPendingIntent(context, PendingIntent.FLAG_NO_CREATE);
        if (manager != null && pendingIntent != null) {
            manager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private static void clearSummaryAlarmMetadata(Context context) {
        context.getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE).edit()
                .remove(KEY_SUMMARY_ALARM_DATE)
                .remove(KEY_SUMMARY_ALARM_HOUR)
                .remove(KEY_SUMMARY_ALARM_MINUTE)
                .apply();
    }

    private static int notificationId(long scheduleId) {
        return (int) (scheduleId & 0x7fffffff);
    }

    private static int notificationId(String scheduleId) {
        try {
            return notificationId(Long.parseLong(scheduleId));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String formatTime(long epochMillis, ZoneId zone) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone);
        return formatTime(dateTime.getHour() * 60 + dateTime.getMinute());
    }

    private static String formatTime(int minuteOfDay) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d",
                minuteOfDay / 60, minuteOfDay % 60);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }
}
