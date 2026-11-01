package com.example.demo.data.validation;

/** Validates schedule exception fields (cancel, modify, makeup) without touching the database. */
public final class ScheduleExceptionValidator {
    public static final String TYPE_CANCEL = "CANCEL";
    public static final String TYPE_MODIFY = "MODIFY";
    public static final String TYPE_MAKEUP = "MAKEUP";

    private ScheduleExceptionValidator() {
    }

    public static void validate(String type, Long scheduleId, long classSectionId,
                                Long originalEpochDay, Long newEpochDay,
                                Integer newStartMinute, Integer newEndMinute, String newRoom) {
        if (type == null) {
            throw new IllegalArgumentException("Vui lòng chọn loại thay đổi.");
        }
        if (classSectionId <= 0) {
            throw new IllegalArgumentException("Thiếu lớp học phần cho thay đổi lịch.");
        }
        switch (type) {
            case TYPE_CANCEL:
                requireOccurrence(scheduleId, originalEpochDay);
                if (newEpochDay != null || newStartMinute != null || newEndMinute != null
                        || !isBlank(newRoom)) {
                    throw new IllegalArgumentException("Buổi bị hủy không có ngày, giờ hoặc phòng mới.");
                }
                break;
            case TYPE_MODIFY:
                requireOccurrence(scheduleId, originalEpochDay);
                boolean hasTime = newStartMinute != null || newEndMinute != null;
                if (newEpochDay == null && !hasTime && isBlank(newRoom)) {
                    throw new IllegalArgumentException("Vui lòng nhập ngày, giờ hoặc phòng mới.");
                }
                if (hasTime) {
                    requireTimeRange(newStartMinute, newEndMinute);
                }
                break;
            case TYPE_MAKEUP:
                if (scheduleId != null || originalEpochDay != null) {
                    throw new IllegalArgumentException("Buổi học bù không gắn với một buổi gốc.");
                }
                if (newEpochDay == null) {
                    throw new IllegalArgumentException("Vui lòng chọn ngày học bù.");
                }
                requireTimeRange(newStartMinute, newEndMinute);
                break;
            default:
                throw new IllegalArgumentException("Loại thay đổi không hợp lệ: " + type);
        }
    }

    /** The original date must be a real occurrence: right weekday and inside the schedule range. */
    public static void validateOccurrence(int dayOfWeek, long validFromEpochDay,
                                          long validToEpochDay, long originalEpochDay) {
        if (originalEpochDay < validFromEpochDay || originalEpochDay > validToEpochDay) {
            throw new IllegalArgumentException("Ngày này nằm ngoài khoảng áp dụng của lịch học.");
        }
        // Epoch day 0 (1970-01-01) was a Thursday; ISO Monday = 1 ... Sunday = 7.
        int isoDay = (int) Math.floorMod(originalEpochDay + 3L, 7L) + 1;
        if (isoDay != dayOfWeek) {
            throw new IllegalArgumentException("Ngày này không trùng với thứ của lịch học.");
        }
    }

    private static void requireOccurrence(Long scheduleId, Long originalEpochDay) {
        if (scheduleId == null || scheduleId <= 0) {
            throw new IllegalArgumentException("Thiếu lịch học gốc.");
        }
        if (originalEpochDay == null) {
            throw new IllegalArgumentException("Thiếu ngày của buổi học gốc.");
        }
    }

    private static void requireTimeRange(Integer start, Integer end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Vui lòng nhập đủ giờ bắt đầu và giờ kết thúc.");
        }
        if (start < 0 || start >= 1_440) {
            throw new IllegalArgumentException("Giờ bắt đầu phải từ 00:00 đến 23:59.");
        }
        if (end <= start || end > 1_440) {
            throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}