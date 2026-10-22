package com.example.demo.data.validation;

/** Validation shared by schedule forms and repository writes. */
public final class ScheduleValidator {
    private ScheduleValidator() {
    }

    public static void validateSemesterDates(Long startDateEpochDay, Long endDateEpochDay) {
        if (startDateEpochDay != null && endDateEpochDay != null
                && endDateEpochDay < startDateEpochDay) {
            throw new IllegalArgumentException("Semester end date must not be before its start date.");
        }
    }

    public static void validateSchedule(int dayOfWeek, int startMinute, int endMinute,
                                        long validFromEpochDay, long validToEpochDay) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Day of week must be between 1 (Monday) and 7 (Sunday).");
        }
        if (startMinute < 0 || startMinute >= 1_440) {
            throw new IllegalArgumentException("Start time must be between 00:00 and 23:59.");
        }
        if (endMinute <= startMinute || endMinute > 1_440) {
            throw new IllegalArgumentException("End time must be after start time and no later than midnight.");
        }
        if (validToEpochDay < validFromEpochDay) {
            throw new IllegalArgumentException("Schedule end date must not be before its start date.");
        }
    }
}
