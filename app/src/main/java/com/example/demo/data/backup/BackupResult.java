package com.example.demo.data.backup;

/** Lightweight completion details; contains counts, not the full backup payload. */
public final class BackupResult {
    public final long byteCount;
    public final long createdAtEpochMillis;
    public final int semesterCount;
    public final int subjectCount;
    public final int classSectionCount;
    public final int scheduleCount;
    public final int scheduleExceptionCount;

    BackupResult(long byteCount, BackupData data) {
        this.byteCount = byteCount;
        this.createdAtEpochMillis = data.createdAtEpochMillis;
        this.semesterCount = data.semesters.size();
        this.subjectCount = data.subjects.size();
        this.classSectionCount = data.classSections.size();
        this.scheduleCount = data.schedules.size();
        this.scheduleExceptionCount = data.scheduleExceptions.size();
    }
}
