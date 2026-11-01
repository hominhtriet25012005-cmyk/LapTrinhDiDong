package com.example.demo.data.model;

import com.example.demo.data.local.projection.ScheduleDetails;

import java.time.LocalDate;

/** One effective class meeting on one calendar date. */
public final class ClassOccurrence {
    public enum Kind {
        RECURRING,
        MODIFIED,
        MAKEUP
    }

    /** Source metadata from the recurring Room row. */
    public final ScheduleDetails schedule;
    public final LocalDate date;
    public final LocalDate originalDate;
    public final int startMinute;
    public final int endMinute;
    public final String room;
    public final String note;
    public final Kind kind;
    public final long exceptionId;

    ClassOccurrence(ScheduleDetails schedule, LocalDate date, LocalDate originalDate,
                    int startMinute, int endMinute, String room, String note,
                    Kind kind, long exceptionId) {
        this.schedule = copy(schedule);
        this.date = date;
        this.originalDate = originalDate;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.room = room;
        this.note = note;
        this.kind = kind;
        this.exceptionId = exceptionId;
    }

    /** Returns a detached row-shaped value for existing UI and export adapters. */
    public ScheduleDetails toScheduleDetails() {
        ScheduleDetails result = copy(schedule);
        result.dayOfWeek = date.getDayOfWeek().getValue();
        result.startMinute = startMinute;
        result.endMinute = endMinute;
        result.room = room;
        result.note = note;
        result.validFromEpochDay = date.toEpochDay();
        result.validToEpochDay = date.toEpochDay();
        return result;
    }

    private static ScheduleDetails copy(ScheduleDetails source) {
        ScheduleDetails result = new ScheduleDetails();
        result.scheduleId = source.scheduleId;
        result.classSectionId = source.classSectionId;
        result.dayOfWeek = source.dayOfWeek;
        result.startMinute = source.startMinute;
        result.endMinute = source.endMinute;
        result.room = source.room;
        result.validFromEpochDay = source.validFromEpochDay;
        result.validToEpochDay = source.validToEpochDay;
        result.note = source.note;
        result.notificationEnabled = source.notificationEnabled;
        result.reminderMinutesBefore = source.reminderMinutesBefore;
        result.classCode = source.classCode;
        result.lecturer = source.lecturer;
        result.subjectId = source.subjectId;
        result.subjectCode = source.subjectCode;
        result.subjectName = source.subjectName;
        result.subjectColorKey = source.subjectColorKey;
        result.semesterId = source.semesterId;
        result.semesterName = source.semesterName;
        result.semesterAcademicYear = source.semesterAcademicYear;
        return result;
    }
}
