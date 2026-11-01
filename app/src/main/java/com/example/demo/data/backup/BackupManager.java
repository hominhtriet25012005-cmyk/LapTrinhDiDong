package com.example.demo.data.backup;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.notifications.NotificationScheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Reads and writes backup files and replaces the database content on restore. Blocking: call off the main thread. */
public final class BackupManager {
    public static final int MAX_BACKUP_BYTES = 20 * 1024 * 1024;
    private static final String PREFERENCES = "backup_preferences";
    private static final String KEY_LAST_BACKUP = "last_backup_millis";

    private BackupManager() {
    }

    public static long lastBackupMillis(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_BACKUP, 0L);
    }

    /** Writes a consistent snapshot of all data to the chosen document. */
    public static void writeBackup(Context context, Uri target) throws IOException {
        AppDatabase database = AppDatabase.getInstance(context);
        BackupData[] snapshot = new BackupData[1];
        database.runInTransaction(() -> snapshot[0] = new BackupData(
                System.currentTimeMillis(),
                database.semesterDao().getAll(),
                database.subjectDao().getAll(),
                database.classSectionDao().getAll(),
                database.scheduleDao().getAll(),
                database.scheduleExceptionDao().getAll()));
        byte[] bytes = BackupCodec.toJson(snapshot[0]).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = context.getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) {
                throw new IOException("Cannot open output stream.");
            }
            out.write(bytes);
            out.flush();
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_BACKUP, snapshot[0].createdAtMillis).apply();
    }

    /** Reads and validates a backup file. Nothing is changed in the database. */
    public static BackupData readBackup(Context context, Uri source)
            throws IOException, BackupFormatException {
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) {
                throw new IOException("Cannot open input stream.");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_BACKUP_BYTES) {
                    throw new BackupFormatException("File quá lớn để là bản sao lưu UniSchedule.");
                }
            }
            return BackupCodec.fromJson(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Replaces all data with the backup in one transaction: if anything fails the current data
     * is left untouched.
     */
    public static void restore(Context context, BackupData data) {
        Context appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(appContext);
        database.runInTransaction(() -> {
            SupportSQLiteDatabase sql = database.getOpenHelper().getWritableDatabase();
            sql.execSQL("DELETE FROM schedule_exceptions");
            sql.execSQL("DELETE FROM schedules");
            sql.execSQL("DELETE FROM class_sections");
            sql.execSQL("DELETE FROM subjects");
            sql.execSQL("DELETE FROM semesters");

            for (SemesterEntity e : data.semesters) {
                ContentValues v = new ContentValues();
                v.put("id", e.id);
                v.put("name", e.name);
                putString(v, "academicYear", e.academicYear);
                putLong(v, "startDateEpochDay", e.startDateEpochDay);
                putLong(v, "endDateEpochDay", e.endDateEpochDay);
                v.put("isCurrent", e.isCurrent ? 1 : 0);
                putString(v, "note", e.note);
                insert(sql, "semesters", v);
            }
            for (SubjectEntity e : data.subjects) {
                ContentValues v = new ContentValues();
                v.put("id", e.id);
                v.put("semesterId", e.semesterId);
                v.put("code", e.code);
                v.put("name", e.name);
                v.put("credits", e.credits);
                putString(v, "defaultLecturer", e.defaultLecturer);
                putString(v, "colorKey", e.colorKey);
                putString(v, "note", e.note);
                insert(sql, "subjects", v);
            }
            for (ClassSectionEntity e : data.classSections) {
                ContentValues v = new ContentValues();
                v.put("id", e.id);
                v.put("subjectId", e.subjectId);
                putString(v, "classCode", e.classCode);
                putString(v, "lecturer", e.lecturer);
                putString(v, "note", e.note);
                insert(sql, "class_sections", v);
            }
            for (ScheduleEntity e : data.schedules) {
                ContentValues v = new ContentValues();
                v.put("id", e.id);
                v.put("classSectionId", e.classSectionId);
                v.put("dayOfWeek", e.dayOfWeek);
                v.put("startMinute", e.startMinute);
                v.put("endMinute", e.endMinute);
                putString(v, "room", e.room);
                v.put("validFromEpochDay", e.validFromEpochDay);
                v.put("validToEpochDay", e.validToEpochDay);
                putString(v, "note", e.note);
                v.put("notificationEnabled", e.notificationEnabled ? 1 : 0);
                v.put("reminderMinutesBefore", e.reminderMinutesBefore);
                insert(sql, "schedules", v);
            }
            for (ScheduleExceptionEntity e : data.exceptions) {
                ContentValues v = new ContentValues();
                v.put("id", e.id);
                v.put("type", e.type);
                putLong(v, "scheduleId", e.scheduleId);
                v.put("classSectionId", e.classSectionId);
                putLong(v, "originalEpochDay", e.originalEpochDay);
                putLong(v, "newEpochDay", e.newEpochDay);
                if (e.newStartMinute == null) {
                    v.putNull("newStartMinute");
                } else {
                    v.put("newStartMinute", e.newStartMinute);
                }
                if (e.newEndMinute == null) {
                    v.putNull("newEndMinute");
                } else {
                    v.put("newEndMinute", e.newEndMinute);
                }
                putString(v, "newRoom", e.newRoom);
                putString(v, "note", e.note);
                v.put("createdAtMillis", e.createdAtMillis);
                insert(sql, "schedule_exceptions", v);
            }
        });
        // Old alarms belong to the replaced data; rebuild them from the restored schedule.
        NotificationScheduler.syncAll(appContext);
    }

    private static void insert(SupportSQLiteDatabase sql, String table, ContentValues values) {
        long rowId = sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values);
        if (rowId == -1) {
            throw new IllegalStateException("Cannot insert into " + table);
        }
    }

    private static void putString(ContentValues values, String key, String value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    private static void putLong(ContentValues values, String key, Long value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }
}