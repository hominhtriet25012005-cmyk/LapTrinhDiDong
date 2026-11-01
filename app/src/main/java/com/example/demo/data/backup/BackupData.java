package com.example.demo.data.backup;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All user data stored in the app, as written to and read from a backup file. */
public final class BackupData {
    public final long createdAtMillis;
    public final List<SemesterEntity> semesters;
    public final List<SubjectEntity> subjects;
    public final List<ClassSectionEntity> classSections;
    public final List<ScheduleEntity> schedules;
    public final List<ScheduleExceptionEntity> exceptions;

    public BackupData(long createdAtMillis,
                      List<SemesterEntity> semesters,
                      List<SubjectEntity> subjects,
                      List<ClassSectionEntity> classSections,
                      List<ScheduleEntity> schedules,
                      List<ScheduleExceptionEntity> exceptions) {
        this.createdAtMillis = createdAtMillis;
        this.semesters = copy(semesters);
        this.subjects = copy(subjects);
        this.classSections = copy(classSections);
        this.schedules = copy(schedules);
        this.exceptions = copy(exceptions);
    }

    private static <T> List<T> copy(List<T> source) {
        return source == null
                ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(source));
    }
}