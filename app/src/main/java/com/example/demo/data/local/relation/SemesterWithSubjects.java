package com.example.demo.data.local.relation;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;

import java.util.List;

public class SemesterWithSubjects {
    @Embedded
    public SemesterEntity semester;

    @Relation(parentColumn = "id", entityColumn = "semesterId")
    public List<SubjectEntity> subjects;
}
