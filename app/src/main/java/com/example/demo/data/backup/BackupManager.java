package com.example.demo.data.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.notifications.NotificationScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Asynchronous SAF backup/restore API. The caller owns URI selection and permissions. */
public final class BackupManager {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "schedule-backup-io");
        thread.setDaemon(true);
        return thread;
    });

    private final Context appContext;
    private final AppDatabase database;
    private final BackupRoomStore roomStore;

    public BackupManager(Context context) {
        this(context, AppDatabase.getInstance(context));
    }

    public BackupManager(Context context, AppDatabase database) {
        if (context == null || database == null) {
            throw new IllegalArgumentException("Context and database are required.");
        }
        this.appContext = context.getApplicationContext();
        this.database = database;
        this.roomStore = new BackupRoomStore();
    }

    /** Creates a UTF-8 JSON backup at a SAF destination without doing I/O on the caller thread. */
    public Future<BackupResult> backupTo(Uri destinationUri) {
        validateContentUri(destinationUri);
        return IO_EXECUTOR.submit(() -> {
            BackupData snapshot = roomStore.createSnapshot(database);
            byte[] bytes = BackupCodec.encode(snapshot);
            ContentResolver resolver = appContext.getContentResolver();
            try (OutputStream output = resolver.openOutputStream(destinationUri, "wt")) {
                if (output == null) {
                    throw new IOException("Không mở được tệp đích để ghi bản sao lưu.");
                }
                output.write(bytes);
                output.flush();
            }
            return new BackupResult(bytes.length, snapshot);
        });
    }

    /** Validates the entire input before replacing Room data and syncing schedule reminders. */
    public Future<BackupResult> restoreFrom(Uri sourceUri) {
        validateContentUri(sourceUri);
        return IO_EXECUTOR.submit(() -> {
            InputStream input = appContext.getContentResolver().openInputStream(sourceUri);
            byte[] bytes = BackupCodec.readBounded(input);
            BackupData decoded = BackupCodec.decode(bytes);
            roomStore.restore(database, decoded);
            NotificationScheduler.syncAll(appContext);
            return new BackupResult(bytes.length, decoded);
        });
    }

    private static void validateContentUri(Uri uri) {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Hãy chọn tệp bằng trình chọn tài liệu của Android.");
        }
    }
}
