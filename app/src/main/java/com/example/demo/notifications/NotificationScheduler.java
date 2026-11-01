package com.example.demo.notifications;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleResolver;
import com.example.demo.data.schedule.ScheduleSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns durable per-session reminder alarms and the daily-summary alarm. Alarms are planned from
 * resolved class sessions, so cancelled sessions are skipped and changed or makeup sessions
 * remind at their own date and time.
 */
public final class NotificationScheduler {
    public static final String ACTION_SCHEDULE_REMINDER =
            "com.example.demo.action.SCHEDULE_REMINDER";
    public static final String ACTION_DAILY_SUMMARY =
            "com.example.demo.action.DAILY_SUMMARY";
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_EXCEPTION_ID = "exception_id";
    public static final String EXTRA_EPOCH_DAY = "epoch_day";
    public static final String EXTRA_CLASS_STARTS_AT = "class_starts_at";

    private static final int LOOKAHEAD_DAYS = 21;
    private static final int MAX_ALARMS = 200;
    private static final String CHANNEL_ID = "schedule_reminders";
    private static final String CHANNEL_NAME = "Nhắc lịch học";
    private static final String PREF_ALARMS = "scheduled_notification_alarms";
    /** Alarm keys scheduled by this version ("scheduleId-exceptionId-epochDay"). */
    private static final String KEY_OCCURRENCE_KEYS = "occurrence_keys";
    /** Old per-schedule alarms from earlier versions; cleaned up on the first sync. */
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
                    long epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE);
                    if (epochDay != Long.MIN_VALUE) {
                        showOccurrenceReminder(appContext,
                                intent.getLongExtra(EXTRA_SCHEDULE_ID, 0L),
                                intent.getLongExtra(EXTRA_EXCEPTION_ID, 0L),
                                epochDay,
                                intent.getLongExtra(EXTRA_CLASS_STARTS_AT, 0L));
                    }
                }
                // Keep the 21-day alarm window rolling forward after every alarm.
                syncAllOnWorker(appContext);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Không thể xử lý thông báo đến hạn.", exception);
            } finally {
                if (completion != null) {
                    completion.run();
                }
            }
        });
    }

    private static ScheduleSnapshot loadSnapshot(AppDatabase database) {
        ScheduleSnapshot[] holder = new ScheduleSnapshot[1];
        database.runInTransaction(() -> holder[0] = new ScheduleSnapshot(
                database.scheduleDao().getAllDetails(),
                database.scheduleExceptionDao().getAll(),
                database.scheduleExceptionDao().getAllClassSectionDetails()));
        return holder[0];
    }

    private static void syncAllOnWorker(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        boolean remindersEnabled = NotificationPreferences.remindersEnabled(context);
        if (!NotificationPreferences.remindersInitialized(context)) {
            database.scheduleDao().setNotificationEnabledForAll(remindersEnabled);
            NotificationPreferences.setRemindersEnabled(context, remindersEnabled);
        }

        SharedPreferences preferences = context.getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE);
        cancelLegacyScheduleAlarms(context, preferences);

        Set<String> previouslyScheduled = new HashSet<>(
                preferences.getStringSet(KEY_OCCURRENCE_KEYS, new HashSet<>()));
        Set<String> scheduledNow = new HashSet<>();
        List<ScheduleReminderPlanner.PlannedReminder> plan = ScheduleReminderPlanner.plan(
                loadSnapshot(database), Instant.now(), ZoneId.systemDefault(),
                LOOKAHEAD_DAYS, remindersEnabled, MAX_ALARMS);
        for (ScheduleReminderPlanner.PlannedReminder reminder : plan) {
            scheduleReminder(context, reminder);
            scheduledNow.add(reminder.key);
        }

        // Cancelled, moved, deleted or already-fired sessions no longer have an alarm.
        for (String oldKey : previouslyScheduled) {
            if (!scheduledNow.contains(oldKey)) {
                cancelReminderAlarm(context, oldKey);
            }
        }
        preferences.edit().putStringSet(KEY_OCCURRENCE_KEYS, scheduledNow).apply();

        if (NotificationPreferences.dailySummaryEnabled(context)) {
            ensureSummaryAlarm(context);
        } else {
            cancelSummaryAlarm(context);
            NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID);
            clearSummaryAlarmMetadata(context);
        }
    }

    private static void cancelLegacyScheduleAlarms(Context context, SharedPreferences preferences) {
        Set<String> legacyIds = preferences.getStringSet(KEY_SCHEDULE_IDS, null);
        if (legacyIds == null) {
            return;
        }
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        for (String id : legacyIds) {
            Intent intent = new Intent(context, ScheduleNotificationReceiver.class)
                    .setAction(ACTION_SCHEDULE_REMINDER)
                    .setData(Uri.parse("schedule-reminder://schedule/" + id));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (manager != null && pendingIntent != null) {
                manager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
        preferences.edit().remove(KEY_SCHEDULE_IDS).apply();
    }

    private static void ensureSummaryAlarm(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE);
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

    private static void scheduleReminder(Context context,
                                         ScheduleReminderPlanner.PlannedReminder reminder) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) {
            return;
        }
        ClassOccurrence occurrence = reminder.occurrence;
        PendingIntent pendingIntent = reminderPendingIntent(context, reminder.key,
                occurrence.scheduleId, occurrence.exceptionId, occurrence.epochDay,
                reminder.classStartsAtMillis, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    reminder.reminderAtMillis, pendingIntent);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Không thể lên lịch nhắc cho buổi " + reminder.key, exception);
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

    /** Re-checks the session when the alarm fires, so a cancel or change made since is honoured. */
    private static void showOccurrenceReminder(Context context, long scheduleId, long exceptionId,
                                               long epochDay, long classStartsAtMillis) {
        if (!NotificationPreferences.remindersEnabled(context)) {
            return;
        }
        ScheduleSnapshot snapshot = loadSnapshot(AppDatabase.getInstance(context));
        ClassOccurrence target = null;
        for (ClassOccurrence item : ScheduleResolver.resolveDay(snapshot, epochDay, false)) {
            if (item.scheduleId == scheduleId && item.exceptionId == exceptionId) {
                target = item;
                break;
            }
        }
        if (target == null
                || ScheduleReminderPlanner.startsAtMillis(target, ZoneId.systemDefault()) != classStartsAtMillis
                || (target.scheduleId > 0 && !target.notificationEnabled)
                || !notificationsAllowed(context)) {
            return;
        }

        int titleRes = target.status == ClassOccurrence.STATUS_MAKEUP
                ? R.string.notification_reminder_makeup_title
                : target.status == ClassOccurrence.STATUS_MODIFIED
                ? R.string.notification_reminder_modified_title
                : R.string.notification_reminder_title;
        String title = context.getString(titleRes, safe(target.subjectName), safe(target.classCode));
        String body = context.getString(R.string.notification_reminder_body,
                formatTime(target.startMinute),
                target.room == null || target.room.trim().isEmpty()
                        ? context.getString(R.string.notification_room_unknown) : target.room);
        postNotification(context, notificationId(
                ScheduleReminderPlanner.key(scheduleId, exceptionId, epochDay)), title, body);
    }

    private static void showDailySummary(Context context) {
        if (!NotificationPreferences.dailySummaryEnabled(context)) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<ClassOccurrence> todaysSessions = ScheduleResolver.resolveDay(
                loadSnapshot(AppDatabase.getInstance(context)), today.toEpochDay(), false);

        if (notificationsAllowed(context)) {
            String title = context.getString(R.string.notification_summary_title);
            String body;
            if (todaysSessions.isEmpty()) {
                body = context.getString(R.string.notification_summary_empty);
            } else {
                ClassOccurrence first = todaysSessions.get(0);
                body = context.getString(R.string.notification_summary_body,
                        todaysSessions.size(), formatTime(first.startMinute),
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

    private static PendingIntent reminderPendingIntent(Context context, String key,
                                                       long scheduleId, long exceptionId,
                                                       long epochDay, long classStartsAt, int flag) {
        Intent intent = new Intent(context, ScheduleNotificationReceiver.class)
                .setAction(ACTION_SCHEDULE_REMINDER)
                .setData(Uri.parse("schedule-reminder://occurrence/" + key))
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                .putExtra(EXTRA_EXCEPTION_ID, exceptionId)
                .putExtra(EXTRA_EPOCH_DAY, epochDay)
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

    private static void cancelReminderAlarm(Context context, String key) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = reminderPendingIntent(context, key, 0L, 0L, 0L, 0L,
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

    private static int notificationId(String key) {
        return key.hashCode() & 0x7fffffff;
    }

    private static String formatTime(int minuteOfDay) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d",
                minuteOfDay / 60, minuteOfDay % 60);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }
}