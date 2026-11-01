package com.example.demo.data.schedule;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All data needed to resolve concrete class sessions, loaded together so it is consistent. */
public final class ScheduleSnapshot {
    public final List<ScheduleDetails> schedules;
    public final List<ScheduleExceptionEntity> exceptions;
    public final List<ClassSectionDetails> classSections;

    public ScheduleSnapshot(List<ScheduleDetails> schedules,
                            List<ScheduleExceptionEntity> exceptions,
                            List<ClassSectionDetails> classSections) {
        this.schedules = copy(schedules);
        this.exceptions = copy(exceptions);
        this.classSections = copy(classSections);
    }

    private static <T> List<T> copy(List<T> source) {
        return source == null
                ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(source));
    }
}