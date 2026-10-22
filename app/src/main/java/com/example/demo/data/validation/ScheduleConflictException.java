package com.example.demo.data.validation;

/** Raised when a recurring schedule overlaps another active schedule occurrence. */
public final class ScheduleConflictException extends RuntimeException {
    public ScheduleConflictException() {
        super("Lịch học bị trùng với một buổi học khác trong cùng khoảng thời gian.");
    }
}
