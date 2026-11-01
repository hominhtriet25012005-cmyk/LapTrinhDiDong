package com.example.demo.data.schedule;

import static org.junit.Assert.*;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

public class SessionStatisticsTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Test
    public void countsRealSessionsAndSkipsCancelled() {
        SemesterEntity semester = semester(null, null);
        long[] span = SessionStatistics.semesterSpan(snapshot(), semester);
        assertArrayEquals(new long[]{MONDAY.toEpochDay(), MONDAY.plusDays(14).toEpochDay()}, span);

        SessionStatistics.Result r = SessionStatistics.calculate(snapshot(), semester, span[0], span[1]);

        assertEquals(3, r.sessionCount);       // 26/10 modified, 2/11 regular, 22/10 makeup
        assertEquals(1, r.cancelledCount);
        assertEquals(1, r.modifiedCount);
        assertEquals(1, r.makeupCount);
        assertEquals(360, r.totalMinutes);
        assertEquals(2, r.sessionsByDay[0]);
        assertEquals(1, r.sessionsByDay[3]);
        assertEquals(240, r.minutesByDay[0]);
        assertEquals(3, r.weekCount);
        assertEquals(1.0, r.sessionsPerWeek, 1e-9);
        assertEquals(120.0, r.minutesPerWeek, 1e-9);
    }

    @Test
    public void rangeIsClampedToSemesterDates() {
        SemesterEntity semester = semester(MONDAY.plusDays(1).toEpochDay(), MONDAY.plusDays(6).toEpochDay());
        SessionStatistics.Result r = SessionStatistics.calculate(snapshot(), semester,
                MONDAY.toEpochDay(), MONDAY.plusDays(14).toEpochDay());
        assertEquals(1, r.sessionCount);   // only the makeup on 22/10
        assertEquals(0, r.cancelledCount); // 19/10 is outside the semester
        assertEquals(MONDAY.plusDays(1).toEpochDay(), r.fromEpochDay);
    }

    @Test
    public void emptyWhenRangeOutsideSemester() {
        SemesterEntity semester = semester(MONDAY.toEpochDay(), MONDAY.plusDays(3).toEpochDay());
        SessionStatistics.Result r = SessionStatistics.calculate(snapshot(), semester,
                MONDAY.plusDays(10).toEpochDay(), MONDAY.plusDays(20).toEpochDay());
        assertTrue(r.emptyRange);
        assertEquals(0, r.sessionCount);
        assertEquals(0.0, r.minutesPerWeek, 0.0);
        assertNull(SessionStatistics.semesterSpan(SessionStatistics.emptySnapshot(), semester(null, null)));
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