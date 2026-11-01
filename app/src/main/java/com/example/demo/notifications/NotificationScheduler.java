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
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ClassOccurrence;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
    public static final String EXTRA_OCCURRENCE_DATE_EPOCH_DAY = "occurrence_date_epoch_day";
    public static final String EXTRA_EXCEPTION_ID = "schedule_exception_id";

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
                    long occurrenceDate = intent.getLongExtra(
                            EXTRA_OCCURRENCE_DATE_EPOCH_DAY, Long.MIN_VALUE);
                    long exceptionId = intent.getLongExtra(EXTRA_EXCEPTION_ID, 0L);
                    showScheduleReminder(appContext, id, classStartsAt,
                            occurrenceDate, exceptionId);
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

        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = now.atZone(zone).toLocalDate();
        ScheduleSnapshot snapshot = database.runInTransaction(() -> {
            List<ScheduleDetails> schedules = database.scheduleDao().getAllDetails();
            List<ScheduleExceptionEntity> exceptions = database.scheduleExceptionDao().getAll();
            LocalDate lastRelevantDate = latestRelevantDate(schedules, exceptions, today);
            return ScheduleResolver.resolve(schedules, exceptions, today, lastRelevantDate);
        });
        Set<String> previouslyScheduled = new HashSet<>(context
                .getSharedPreferences(PREF_ALARMS, Context.MODE_PRIVATE)
                .getStringSet(KEY_SCHEDULE_IDS, new HashSet<>()));
        Set<String> scheduledNow = new HashSet<>();
        List<ScheduleReminderPlanner.Reminder> reminders =
                ScheduleReminderPlanner.planNextPerSchedule(snapshot, now, zone, remindersEnabled);
        for (ScheduleReminderPlanner.Reminder reminder : reminders) {
            scheduleReminder(context, reminder);
            scheduledNow.add(Long.toString(reminder.scheduleId));
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

    private static LocalDate latestRelevantDate(List<ScheduleDetails> schedules,
                                               List<ScheduleExceptionEntity> exceptions,
                                               LocalDate today) {
        LocalDate latest = today;
        for (ScheduleDetails schedule : schedules) {
            LocalDate end = safeDate(schedule.validToEpochDay);
            if (end != null && end.isAfter(latest)) {
                latest = end;
            }
        }
        for (ScheduleExceptionEntity exception : exceptions) {
            if (exception.replacementDateEpochDay == null) {
                continue;
            }
            LocalDate replacement = safeDate(exception.replacementDateEpochDay);
            if (replacement != null && replacement.isAfter(latest)) {
                latest = replacement;
            }
        }
        return latest;
    }

    private static LocalDate safeDate(long epochDay) {
        try {
            return LocalDate.ofEpochDay(epochDay);
        } catch (DateTimeException ignored) {
            return null;
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

    private static void scheduleReminder(Context context,
                                         ScheduleReminderPlanner.Reminder reminder) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) {
            return;
        }
        PendingIntent pendingIntent = reminderPendingIntent(context, reminder,
                PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            // Deliberately use inexact alarms to avoid restricted exact-alarm special access.
            // Android may defer delivery while power-saving restrictions are active.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    reminder.reminderAtMillis, pendingIntent);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Không thể lên lịch nhắc cho lịch " + reminder.scheduleId, exception);
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
                                             long classStartsAtMillis,
                                             long occurrenceDateEpochDay, long exceptionId) {
        AppDatabase database = AppDatabase.getInstance(context);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate occurrenceDate = safeDate(occurrenceDateEpochDay);
        ClassOccurrence currentOccurrence = occurrenceDate == null ? null
                : findOccurrence(database, occurrenceDate, scheduleId, exceptionId,
                        classStartsAtMillis, zone);
        long nowMillis = System.currentTimeMillis();
        if (currentOccurrence != null
                && nowMillis < classEndMillis(currentOccurrence, zone)
                && NotificationPreferences.remindersEnabled(context)
                && currentOccurrence.schedule.notificationEnabled
                && notificationsAllowed(context)) {
            String title = context.getString(R.string.notification_reminder_title,
                    safe(currentOccurrence.schedule.subjectName),
                    safe(currentOccurrence.schedule.classCode));
            String room = currentOccurrence.room == null || currentOccurrence.room.trim().isEmpty()
                    ? context.getString(R.string.notification_room_unknown)
                    : currentOccurrence.room.trim();
            String body = context.getString(R.string.notification_reminder_body,
                    formatTime(classStartsAtMillis, zone), room);
            postNotification(context, notificationId(scheduleId), title, body);
        }
        // Re-resolve after delivery so the next alarm reflects current Room rows and exceptions.
        syncAllOnWorker(context);
    }

    private static void showDailySummary(Context context) {
        if (!NotificationPreferences.dailySummaryEnabled(context)) {
            clearSummaryAlarmMetadata(context);
            syncAllOnWorker(context);
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        ScheduleSnapshot snapshot = loadSnapshotForRange(
                AppDatabase.getInstance(context), today, today);
        List<ClassOccurrence> todaysOccurrences = snapshot.on(today);

        if (notificationsAllowed(context)) {
            String title = context.getString(R.string.notification_summary_title);
            String body;
            if (todaysOccurrences.isEmpty()) {
                body = context.getString(R.string.notification_summary_empty);
            } else {
                body = context.getString(R.string.notification_summary_body,
                        todaysOccurrences.size(), DailySummaryFormatter.formatAgenda(
                                todaysOccurrences,
                                context.getString(R.string.notification_room_unknown)));
            }
            postNotification(context, SUMMARY_NOTIFICATION_ID, title, body);
        }
        // The just-fired daily alarm still has a PendingIntent token, so clear its metadata
        // before the regular sync decides whether to create tomorrow's summary alarm.
        clearSummaryAlarmMetadata(context);
        syncAllOnWorker(context);
    }

    private static ScheduleSnapshot loadSnapshotForRange(AppDatabase database,
                                                         LocalDate from, LocalDate to) {
        return database.runInTransaction(() -> ScheduleResolver.resolve(
                database.scheduleDao().getAllDetails(),
                database.scheduleExceptionDao().getAll(), from, to));
    }

    private static ClassOccurrence findOccurrence(AppDatabase database, LocalDate date,
                                                  long scheduleId, long exceptionId,
                                                  long classStartsAtMillis, ZoneId zone) {
        ScheduleSnapshot snapshot = loadSnapshotForRange(database, date, date);
        for (ClassOccurrence occurrence : snapshot.on(date)) {
            if (occurrence.schedule.scheduleId == scheduleId
                    && occurrence.exceptionId == exceptionId
                    && classStartMillis(occurrence, zone) == classStartsAtMillis) {
                return occurrence;
            }
        }
        return null;
    }

    private static long classStartMillis(ClassOccurrence occurrence, ZoneId zone) {
        return LocalDateTime.of(occurrence.date, LocalTime.of(
                        occurrence.startMinute / 60, occurrence.startMinute % 60))
                .atZone(zone).toInstant().toEpochMilli();
    }

    private static long classEndMillis(ClassOccurrence occurrence, ZoneId zone) {
        if (occurrence.endMinute == 1_440) {
            return occurrence.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        }
        return LocalDateTime.of(occurrence.date, LocalTime.of(
                        occurrence.endMinute / 60, occurrence.endMinute % 60))
                .atZone(zone).toInstant().toEpochMilli();
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

    private static PendingIntent reminderPendingIntent(
            Context context, ScheduleReminderPlanner.Reminder reminder, int flag) {
        return reminderPendingIntent(context, reminder.scheduleId,
                reminder.classStartsAtMillis, reminder.occurrenceDateEpochDay,
                reminder.exceptionId, flag);
    }

    private static PendingIntent reminderPendingIntent(Context context, long scheduleId,
                                                       long classStartsAt, long dateEpochDay,
                                                       long exceptionId, int flag) {
        Intent intent = new Intent(context, ScheduleNotificationReceiver.class)
                .setAction(ACTION_SCHEDULE_REMINDER)
                .setData(Uri.parse("schedule-reminder://schedule/" + scheduleId))
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                .putExtra(EXTRA_CLASS_STARTS_AT, classStartsAt)
                .putExtra(EXTRA_OCCURRENCE_DATE_EPOCH_DAY, dateEpochDay)
                .putExtra(EXTRA_EXCEPTION_ID, exceptionId);
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
                Long.MIN_VALUE, 0L, PendingIntent.FLAG_NO_CREATE);
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
