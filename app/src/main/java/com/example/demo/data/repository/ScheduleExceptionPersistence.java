package com.example.demo.data.repository;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.validation.ScheduleConflictException;
import com.example.demo.data.validation.ScheduleExceptionConflictValidator;
import com.example.demo.data.validation.ScheduleExceptionValidator;

import java.util.List;

/** Validates and stores one-off schedule changes atomically with conflict checks. */
public final class ScheduleExceptionPersistence {
    private final AppDatabase database;

    public ScheduleExceptionPersistence(AppDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database must not be null.");
        }
        this.database = database;
    }

    public long insert(ScheduleExceptionEntity exception) {
        if (exception == null) {
            throw new IllegalArgumentException("Thông tin ngoại lệ lịch học không hợp lệ.");
        }
        long[] savedId = new long[1];
        database.runInTransaction(() -> {
            ScheduleExceptionEntity normalized = validateForWrite(exception, 0L);
            normalized.id = 0L;
            savedId[0] = database.scheduleExceptionDao().insert(normalized);
        });
        return savedId[0];
    }

    public int update(ScheduleExceptionEntity exception) {
        if (exception == null || exception.id <= 0) {
            throw new IllegalArgumentException("Không tìm thấy ngoại lệ cần chỉnh sửa.");
        }
        int[] updatedRows = new int[1];
        database.runInTransaction(() -> {
            if (database.scheduleExceptionDao().getById(exception.id) == null) {
                throw new IllegalArgumentException("Không tìm thấy ngoại lệ cần chỉnh sửa.");
            }
            ScheduleExceptionEntity normalized = validateForWrite(exception, exception.id);
            updatedRows[0] = database.scheduleExceptionDao().update(normalized);
            if (updatedRows[0] == 0) {
                throw new IllegalArgumentException("Không tìm thấy ngoại lệ cần chỉnh sửa.");
            }
        });
        return updatedRows[0];
    }

    public int delete(ScheduleExceptionEntity exception) {
        if (exception == null || exception.id <= 0) {
            return 0;
        }
        int[] deletedRows = new int[1];
        database.runInTransaction(() -> deletedRows[0] =
                database.scheduleExceptionDao().delete(exception));
        return deletedRows[0];
    }

    private ScheduleExceptionEntity validateForWrite(ScheduleExceptionEntity input,
                                                       long excludedExceptionId) {
        ScheduleEntity schedule = database.scheduleDao().getById(input.scheduleId);
        if (schedule == null) {
            throw new IllegalArgumentException("Không tìm thấy lịch học gốc của ngoại lệ.");
        }
        ScheduleExceptionEntity normalized = ScheduleExceptionValidator
                .normalizeAndValidate(input, schedule);
        ScheduleExceptionEntity duplicate = database.scheduleExceptionDao()
                .findOtherForOriginalOccurrence(normalized.scheduleId,
                        normalized.originalDateEpochDay, excludedExceptionId);
        if (duplicate != null) {
            throw new IllegalArgumentException("Buổi học này đã có ngoại lệ.");
        }

        List<ScheduleEntity> schedules = database.scheduleDao().getAll();
        List<ScheduleExceptionEntity> exceptions = database.scheduleExceptionDao().getAll();
        if (ScheduleExceptionConflictValidator.hasConflict(normalized, schedules, exceptions)) {
            throw new ScheduleConflictException();
        }
        return normalized;
    }
}
