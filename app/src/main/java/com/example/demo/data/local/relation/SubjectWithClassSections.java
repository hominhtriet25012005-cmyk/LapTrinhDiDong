package com.example.demo.data.local.relation;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.SubjectEntity;

import java.util.List;

public class SubjectWithClassSections {
    @Embedded
    public SubjectEntity subject;

    @Relation(parentColumn = "id", entityColumn = "subjectId")
    public List<ClassSectionEntity> classSections;
}
