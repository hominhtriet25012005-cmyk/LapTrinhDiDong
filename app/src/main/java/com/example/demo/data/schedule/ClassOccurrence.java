package com.example.demo.data.schedule;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;

/** One concrete class session on one date, after applying cancel, modify and makeup exceptions. */
public final class ClassOccurrence {
    public static final int STATUS_REGULAR = 0;
    public static final int STATUS_MODIFIED = 1;
    public static final int STATUS_MAKEUP = 2;
    public static final int STATUS_CANCELLED = 3;

    public final int status;
    /** The date this session actually takes place (or would have, when cancelled). */
    public final long epochDay;
    /** For a modified session moved to another date: the date of the original session. */
    public final Long originalEpochDay;
    public final int startMinute;
    public final int endMinute;
    public final String room;
    /** Note of the weekly schedule. */
    public final String note;
    /** Reason entered for the cancel, change or makeup. */
    public final String changeNote;
    /** 0 for makeup sessions. */
    public final long scheduleId;
    /** 0 for regular sessions. */
    public final long exceptionId;
    public final long classSectionId;
    public final String classCode;
    public final String lecturer;
    public final long subjectId;
    public final String subjectCode;
    public final String subjectName;
    public final String subjectColorKey;
    public final long semesterId;
    public final String semesterName;
    public final String semesterAcademicYear;
    public final boolean notificationEnabled;
    public final int reminderMinutesBefore;

    private ClassOccurrence(int status, long epochDay, Long originalEpochDay,
                            int startMinute, int endMinute, String room, String note,
                            String changeNote, long exceptionId,
                            ScheduleDetails schedule, ClassSectionDetails section) {
        this.status = status;
        this.epochDay = epochDay;
        this.originalEpochDay = originalEpochDay;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.room = room;
        this.note = note;
        this.changeNote = changeNote;
        this.exceptionId = exceptionId;
        if (schedule != null) {
            scheduleId = schedule.scheduleId;
            classSectionId = schedule.classSectionId;
            classCode = schedule.classCode;
            lecturer = schedule.lecturer;
            subjectId = schedule.subjectId;
            subjectCode = schedule.subjectCode;
            subjectName = schedule.subjectName;
            subjectColorKey = schedule.subjectColorKey;
            semesterId = schedule.semesterId;
            semesterName = schedule.semesterName;
            semesterAcademicYear = schedule.semesterAcademicYear;
            notificationEnabled = schedule.notificationEnabled;
            reminderMinutesBefore = schedule.reminderMinutesBefore;
        } else {
            scheduleId = 0L;
            classSectionId = section.classSectionId;
            classCode = section.classCode;
            lecturer = section.lecturer;
            subjectId = section.subjectId;
            subjectCode = section.subjectCode;
            subjectName = section.subjectName;
            subjectColorKey = section.subjectColorKey;
            semesterId = section.semesterId;
            semesterName = section.semesterName;
            semesterAcademicYear = section.semesterAcademicYear;
            notificationEnabled = false;
            reminderMinutesBefore = 1_440;
        }
    }

    static ClassOccurrence regular(ScheduleDetails schedule, long epochDay) {
        return new ClassOccurrence(STATUS_REGULAR, epochDay, null,
                schedule.startMinute, schedule.endMinute, schedule.room, schedule.note,
                null, 0L, schedule, null);
    }

    static ClassOccurrence cancelled(ScheduleDetails schedule, ScheduleExceptionEntity exception,
                                     long epochDay) {
        return new ClassOccurrence(STATUS_CANCELLED, epochDay, null,
                schedule.startMinute, schedule.endMinute, schedule.room, schedule.note,
                exception.note, exception.id, schedule, null);
    }

    /** Applies the new time and room of a MODIFY exception, on the given (possibly moved) date. */
    static ClassOccurrence modified(ScheduleDetails schedule, ScheduleExceptionEntity exception,
                                    long epochDay) {
        int start = exception.newStartMinute != null ? exception.newStartMinute : schedule.startMinute;
        int end = exception.newEndMinute != null ? exception.newEndMinute : schedule.endMinute;
        String room = isBlank(exception.newRoom) ? schedule.room : exception.newRoom;
        Long original = exception.originalEpochDay != null && exception.originalEpochDay != epochDay
                ? exception.originalEpochDay : null;
        return new ClassOccurrence(STATUS_MODIFIED, epochDay, original, start, end, room,
                schedule.note, exception.note, exception.id, schedule, null);
    }

    static ClassOccurrence makeup(ClassSectionDetails section, ScheduleExceptionEntity exception) {
        return new ClassOccurrence(STATUS_MAKEUP, exception.newEpochDay, null,
                exception.newStartMinute, exception.newEndMinute, exception.newRoom, null,
                exception.note, exception.id, null, section);
    }

    public boolean isCancelled() {
        return status == STATUS_CANCELLED;
    }

    public int durationMinutes() {
        return endMinute - startMinute;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}