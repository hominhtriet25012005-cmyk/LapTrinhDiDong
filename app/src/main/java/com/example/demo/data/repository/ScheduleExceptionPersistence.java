package com.example.demo.data.repository;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.dao.ScheduleExceptionDao;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.validation.ScheduleExceptionValidator;

/** Saves schedule exceptions as SQLite transactions, replacing any older exception of the same occurrence. */
public final class ScheduleExceptionPersistence {
    private final AppDatabase database;

    public ScheduleExceptionPersistence(AppDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database must not be null.");
        }
        this.database = database;
    }

    public long save(ScheduleExceptionEntity exception) {
        if (exception == null) {
            throw new IllegalArgumentException("Exception must not be null.");
        }
        ScheduleExceptionValidator.validate(exception.type, exception.scheduleId,
                exception.classSectionId, exception.originalEpochDay, exception.newEpochDay,
                exception.newStartMinute, exception.newEndMinute, exception.newRoom);
        long[] savedId = new long[1];
        database.runInTransaction(() -> savedId[0] = saveInTransaction(exception));
        return savedId[0];
    }

    public int delete(long exceptionId) {
        if (exceptionId <= 0) {
            return 0;
        }
        int[] deleted = new int[1];
        database.runInTransaction(() ->
                deleted[0] = database.scheduleExceptionDao().deleteById(exceptionId));
        return deleted[0];
    }

    private long saveInTransaction(ScheduleExceptionEntity exception) {
        ScheduleExceptionDao dao = database.scheduleExceptionDao();
        if (ScheduleExceptionEntity.TYPE_MAKEUP.equals(exception.type)) {
            if (database.classSectionDao().getById(exception.classSectionId) == null) {
                throw new IllegalArgumentException("Không tìm thấy lớp học phần cho buổi học bù.");
            }
        } else {
            ScheduleEntity schedule = dao.findScheduleById(exception.scheduleId);
            if (schedule == null) {
                throw new IllegalArgumentException("Không tìm thấy lịch học gốc.");
            }
            ScheduleExceptionValidator.validateOccurrence(schedule.dayOfWeek,
                    schedule.validFromEpochDay, schedule.validToEpochDay,
                    exception.originalEpochDay);
            exception.classSectionId = schedule.classSectionId;
            ScheduleExceptionEntity existing = dao.findForOccurrence(
                    exception.scheduleId, exception.originalEpochDay);
            if (existing != null) {
                exception.id = existing.id;
                exception.createdAtMillis = existing.createdAtMillis;
            }
        }
        if (exception.createdAtMillis <= 0) {
            exception.createdAtMillis = System.currentTimeMillis();
        }
        if (exception.id > 0) {
            if (dao.update(exception) == 0) {
                throw new IllegalArgumentException("Không tìm thấy thay đổi cần cập nhật.");
            }
            return exception.id;
        }
        return dao.insert(exception);
    }
}