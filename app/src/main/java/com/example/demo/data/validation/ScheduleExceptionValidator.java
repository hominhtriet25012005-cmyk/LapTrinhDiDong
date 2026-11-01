package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;

/** Validates and normalizes one-off changes against their source recurring schedule. */
public final class ScheduleExceptionValidator {
    private ScheduleExceptionValidator() {
    }

    public static ScheduleExceptionEntity normalizeAndValidate(ScheduleExceptionEntity input,
                                                               ScheduleEntity schedule) {
        if (input == null || schedule == null) {
            throw new IllegalArgumentException("Thông tin ngoại lệ lịch học không hợp lệ.");
        }
        if (input.id < 0 || schedule.id <= 0 || input.scheduleId != schedule.id) {
            throw new IllegalArgumentException("Không tìm thấy lịch học gốc của ngoại lệ.");
        }

        ScheduleValidator.validateSchedule(schedule.dayOfWeek, schedule.startMinute,
                schedule.endMinute, schedule.validFromEpochDay, schedule.validToEpochDay);
        LocalDate originalDate = toDate(input.originalDateEpochDay, "Ngày học gốc không hợp lệ.");
        if (input.originalDateEpochDay < schedule.validFromEpochDay
                || input.originalDateEpochDay > schedule.validToEpochDay
                || originalDate.getDayOfWeek().getValue() != schedule.dayOfWeek) {
            throw new IllegalArgumentException(
                    "Ngày gốc phải đúng thứ và nằm trong thời gian áp dụng của lịch học.");
        }

        String type = input.exceptionType == null
                ? "" : input.exceptionType.trim().toUpperCase(Locale.ROOT);
        if (!ScheduleExceptionEntity.TYPE_CANCEL.equals(type)
                && !ScheduleExceptionEntity.TYPE_MODIFY.equals(type)
                && !ScheduleExceptionEntity.TYPE_MAKEUP.equals(type)) {
            throw new IllegalArgumentException("Loại ngoại lệ lịch học không được hỗ trợ.");
        }

        if (input.replacementDateEpochDay != null) {
            toDate(input.replacementDateEpochDay, "Ngày học thay thế không hợp lệ.");
        }
        validateOverrideTime(input.startMinute, input.endMinute);
        String room = input.room == null ? null : input.room.trim();

        if (ScheduleExceptionEntity.TYPE_CANCEL.equals(type)) {
            if (input.replacementDateEpochDay != null || input.startMinute != null
                    || input.endMinute != null || room != null) {
                throw new IllegalArgumentException("Buổi bị hủy không được có thông tin thay thế.");
            }
        } else if (ScheduleExceptionEntity.TYPE_MAKEUP.equals(type)) {
            if (input.replacementDateEpochDay == null
                    || input.replacementDateEpochDay == input.originalDateEpochDay) {
                throw new IllegalArgumentException(
                        "Buổi học bù phải có ngày khác ngày học gốc.");
            }
        }

        ScheduleExceptionEntity normalized = new ScheduleExceptionEntity();
        normalized.id = input.id;
        normalized.scheduleId = input.scheduleId;
        normalized.originalDateEpochDay = input.originalDateEpochDay;
        normalized.exceptionType = type;
        normalized.replacementDateEpochDay = input.replacementDateEpochDay;
        normalized.startMinute = input.startMinute;
        normalized.endMinute = input.endMinute;
        normalized.room = room;
        normalized.note = input.note == null ? "" : input.note.trim();
        return normalized;
    }

    private static void validateOverrideTime(Integer startMinute, Integer endMinute) {
        if ((startMinute == null) != (endMinute == null)) {
            throw new IllegalArgumentException("Giờ bắt đầu và kết thúc phải được nhập cùng nhau.");
        }
        if (startMinute != null && (startMinute < 0 || startMinute >= 1_440
                || endMinute <= startMinute || endMinute > 1_440)) {
            throw new IllegalArgumentException("Khoảng giờ thay thế không hợp lệ.");
        }
    }

    private static LocalDate toDate(long epochDay, String message) {
        try {
            return LocalDate.ofEpochDay(epochDay);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }
}
