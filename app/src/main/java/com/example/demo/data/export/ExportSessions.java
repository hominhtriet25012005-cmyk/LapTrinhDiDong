package com.example.demo.data.export;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleResolver;
import com.example.demo.data.schedule.ScheduleSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects the real class sessions of a semester for export, honouring cancelled, changed and makeup sessions. */
public final class ExportSessions {
    private static final int MAX_SESSIONS = 10_000;
    private static final long MAX_RANGE_DAYS = 3_660L;

    private ExportSessions() {
    }

    public static final class Result {
        public final ScheduleExportCalculator.DateRange range;
        public final List<ClassOccurrence> sessions;

        Result(ScheduleExportCalculator.DateRange range, List<ClassOccurrence> sessions) {
            this.range = range;
            this.sessions = sessions;
        }
    }

    public static Result collect(ScheduleSnapshot snapshot, SemesterEntity semester,
                                 ScheduleExportCalculator.Scope scope, LocalDate today,
                                 boolean includeCancelled) {
        if (snapshot == null || semester == null) {
            throw new IllegalArgumentException("Snapshot and semester are required.");
        }
        List<ScheduleDetails> semesterSchedules = new ArrayList<>();
        for (ScheduleDetails schedule : snapshot.schedules) {
            if (schedule != null && schedule.semesterId == semester.id) {
                semesterSchedules.add(schedule);
            }
        }
        ScheduleExportCalculator.DateRange range =
                ScheduleExportCalculator.resolveRange(scope, today, semester, semesterSchedules);
        return build(snapshot, semester, range, includeCancelled);
    }

    /** A user-chosen date range (inclusive), cut to the semester's own dates when it has them. */
    public static Result collectRange(ScheduleSnapshot snapshot, SemesterEntity semester,
                                      long fromEpochDay, long toEpochDay, boolean includeCancelled) {
        if (snapshot == null || semester == null) {
            throw new IllegalArgumentException("Snapshot and semester are required.");
        }
        long start = fromEpochDay;
        long end = toEpochDay;
        if (semester.startDateEpochDay != null) start = Math.max(start, semester.startDateEpochDay);
        if (semester.endDateEpochDay != null) end = Math.min(end, semester.endDateEpochDay);
        if (end >= start && end - start + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Khoảng ngày quá dài để xuất an toàn.");
        }
        return build(snapshot, semester, new ScheduleExportCalculator.DateRange(start, end), includeCancelled);
    }

    private static Result build(ScheduleSnapshot snapshot, SemesterEntity semester,
                                ScheduleExportCalculator.DateRange range, boolean includeCancelled) {
        if (range.isEmpty()) {
            return new Result(range, Collections.<ClassOccurrence>emptyList());
        }
        List<ClassOccurrence> sessions = new ArrayList<>();
        for (ClassOccurrence occurrence : ScheduleResolver.resolve(
                snapshot, range.startEpochDay, range.endEpochDay, includeCancelled)) {
            if (occurrence.semesterId == semester.id) {
                sessions.add(occurrence);
                if (sessions.size() > MAX_SESSIONS) {
                    throw new IllegalArgumentException("Phạm vi có quá nhiều buổi học để xuất an toàn.");
                }
            }
        }
        return new Result(range, sessions);
    }

    /** Converts resolved sessions to the row type used by the PDF writer. */
    public static List<ScheduleExportCalculator.Occurrence> toPdfRows(List<ClassOccurrence> sessions) {
        List<ScheduleExportCalculator.Occurrence> rows = new ArrayList<>();
        for (ClassOccurrence session : sessions) {
            LocalDate date = LocalDate.ofEpochDay(session.epochDay);
            ScheduleDetails details = new ScheduleDetails();
            details.scheduleId = session.scheduleId;
            details.classSectionId = session.classSectionId;
            details.dayOfWeek = date.getDayOfWeek().getValue();
            details.startMinute = session.startMinute;
            details.endMinute = session.endMinute;
            details.room = session.room;
            details.validFromEpochDay = session.epochDay;
            details.validToEpochDay = session.epochDay;
            details.note = session.note;
            details.classCode = session.classCode;
            details.lecturer = session.lecturer;
            details.subjectId = session.subjectId;
            details.subjectCode = session.subjectCode;
            details.subjectName = session.subjectName;
            details.subjectColorKey = session.subjectColorKey;
            details.semesterId = session.semesterId;
            details.semesterName = session.semesterName;
            details.semesterAcademicYear = session.semesterAcademicYear;
            rows.add(ScheduleExportCalculator.Occurrence.of(date, details));
        }
        return rows;
    }
}