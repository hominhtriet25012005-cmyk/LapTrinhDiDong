package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SubjectEntity;

import java.util.Locale;

/** Converts validated subject form values into the Room entity used by the app. */
public final class SubjectFormMapper {
    private SubjectFormMapper() {
    }

    public static SubjectEntity toEntity(Long id, long semesterId, String code, String name,
                                         String creditsText, String lecturer, String colorKey,
                                         String note) {
        String normalizedName = trim(name);
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Tên môn học là bắt buộc.");
        }

        String normalizedCode = trim(code).toUpperCase(Locale.ROOT);
        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("Mã môn học là bắt buộc.");
        }
        if (semesterId <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn học kỳ.");
        }

        int credits = parseCredits(creditsText);
        String normalizedColor = trim(colorKey);
        if (normalizedColor.isEmpty()) {
            normalizedColor = "blue";
        }

        SubjectEntity subject = new SubjectEntity();
        if (id != null) {
            subject.id = id;
        }
        subject.semesterId = semesterId;
        subject.code = normalizedCode;
        subject.name = normalizedName;
        subject.credits = credits;
        subject.defaultLecturer = trim(lecturer);
        subject.colorKey = normalizedColor;
        subject.note = trim(note);
        return subject;
    }

    private static int parseCredits(String value) {
        String normalized = trim(value);
        if (normalized.isEmpty()) {
            return 0;
        }
        try {
            int credits = Integer.parseInt(normalized);
            if (credits >= 0) {
                return credits;
            }
        } catch (NumberFormatException ignored) {
            // Report a consistent form-level validation message below.
        }
        throw new IllegalArgumentException("Số tín chỉ phải là số nguyên không âm.");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
