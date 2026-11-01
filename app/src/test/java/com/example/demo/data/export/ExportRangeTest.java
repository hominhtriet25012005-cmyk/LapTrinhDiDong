package com.example.demo.data.export;

import static org.junit.Assert.*;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.schedule.ScheduleSnapshot;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

public class ExportRangeTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Test
    public void customRangeCoversOnlyChosenDays() {
        ExportSessions.Result r = ExportSessions.collectRange(snapshot(), semester(null, null),
                MONDAY.plusDays(1).toEpochDay(), MONDAY.plusDays(7).toEpochDay(), true);
        // 22/10 makeup + 26/10 modified
        assertEquals(2, r.sessions.size());
        assertEquals(MONDAY.plusDays(1).toEpochDay(), r.range.startEpochDay);
    }

    @Test
    public void singleDayAndCancelledToggle() {
        long day = MONDAY.toEpochDay();
        assertEquals(1, ExportSessions.collectRange(snapshot(), semester(null, null), day, day, true).sessions.size());
        assertEquals(0, ExportSessions.collectRange(snapshot(), semester(null, null), day, day, false).sessions.size());
    }

    @Test
    public void clampedToSemesterAndRejectsHugeRange() {
        SemesterEntity sem = semester(MONDAY.plusDays(2).toEpochDay(), MONDAY.plusDays(20).toEpochDay());
        ExportSessions.Result r = ExportSessions.collectRange(snapshot(), sem,
                MONDAY.toEpochDay(), MONDAY.plusDays(100).toEpochDay(), true);
        assertEquals(MONDAY.plusDays(2).toEpochDay(), r.range.startEpochDay);
        assertEquals(MONDAY.plusDays(20).toEpochDay(), r.range.endEpochDay);
        assertTrue(ExportSessions.collectRange(snapshot(), sem, 100, 50, true).range.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ExportSessions.collectRange(
                snapshot(), semester(null, null), 0, 10_000, true));
    }

    private static SemesterEntity semester(Long start, Long end) {
        SemesterEntity s = new SemesterEntity("HK1", "", start, end, true, "");
        s.id = 1;
        return s;
    }

    private static ScheduleSnapshot snapshot() {
        ScheduleDetails monday = new ScheduleDetails();
        monday.scheduleId = 1; monday.classSectionId = 10; monday.dayOfWeek = 1;
        monday.startMinute = 420; monday.endMinute = 540; monday.room = "A101";
        monday.validFromEpochDay = MONDAY.toEpochDay();
        monday.validToEpochDay = MONDAY.plusDays(14).toEpochDay();
        monday.classCode = "L01"; monday.lecturer = "GV A";
        monday.subjectId = 100; monday.subjectCode = "INT2208"; monday.subjectName = "Web";
        monday.semesterId = 1; monday.semesterName = "HK1";
        ClassSectionDetails section = new ClassSectionDetails();
        section.classSectionId = 10; section.classCode = "L01"; section.lecturer = "GV A";
        section.subjectId = 100; section.subjectCode = "INT2208"; section.subjectName = "Web";
        section.semesterId = 1; section.semesterName = "HK1";
        return new ScheduleSnapshot(Collections.singletonList(monday), Arrays.asList(
                ScheduleExceptionEntity.cancel(1, 10, MONDAY.toEpochDay(), "nghỉ"),
                ScheduleExceptionEntity.modify(1, 10, MONDAY.plusDays(7).toEpochDay(), null, 540, 660, "B2", "đổi"),
                ScheduleExceptionEntity.makeup(10, MONDAY.plusDays(3).toEpochDay(), 780, 900, "C3", "bù")),
                Collections.singletonList(section));
    }
}