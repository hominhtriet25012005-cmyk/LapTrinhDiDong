package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SubjectEntity;

/** Converts validated subject form values into the Room entity used by the app. */
public final class SubjectFormMapper {
    private SubjectFormMapper() {
    }

    public static SubjectEntity toEntity(Long id, long semesterId, String code, String name,
                                         String creditsText, String lecturer, String colorKey,
                                         String note) {
        SubjectEntity subject = new SubjectEntity();
        if (id != null) {
            subject.id = id;
        }
        subject.semesterId = semesterId;
        subject.code = code;
        subject.name = name;
        subject.credits = parseCredits(creditsText);
        subject.defaultLecturer = lecturer;
        subject.colorKey = colorKey;
        subject.note = note;
        return SubjectValidator.normalizeAndValidate(subject);
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
