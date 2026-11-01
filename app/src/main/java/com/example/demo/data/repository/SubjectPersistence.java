package com.example.demo.data.repository;

import android.database.sqlite.SQLiteConstraintException;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.validation.SubjectValidator;

/** Validates and writes subjects transactionally, including case-insensitive code uniqueness. */
public final class SubjectPersistence {
    private final AppDatabase database;

    public SubjectPersistence(AppDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database must not be null.");
        }
        this.database = database;
    }

    public long insert(SubjectEntity subject) {
        SubjectEntity normalized = SubjectValidator.normalizeAndValidate(subject);
        normalized.id = 0L;
        long[] savedId = new long[1];
        database.runInTransaction(() -> {
            validateSemester(normalized.semesterId);
            rejectDuplicateCode(normalized, 0L);
            savedId[0] = database.subjectDao().insert(normalized);
        });
        return savedId[0];
    }

    public int update(SubjectEntity subject) {
        SubjectEntity normalized = SubjectValidator.normalizeAndValidate(subject);
        if (normalized.id <= 0) {
            throw new IllegalArgumentException("Không tìm thấy môn học cần chỉnh sửa.");
        }
        int[] updatedRows = new int[1];
        database.runInTransaction(() -> {
            validateSemester(normalized.semesterId);
            rejectDuplicateCode(normalized, normalized.id);
            updatedRows[0] = database.subjectDao().update(normalized);
        });
        return updatedRows[0];
    }

    private void validateSemester(long semesterId) {
        if (database.semesterDao().getById(semesterId) == null) {
            throw new IllegalArgumentException("Vui lòng chọn học kỳ.");
        }
    }

    private void rejectDuplicateCode(SubjectEntity subject, long excludedId) {
        if (database.subjectDao().hasDuplicateCode(
                subject.semesterId, subject.code, excludedId)) {
            // Keep the existing form's unique-constraint handling behavior.
            throw new SQLiteConstraintException(
                    "UNIQUE constraint failed: subjects.semesterId, subjects.code");
        }
    }
}