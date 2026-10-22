package com.example.demo.data.local.projection;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.example.demo.data.local.entity.SemesterEntity;

/** Semester data shown in the list, including a live count of linked subjects. */
public class SemesterSummary {
    @Embedded
    public SemesterEntity semester;

    @ColumnInfo(name = "subjectCount")
    public int subjectCount;
}
