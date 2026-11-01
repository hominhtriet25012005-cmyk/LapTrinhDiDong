package com.example.demo.data.schedule;

import static org.junit.Assert.assertEquals;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SessionSearchTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Test
    public void singleDayReturnsOnlyThatDateIncludingCancelled() {
        List<ClassOccurrence> day = ScheduleResolver.resolve(snapshot(), MONDAY.toEpochDay(), MONDAY.toEpochDay(), true);
        assertEquals(1, day.size());
        assertEquals(ClassOccurrence.STATUS_CANCELLED, day.get(0).status);
    }

    @Test
    public void rangeFindsMakeupAndModified() {
        List<ClassOccurrence> all = ScheduleResolver.resolve(snapshot(), MONDAY.toEpochDay(),
                MONDAY.plusDays(14).toEpochDay(), true);
        assertEquals(4, all.size());
        assertEquals(1, SessionSearch.filter(all, "", 0, 4, "").size()); // makeup is Thursday
        assertEquals(1, SessionSearch.filter(all, "", 0, 0, "b2").size()); // modified room
    }

    @Test
    public void textIsAccentAndCaseInsensitive() {
        List<ClassOccurrence> all = ScheduleResolver.resolve(snapshot(), MONDAY.toEpochDay(),
                MONDAY.plusDays(14).toEpochDay(), true);
        assertEquals(4, SessionSearch.filter(all, "LAP TRINH", 0, 0, "").size());
        assertEquals(4, SessionSearch.filter(all, "gv a", 0, 0, "").size());
        assertEquals(0, SessionSearch.filter(all, "toan", 0, 0, "").size());
        assertEquals(0, SessionSearch.filter(all, "", 99, 0, "").size());
        assertEquals(4, SessionSearch.filter(all, "", 1, 0, "").size());
    }

    private static ScheduleSnapshot snapshot() {
        ScheduleDetails monday = new ScheduleDetails();
        monday.scheduleId = 1; monday.classSectionId = 10; monday.dayOfWeek = 1;
        monday.startMinute = 420; monday.endMinute = 540; monday.room = "A101";
        monday.validFromEpochDay = MONDAY.toEpochDay();
        monday.validToEpochDay = MONDAY.plusDays(14).toEpochDay();
        monday.classCode = "L01"; monday.lecturer = "GV A";
        monday.subjectId = 100; monday.subjectCode = "INT2208"; monday.subjectName = "Lập trình Web";
        monday.semesterId = 1; monday.semesterName = "HK1";
        ClassSectionDetails section = new ClassSectionDetails();
        section.classSectionId = 10; section.classCode = "L01"; section.lecturer = "GV A";
        section.subjectId = 100; section.subjectCode = "INT2208"; section.subjectName = "Lập trình Web";
        section.semesterId = 1; section.semesterName = "HK1";
        return new ScheduleSnapshot(Collections.singletonList(monday), Arrays.asList(
                ScheduleExceptionEntity.cancel(1, 10, MONDAY.toEpochDay(), "nghỉ"),
                ScheduleExceptionEntity.modify(1, 10, MONDAY.plusDays(7).toEpochDay(), null, 540, 660, "B2", "đổi"),
                ScheduleExceptionEntity.makeup(10, MONDAY.plusDays(3).toEpochDay(), 780, 900, "C3", "bù")),
                Collections.singletonList(section));
    }
}