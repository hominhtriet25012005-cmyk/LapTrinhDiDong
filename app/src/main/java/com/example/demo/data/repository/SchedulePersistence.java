package com.example.demo.data.repository;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.validation.ScheduleConflictException;
import com.example.demo.data.validation.ScheduleConflictValidator;
import com.example.demo.data.validation.ScheduleValidator;

import java.util.List;

/** Performs class-section and schedule writes as SQLite transactions. */
public final class SchedulePersistence {
    private final AppDatabase database;

    public SchedulePersistence(AppDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database must not be null.");
        }
        this.database = database;
    }

    public long saveClassSection(ClassSectionEntity classSection) {
        validateClassSection(classSection);
        long[] savedId = new long[1];
        database.runInTransaction(() -> savedId[0] = saveClassSectionInTransaction(classSection));
        return savedId[0];
    }

    public int deleteClassSection(ClassSectionEntity classSection) {
        if (classSection == null || classSection.id <= 0) {
            return 0;
        }
        final int[] deletedRows = new int[1];
        database.runInTransaction(() -> deletedRows[0] = database.classSectionDao().delete(classSection));
        return deletedRows[0];
    }

    public long saveScheduleWithClassSection(ClassSectionEntity classSection, ScheduleEntity schedule) {
        validateClassSection(classSection);
        validateSchedule(schedule);
        long[] savedId = new long[1];
        database.runInTransaction(() -> {
            long classSectionId = saveClassSectionInTransaction(classSection);
            schedule.classSectionId = classSectionId;
            savedId[0] = saveScheduleInTransaction(schedule);
        });
        return savedId[0];
    }

    public long saveSchedule(ScheduleEntity schedule) {
        validateSchedule(schedule);
        long[] savedId = new long[1];
        database.runInTransaction(() -> {
            if (database.classSectionDao().getById(schedule.classSectionId) == null) {
                throw new IllegalArgumentException("Không tìm thấy lớp học phần của lịch.");
            }
            savedId[0] = saveScheduleInTransaction(schedule);
        });
        return savedId[0];
    }

    private long saveClassSectionInTransaction(ClassSectionEntity classSection) {
        ClassSectionEntity sameCode = database.classSectionDao().findBySubjectAndCode(
                classSection.subjectId, classSection.classCode);
        if (classSection.id > 0) {
            if (database.classSectionDao().getById(classSection.id) == null) {
                throw new IllegalArgumentException("Không tìm thấy lớp học phần cần chỉnh sửa.");
            }
            if (sameCode != null && sameCode.id != classSection.id) {
                throw new IllegalArgumentException("Mã lớp học phần đã tồn tại trong môn học này.");
            }
            if (database.classSectionDao().update(classSection) == 0) {
                throw new IllegalArgumentException("Không tìm thấy lớp học phần cần chỉnh sửa.");
            }
            return classSection.id;
        }

        if (sameCode != null) {
            classSection.id = sameCode.id;
            if (database.classSectionDao().update(classSection) == 0) {
                throw new IllegalArgumentException("Không thể cập nhật lớp học phần đã tồn tại.");
            }
            return sameCode.id;
        }
        return database.classSectionDao().insert(classSection);
    }

    private long saveScheduleInTransaction(ScheduleEntity schedule) {
        List<ScheduleEntity> sameWeekday = database.scheduleDao().getForDayOfWeek(schedule.dayOfWeek);
        if (ScheduleConflictValidator.hasConflict(schedule, sameWeekday)) {
            throw new ScheduleConflictException();
        }
        if (schedule.id > 0) {
            if (database.scheduleDao().update(schedule) == 0) {
                throw new IllegalArgumentException("Không tìm thấy lịch học cần chỉnh sửa.");
            }
            return schedule.id;
        }
        return database.scheduleDao().insert(schedule);
    }

    private static void validateClassSection(ClassSectionEntity classSection) {
        if (classSection == null) {
            throw new IllegalArgumentException("Class section must not be null.");
        }
        if (classSection.subjectId <= 0 || classSection.classCode == null
                || classSection.classCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn môn học và nhập mã lớp học phần.");
        }
    }

    private static void validateSchedule(ScheduleEntity schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule must not be null.");
        }
        ScheduleValidator.validateSchedule(schedule.dayOfWeek, schedule.startMinute,
                schedule.endMinute, schedule.validFromEpochDay, schedule.validToEpochDay);
    }
}
