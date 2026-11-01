package com.example.demo.data.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScheduleResolverTest {
    private static final long MON_SEP_7 = LocalDate.of(2026, 9, 7).toEpochDay();
    private static final long MON_SEP_14 = MON_SEP_7 + 7;
    private static final long MON_SEP_21 = MON_SEP_7 + 14;
    private static final long MON_SEP_28 = MON_SEP_7 + 21;
    private static final long WED_SEP_16 = MON_SEP_14 + 2;

    /** Schedule 1: Mondays 07:00-09:00, Sep 7 - Sep 28. */
    private static ScheduleDetails mondaySchedule() {
        ScheduleDetails d = new ScheduleDetails();
        d.scheduleId = 1;
        d.classSectionId = 10;
        d.dayOfWeek = 1;
        d.startMinute = 7 * 60;
        d.endMinute = 9 * 60;
        d.room = "A101";
        d.validFromEpochDay = MON_SEP_7;
        d.validToEpochDay = MON_SEP_28;
        d.note = "ghi chu goc";
        d.classCode = "L01";
        d.lecturer = "GV A";
        d.subjectId = 100;
        d.subjectCode = "IT101";
        d.subjectName = "Lap trinh di dong";
        d.subjectColorKey = "blue";
        d.semesterId = 1000;
        d.semesterName = "HK1";
        d.semesterAcademicYear = "2026-2027";
        return d;
    }

    private static ClassSectionDetails section10() {
        ClassSectionDetails c = new ClassSectionDetails();
        c.classSectionId = 10;
        c.classCode = "L01";
        c.lecturer = "GV A";
        c.subjectId = 100;
        c.subjectCode = "IT101";
        c.subjectName = "Lap trinh di dong";
        c.subjectColorKey = "blue";
        c.semesterId = 1000;
        c.semesterName = "HK1";
        c.semesterAcademicYear = "2026-2027";
        return c;
    }

    private static ScheduleSnapshot snapshot(ScheduleExceptionEntity... exceptions) {
        return new ScheduleSnapshot(Collections.singletonList(mondaySchedule()),
                Arrays.asList(exceptions), Collections.singletonList(section10()));
    }

    private static ScheduleExceptionEntity withId(ScheduleExceptionEntity e, long id) {
        e.id = id;
        return e;
    }

    @Test
    public void weeklyScheduleRepeatsOnlyOnItsWeekdayInsideItsRange() {
        ScheduleSnapshot data = snapshot();

        assertEquals(1, ScheduleResolver.resolveDay(data, MON_SEP_7, false).size());
        assertEquals(0, ScheduleResolver.resolveDay(data, MON_SEP_7 + 1, false).size());
        List<ClassOccurrence> month = ScheduleResolver.resolve(data, MON_SEP_7 - 10, MON_SEP_28 + 30, false);
        assertEquals(4, month.size());
        assertEquals(MON_SEP_28, month.get(3).epochDay);
        assertEquals(ClassOccurrence.STATUS_REGULAR, month.get(0).status);
    }

    @Test
    public void cancelRemovesOnlyThatSession() {
        ScheduleSnapshot data = snapshot(withId(
                ScheduleExceptionEntity.cancel(1, 10, MON_SEP_14, "GV nghi"), 5));

        List<ClassOccurrence> shown = ScheduleResolver.resolve(data, MON_SEP_7, MON_SEP_28, false);
        assertEquals(3, shown.size());
        for (ClassOccurrence item : shown) {
            assertTrue(item.epochDay != MON_SEP_14);
        }

        List<ClassOccurrence> withCancelled = ScheduleResolver.resolve(data, MON_SEP_7, MON_SEP_28, true);
        assertEquals(4, withCancelled.size());
        ClassOccurrence cancelled = withCancelled.get(1);
        assertEquals(MON_SEP_14, cancelled.epochDay);
        assertEquals(ClassOccurrence.STATUS_CANCELLED, cancelled.status);
        assertEquals("GV nghi", cancelled.changeNote);
    }

    @Test
    public void modifyChangesTimeAndRoomOfOneSessionOnly() {
        ScheduleSnapshot data = snapshot(withId(ScheduleExceptionEntity.modify(
                1, 10, MON_SEP_14, null, 8 * 60, 10 * 60, "B202", "Doi phong"), 6));

        List<ClassOccurrence> all = ScheduleResolver.resolve(data, MON_SEP_7, MON_SEP_28, false);
        assertEquals(4, all.size());
        ClassOccurrence changed = all.get(1);
        assertEquals(ClassOccurrence.STATUS_MODIFIED, changed.status);
        assertEquals(8 * 60, changed.startMinute);
        assertEquals(10 * 60, changed.endMinute);
        assertEquals("B202", changed.room);
        assertEquals("Doi phong", changed.changeNote);
        assertEquals("ghi chu goc", changed.note);
        assertNull(changed.originalEpochDay);

        assertEquals(7 * 60, all.get(0).startMinute);
        assertEquals("A101", all.get(0).room);
        assertEquals("A101", all.get(2).room);
    }

    @Test
    public void modifyKeepsOriginalValuesWhenOnlyRoomChanges() {
        ScheduleSnapshot data = snapshot(withId(ScheduleExceptionEntity.modify(
                1, 10, MON_SEP_14, null, null, null, "B202", ""), 6));

        ClassOccurrence changed = ScheduleResolver.resolveDay(data, MON_SEP_14, false).get(0);
        assertEquals(7 * 60, changed.startMinute);
        assertEquals(9 * 60, changed.endMinute);
        assertEquals("B202", changed.room);
    }

    @Test
    public void modifyCanMoveASessionToAnotherDate() {
        ScheduleSnapshot data = snapshot(withId(ScheduleExceptionEntity.modify(
                1, 10, MON_SEP_14, WED_SEP_16, 13 * 60, 15 * 60, null, "Doi ngay"), 7));

        assertEquals(0, ScheduleResolver.resolveDay(data, MON_SEP_14, false).size());
        List<ClassOccurrence> moved = ScheduleResolver.resolveDay(data, WED_SEP_16, false);
        assertEquals(1, moved.size());
        assertEquals(ClassOccurrence.STATUS_MODIFIED, moved.get(0).status);
        assertEquals(Long.valueOf(MON_SEP_14), moved.get(0).originalEpochDay);
        assertEquals(13 * 60, moved.get(0).startMinute);
        assertEquals("A101", moved.get(0).room);
    }

    @Test
    public void movedSessionShowsEvenWhenItsOriginalDateIsOutsideTheRange() {
        ScheduleSnapshot data = snapshot(withId(ScheduleExceptionEntity.modify(
                1, 10, MON_SEP_14, WED_SEP_16, null, null, null, ""), 7));

        List<ClassOccurrence> week = ScheduleResolver.resolve(data, MON_SEP_14 + 1, MON_SEP_14 + 6, false);
        assertEquals(1, week.size());
        assertEquals(WED_SEP_16, week.get(0).epochDay);
    }

    @Test
    public void makeupIsIndependentOfTheWeeklyScheduleRange() {
        long outside = MON_SEP_28 + 10;
        ScheduleSnapshot data = snapshot(withId(ScheduleExceptionEntity.makeup(
                10, outside, 13 * 60, 15 * 60, "C3", "Hoc bu"), 8));

        List<ClassOccurrence> day = ScheduleResolver.resolveDay(data, outside, false);
        assertEquals(1, day.size());
        ClassOccurrence makeup = day.get(0);
        assertEquals(ClassOccurrence.STATUS_MAKEUP, makeup.status);
        assertEquals(0L, makeup.scheduleId);
        assertEquals(8L, makeup.exceptionId);
        assertEquals("Lap trinh di dong", makeup.subjectName);
        assertEquals("C3", makeup.room);
        assertEquals("Hoc bu", makeup.changeNote);
    }

    @Test
    public void makeupOfUnknownClassSectionIsIgnored() {
        ScheduleSnapshot data = snapshot(ScheduleExceptionEntity.makeup(
                999, MON_SEP_7 + 2, 13 * 60, 15 * 60, "", ""));

        assertEquals(0, ScheduleResolver.resolveDay(data, MON_SEP_7 + 2, false).size());
    }

    @Test
    public void exceptionForUnknownScheduleIsIgnored() {
        ScheduleSnapshot data = snapshot(ScheduleExceptionEntity.cancel(77, 10, MON_SEP_14, ""));

        assertEquals(4, ScheduleResolver.resolve(data, MON_SEP_7, MON_SEP_28, false).size());
    }

    @Test
    public void resultsAreSortedByDateThenTime() {
        ScheduleSnapshot data = snapshot(
                ScheduleExceptionEntity.makeup(10, MON_SEP_7, 6 * 60, 7 * 60, "", ""),
                ScheduleExceptionEntity.makeup(10, MON_SEP_7, 15 * 60, 16 * 60, "", ""));

        List<ClassOccurrence> day = ScheduleResolver.resolveDay(data, MON_SEP_7, false);
        assertEquals(3, day.size());
        assertEquals(6 * 60, day.get(0).startMinute);
        assertEquals(7 * 60, day.get(1).startMinute);
        assertEquals(15 * 60, day.get(2).startMinute);
    }

    @Test
    public void overlapDetectionAllowsAdjacentAndIgnoresCancelledAndSelf() {
        ScheduleSnapshot data = snapshot(withId(
                ScheduleExceptionEntity.makeup(10, MON_SEP_7, 10 * 60, 11 * 60, "", ""), 9));
        List<ClassOccurrence> day = ScheduleResolver.resolveDay(data, MON_SEP_7, true);

        assertEquals(1, ScheduleResolver.findOverlaps(day, 8 * 60, 9 * 60 + 30, 0, 0).size());
        assertEquals(0, ScheduleResolver.findOverlaps(day, 9 * 60, 10 * 60, 0, 0).size());
        assertEquals(0, ScheduleResolver.findOverlaps(day, 8 * 60, 9 * 60, 1, 0).size());
        assertEquals(0, ScheduleResolver.findOverlaps(day, 10 * 60, 11 * 60, 0, 9).size());

        ScheduleSnapshot cancelledData = snapshot(withId(
                ScheduleExceptionEntity.cancel(1, 10, MON_SEP_7, ""), 3));
        List<ClassOccurrence> cancelledDay = ScheduleResolver.resolveDay(cancelledData, MON_SEP_7, true);
        assertEquals(1, cancelledDay.size());
        assertEquals(0, ScheduleResolver.findOverlaps(cancelledDay, 7 * 60, 9 * 60, 0, 0).size());
    }

    @Test
    public void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleResolver.resolve(null, MON_SEP_7, MON_SEP_7, false));
        assertThrows(IllegalArgumentException.class, () ->
                ScheduleResolver.resolve(snapshot(), MON_SEP_14, MON_SEP_7, false));
    }
}