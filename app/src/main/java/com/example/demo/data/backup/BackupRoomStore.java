package com.example.demo.data.backup;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.SemesterEntity;

/** Reads a consistent Room snapshot and replaces all learning tables in one transaction. */
public final class BackupRoomStore {
    public BackupData createSnapshot(AppDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database must not be null.");
        }
        return database.runInTransaction(() -> {
            BackupData snapshot = BackupData.create(System.currentTimeMillis(),
                    database.semesterDao().getAll(),
                    database.subjectDao().getAll(),
                    database.classSectionDao().getAll(),
                    database.scheduleDao().getAll(),
                    database.scheduleExceptionDao().getAll());
            BackupValidator.validate(snapshot);
            return snapshot;
        });
    }

    /** Validates a detached copy before deleting anything, then restores atomically. */
    public void restore(AppDatabase database, BackupData input) {
        if (database == null) {
            throw new IllegalArgumentException("Database must not be null.");
        }
        BackupData data = BackupData.copyOf(input);
        BackupValidator.validate(data);

        database.runInTransaction(() -> {
            database.scheduleExceptionDao().deleteAll();
            database.scheduleDao().deleteAll();
            database.classSectionDao().deleteAll();
            database.subjectDao().deleteAll();
            database.semesterDao().deleteAll();

            for (SemesterEntity semester : data.semesters) {
                database.semesterDao().insert(semester);
            }
            if (!data.subjects.isEmpty()) {
                database.subjectDao().insertAll(data.subjects);
            }
            if (!data.classSections.isEmpty()) {
                database.classSectionDao().insertAll(data.classSections);
            }
            if (!data.schedules.isEmpty()) {
                database.scheduleDao().insertAll(data.schedules);
            }
            if (!data.scheduleExceptions.isEmpty()) {
                database.scheduleExceptionDao().insertAll(data.scheduleExceptions);
            }
        });
    }
}
