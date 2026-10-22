package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SemesterEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** Converts the semester form's user-facing dates into values stored by Room. */
public final class SemesterFormMapper {
    private static final DateTimeFormatter FORM_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private SemesterFormMapper() {
    }

    public static SemesterEntity toEntity(Long id, String name, String startDateText,
                                          String endDateText, String note, boolean isCurrent) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Tên học kỳ là bắt buộc.");
        }

        LocalDate startDate = parseOptionalDate(startDateText, "Ngày bắt đầu");
        LocalDate endDate = parseOptionalDate(endDateText, "Ngày kết thúc");
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc không được trước ngày bắt đầu.");
        }

        SemesterEntity semester = new SemesterEntity();
        if (id != null) {
            semester.id = id;
        }
        semester.name = normalizedName;
        semester.academicYear = startDate == null
                ? null
                : String.format(Locale.ROOT, "%d–%d", startDate.getYear(), startDate.getYear() + 1);
        semester.startDateEpochDay = startDate == null ? null : startDate.toEpochDay();
        semester.endDateEpochDay = endDate == null ? null : endDate.toEpochDay();
        semester.note = note == null ? "" : note.trim();
        semester.isCurrent = isCurrent;
        return semester;
    }

    private static LocalDate parseOptionalDate(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        try {
            return LocalDate.parse(normalized, FORM_DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " không hợp lệ: " + normalized + ".");
        }
    }
}
