package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** Maps the schedule form's localized values into normalized Room entities. */
public final class ScheduleFormMapper {
    private static final DateTimeFormatter FORM_TIME_FORMAT = DateTimeFormatter
            .ofPattern("hh:mm a", Locale.US)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FORM_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private ScheduleFormMapper() {
    }

    public static ClassSectionEntity toClassSection(Long id, long subjectId, String classCode,
                                                     String lecturer, String note) {
        String normalizedCode = trim(classCode);
        if (subjectId <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn môn học.");
        }
        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("Mã lớp học phần là bắt buộc.");
        }
        ClassSectionEntity classSection = new ClassSectionEntity();
        if (id != null) {
            classSection.id = id;
        }
        classSection.subjectId = subjectId;
        classSection.classCode = normalizedCode;
        classSection.lecturer = trim(lecturer);
        classSection.note = trim(note);
        return classSection;
    }

    public static ScheduleEntity toSchedule(Long id, long classSectionId, int dayOfWeek,
                                             String startTimeText, String endTimeText,
                                             String room, String fromDateText, String toDateText,
                                             String note) {
        LocalTime startTime = parseTime(startTimeText, "Giờ bắt đầu");
        LocalTime endTime = parseTime(endTimeText, "Giờ kết thúc");
        LocalDate fromDate = parseDate(fromDateText, "Ngày bắt đầu");
        LocalDate toDate = parseDate(toDateText, "Ngày kết thúc");
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("Ngày kết thúc không được trước ngày bắt đầu.");
        }

        ScheduleEntity schedule = new ScheduleEntity(
                classSectionId,
                dayOfWeek,
                startTime.getHour() * 60 + startTime.getMinute(),
                endTime.getHour() * 60 + endTime.getMinute(),
                trim(room),
                fromDate.toEpochDay(),
                toDate.toEpochDay(),
                trim(note)
        );
        if (id != null) {
            schedule.id = id;
        }
        ScheduleValidator.validateSchedule(dayOfWeek, schedule.startMinute, schedule.endMinute,
                schedule.validFromEpochDay, schedule.validToEpochDay);
        return schedule;
    }

    private static LocalTime parseTime(String value, String fieldName) {
        String normalized = trim(value);
        try {
            return LocalTime.parse(normalized, FORM_TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " không hợp lệ.");
        }
    }

    private static LocalDate parseDate(String value, String fieldName) {
        String normalized = trim(value);
        try {
            return LocalDate.parse(normalized, FORM_DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " không hợp lệ.");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
