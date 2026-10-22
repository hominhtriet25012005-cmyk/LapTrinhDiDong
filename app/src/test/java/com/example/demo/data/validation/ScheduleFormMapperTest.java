package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ScheduleFormMapperTest {

    @Test
    public void mapsClassAndWeeklyScheduleFormToRoomEntities() {
        ClassSectionEntity classSection = ScheduleFormMapper.toClassSection(
                null, 5L, " CS101-02 ", " Dr. Nguyen ", " note ");
        ScheduleEntity schedule = ScheduleFormMapper.toSchedule(
                null, 0L, 1, "07:30 AM", "09:00 AM", " A101 ",
                "16/03/2026", "30/06/2026", " Bring laptop ");

        assertEquals(5L, classSection.subjectId);
        assertEquals("CS101-02", classSection.classCode);
        assertEquals("Dr. Nguyen", classSection.lecturer);
        assertEquals("note", classSection.note);
        assertEquals(0L, schedule.classSectionId);
        assertEquals(450, schedule.startMinute);
        assertEquals(540, schedule.endMinute);
        assertEquals("A101", schedule.room);
        assertEquals(LocalDate.of(2026, 3, 16).toEpochDay(), schedule.validFromEpochDay);
        assertEquals(LocalDate.of(2026, 6, 30).toEpochDay(), schedule.validToEpochDay);
        assertEquals("Bring laptop", schedule.note);
    }

    @Test
    public void convertsAfternoonAndMidnightTimesTo24HourMinutes() {
        ScheduleEntity schedule = ScheduleFormMapper.toSchedule(
                null, 7L, 7, "01:15 PM", "11:45 PM", "", "01/04/2026", "31/05/2026", "");

        assertEquals(795, schedule.startMinute);
        assertEquals(1_425, schedule.endMinute);
    }

    @Test
    public void rejectsBlankClassCodeAndMalformedOrReversedScheduleValues() {
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleFormMapper.toClassSection(null, 1L, "  ", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleFormMapper.toSchedule(null, 1L, 1, "7:30 AM", "09:00 AM", "",
                        "16/03/2026", "30/06/2026", ""));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleFormMapper.toSchedule(null, 1L, 1, "07:30 AM", "09:00 AM", "",
                        "31/02/2026", "30/06/2026", ""));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleFormMapper.toSchedule(null, 1L, 1, "09:00 AM", "08:00 AM", "",
                        "16/03/2026", "30/06/2026", ""));
    }
}
