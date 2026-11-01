package com.example.demo.data.backup;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;

import java.util.ArrayList;
import java.util.List;

/** Versioned export of learning data. Device preferences are deliberately not included. */
public final class BackupData {
    public static final String APPLICATION_ID = "com.example.demo";
    public static final int CURRENT_FORMAT_VERSION = 1;

    public String appId;
    public int formatVersion;
    public long createdAtEpochMillis;
    public List<SemesterEntity> semesters;
    public List<SubjectEntity> subjects;
    public List<ClassSectionEntity> classSections;
    public List<ScheduleEntity> schedules;
    public List<ScheduleExceptionEntity> scheduleExceptions;

    public BackupData() {
    }

    public static BackupData create(long createdAtEpochMillis,
                                    List<SemesterEntity> semesters,
                                    List<SubjectEntity> subjects,
                                    List<ClassSectionEntity> classSections,
                                    List<ScheduleEntity> schedules,
                                    List<ScheduleExceptionEntity> scheduleExceptions) {
        BackupData result = new BackupData();
        result.appId = APPLICATION_ID;
        result.formatVersion = CURRENT_FORMAT_VERSION;
        result.createdAtEpochMillis = createdAtEpochMillis;
        result.semesters = copySemesters(semesters);
        result.subjects = copySubjects(subjects);
        result.classSections = copyClassSections(classSections);
        result.schedules = copySchedules(schedules);
        result.scheduleExceptions = copyExceptions(scheduleExceptions);
        return result;
    }

    /** Detaches mutable Room rows so validation and restore use the same values. */
    public static BackupData copyOf(BackupData source) {
        if (source == null) {
            return null;
        }
        BackupData result = new BackupData();
        result.appId = source.appId;
        result.formatVersion = source.formatVersion;
        result.createdAtEpochMillis = source.createdAtEpochMillis;
        result.semesters = copySemesters(source.semesters);
        result.subjects = copySubjects(source.subjects);
        result.classSections = copyClassSections(source.classSections);
        result.schedules = copySchedules(source.schedules);
        result.scheduleExceptions = copyExceptions(source.scheduleExceptions);
        return result;
    }

    private static List<SemesterEntity> copySemesters(List<SemesterEntity> source) {
        if (source == null) return null;
        List<SemesterEntity> result = new ArrayList<>(source.size());
        for (SemesterEntity row : source) {
            if (row == null) {
                result.add(null);
                continue;
            }
            SemesterEntity copy = new SemesterEntity();
            copy.id = row.id;
            copy.name = row.name;
            copy.academicYear = row.academicYear;
            copy.startDateEpochDay = row.startDateEpochDay;
            copy.endDateEpochDay = row.endDateEpochDay;
            copy.isCurrent = row.isCurrent;
            copy.note = row.note;
            result.add(copy);
        }
        return result;
    }

    private static List<SubjectEntity> copySubjects(List<SubjectEntity> source) {
        if (source == null) return null;
        List<SubjectEntity> result = new ArrayList<>(source.size());
        for (SubjectEntity row : source) {
            if (row == null) {
                result.add(null);
                continue;
            }
            SubjectEntity copy = new SubjectEntity();
            copy.id = row.id;
            copy.semesterId = row.semesterId;
            copy.code = row.code;
            copy.name = row.name;
            copy.credits = row.credits;
            copy.defaultLecturer = row.defaultLecturer;
            copy.colorKey = row.colorKey;
            copy.note = row.note;
            result.add(copy);
        }
        return result;
    }

    private static List<ClassSectionEntity> copyClassSections(List<ClassSectionEntity> source) {
        if (source == null) return null;
        List<ClassSectionEntity> result = new ArrayList<>(source.size());
        for (ClassSectionEntity row : source) {
            if (row == null) {
                result.add(null);
                continue;
            }
            ClassSectionEntity copy = new ClassSectionEntity();
            copy.id = row.id;
            copy.subjectId = row.subjectId;
            copy.classCode = row.classCode;
            copy.lecturer = row.lecturer;
            copy.note = row.note;
            result.add(copy);
        }
        return result;
    }

    private static List<ScheduleEntity> copySchedules(List<ScheduleEntity> source) {
        if (source == null) return null;
        List<ScheduleEntity> result = new ArrayList<>(source.size());
        for (ScheduleEntity row : source) {
            if (row == null) {
                result.add(null);
                continue;
            }
            ScheduleEntity copy = new ScheduleEntity();
            copy.id = row.id;
            copy.classSectionId = row.classSectionId;
            copy.dayOfWeek = row.dayOfWeek;
            copy.startMinute = row.startMinute;
            copy.endMinute = row.endMinute;
            copy.room = row.room;
            copy.validFromEpochDay = row.validFromEpochDay;
            copy.validToEpochDay = row.validToEpochDay;
            copy.note = row.note;
            copy.notificationEnabled = row.notificationEnabled;
            copy.reminderMinutesBefore = row.reminderMinutesBefore;
            result.add(copy);
        }
        return result;
    }

    private static List<ScheduleExceptionEntity> copyExceptions(
            List<ScheduleExceptionEntity> source) {
        if (source == null) return null;
        List<ScheduleExceptionEntity> result = new ArrayList<>(source.size());
        for (ScheduleExceptionEntity row : source) {
            if (row == null) {
                result.add(null);
                continue;
            }
            ScheduleExceptionEntity copy = new ScheduleExceptionEntity();
            copy.id = row.id;
            copy.scheduleId = row.scheduleId;
            copy.originalDateEpochDay = row.originalDateEpochDay;
            copy.exceptionType = row.exceptionType;
            copy.replacementDateEpochDay = row.replacementDateEpochDay;
            copy.startMinute = row.startMinute;
            copy.endMinute = row.endMinute;
            copy.room = row.room;
            copy.note = row.note;
            result.add(copy);
        }
        return result;
    }
}
