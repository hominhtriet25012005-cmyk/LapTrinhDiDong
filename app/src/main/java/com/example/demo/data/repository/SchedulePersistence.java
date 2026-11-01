package com.example.demo.data.repository;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.validation.ScheduleConflictException;
import com.example.demo.data.validation.ScheduleConflictValidator;
import com.example.demo.data.validation.ScheduleExceptionConflictValidator;
import com.example.demo.data.validation.ScheduleExceptionValidator;
import com.example.demo.data.validation.ScheduleValidator;

import java.util.List;
import java.util.Locale;

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
        ClassSectionEntity normalized = normalizeClassSection(classSection);
        long[] savedId = new long[1];
        database.runInTransaction(() ->
                savedId[0] = saveClassSectionInTransaction(normalized));
        return savedId[0];
    }

    public int deleteClassSection(ClassSectionEntity classSection) {
        if (classSection == null || classSection.id <= 0) {
            return 0;
        }
        final int[] deletedRows = new int[1];
        database.runInTransaction(() ->
                deletedRows[0] = database.classSectionDao().delete(classSection));
        return deletedRows[0];
    }

    public long saveScheduleWithClassSection(ClassSectionEntity classSection, ScheduleEntity schedule) {
        ClassSectionEntity normalized = normalizeClassSection(classSection);
        validateSchedule(schedule);
        long[] savedId = new long[1];
        database.runInTransaction(() -> {
            long classSectionId = saveClassSectionInTransaction(normalized);
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
        if (schedule.id > 0) {
            validateExistingExceptionsBeforeScheduleUpdate(schedule);
        } else {
            validateNewScheduleAgainstExceptions(schedule);
        }
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

    private void validateExistingExceptionsBeforeScheduleUpdate(ScheduleEntity updatedSchedule) {
        List<ScheduleExceptionEntity> scheduleExceptions = database.scheduleExceptionDao()
                .getForSchedule(updatedSchedule.id);
        for (ScheduleExceptionEntity exception : scheduleExceptions) {
            ScheduleExceptionValidator.normalizeAndValidate(exception, updatedSchedule);
        }

        List<ScheduleEntity> schedules = database.scheduleDao().getAll();
        for (int index = 0; index < schedules.size(); index++) {
            if (schedules.get(index).id == updatedSchedule.id) {
                schedules.set(index, updatedSchedule);
                break;
            }
        }
        List<ScheduleExceptionEntity> allExceptions = database.scheduleExceptionDao().getAll();
        if (ScheduleExceptionConflictValidator.hasConflictWithRecurringSchedule(
                updatedSchedule, schedules, allExceptions)) {
            throw new ScheduleConflictException();
        }
        for (ScheduleExceptionEntity exception : scheduleExceptions) {
            if (ScheduleExceptionConflictValidator.hasConflict(
                    exception, schedules, allExceptions)) {
                throw new ScheduleConflictException();
            }
        }
    }

    private void validateNewScheduleAgainstExceptions(ScheduleEntity newSchedule) {
        List<ScheduleEntity> schedules = database.scheduleDao().getAll();
        List<ScheduleExceptionEntity> allExceptions = database.scheduleExceptionDao().getAll();
        if (ScheduleExceptionConflictValidator.hasConflictWithRecurringSchedule(
                newSchedule, schedules, allExceptions)) {
            throw new ScheduleConflictException();
        }
    }

    private static ClassSectionEntity normalizeClassSection(ClassSectionEntity classSection) {
        if (classSection == null) {
            throw new IllegalArgumentException("Class section must not be null.");
        }
        String classCode = trim(classSection.classCode).toUpperCase(Locale.ROOT);
        if (classSection.subjectId <= 0 || classCode.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn môn học và nhập mã lớp học phần.");
        }
        ClassSectionEntity normalized = new ClassSectionEntity();
        normalized.id = classSection.id;
        normalized.subjectId = classSection.subjectId;
        normalized.classCode = classCode;
        normalized.lecturer = trim(classSection.lecturer);
        normalized.note = trim(classSection.note);
        return normalized;
    }

    private static void validateSchedule(ScheduleEntity schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule must not be null.");
        }
        ScheduleValidator.validateSchedule(schedule.dayOfWeek, schedule.startMinute,
                schedule.endMinute, schedule.validFromEpochDay, schedule.validToEpochDay);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
