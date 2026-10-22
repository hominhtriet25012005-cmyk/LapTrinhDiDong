package com.example.demo.data.local.relation;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;

import java.util.List;

public class ClassSectionWithSchedules {
    @Embedded
    public ClassSectionEntity classSection;

    @Relation(parentColumn = "id", entityColumn = "classSectionId")
    public List<ScheduleEntity> schedules;
}
