package com.example.demo.data.local.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "semesters", indices = {@Index(value = {"isCurrent"})})
public class SemesterEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String academicYear;
    public Long startDateEpochDay;
    public Long endDateEpochDay;
    public boolean isCurrent;
    public String note;

    public SemesterEntity() {
    }

    @Ignore
    public SemesterEntity(String name, String academicYear, Long startDateEpochDay,
                          Long endDateEpochDay, boolean isCurrent, String note) {
        this.name = name;
        this.academicYear = academicYear;
        this.startDateEpochDay = startDateEpochDay;
        this.endDateEpochDay = endDateEpochDay;
        this.isCurrent = isCurrent;
        this.note = note;
    }
}
