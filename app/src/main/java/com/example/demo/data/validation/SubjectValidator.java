package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SubjectEntity;

import java.util.Locale;

/** Normalizes and validates subject entities at every application write boundary. */
public final class SubjectValidator {
    private SubjectValidator() {
    }

    public static SubjectEntity normalizeAndValidate(SubjectEntity input) {
        if (input == null) {
            throw new IllegalArgumentException("Thông tin môn học không hợp lệ.");
        }
        if (input.id < 0) {
            throw new IllegalArgumentException("Mã môn học không hợp lệ.");
        }
        if (input.semesterId <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn học kỳ.");
        }

        String name = trim(input.name);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Tên môn học là bắt buộc.");
        }

        String code = trim(input.code).toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Mã môn học là bắt buộc.");
        }
        if (input.credits < 0) {
            throw new IllegalArgumentException("Số tín chỉ phải là số nguyên không âm.");
        }

        SubjectEntity normalized = new SubjectEntity();
        normalized.id = input.id;
        normalized.semesterId = input.semesterId;
        normalized.code = code;
        normalized.name = name;
        normalized.credits = input.credits;
        normalized.defaultLecturer = trim(input.defaultLecturer);
        normalized.colorKey = trim(input.colorKey);
        if (normalized.colorKey.isEmpty()) {
            normalized.colorKey = "blue";
        }
        normalized.note = trim(input.note);
        return normalized;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}