package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ClassOccurrence;
import com.example.demo.data.model.ScheduleSnapshot;
import com.example.demo.data.model.ScheduleResolver;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScheduleResolverTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    @Test
    public void expandsRecurringSchedulesInsideInclusiveRangeInDateTimeOrder() {
        ScheduleDetails monday = schedule(1L, 1, 540, 600, MONDAY, MONDAY.plusDays(14));
        ScheduleDetails wednesday = schedule(2L, 3, 480, 540, MONDAY, MONDAY.plusDays(9));

        ScheduleSnapshot snapshot = ScheduleResolver.resolve(
                Arrays.asList(monday, wednesday), Collections.emptyList(),
                MONDAY, MONDAY.plusDays(9));

        assertEquals(4, snapshot.occurrences.size());
        assertEquals(MONDAY, snapshot.occurrences.get(0).date);
        assertEquals(MONDAY.plusDays(2), snapshot.occurrences.get(1).date);
        assertEquals(MONDAY.plusDays(7), snapshot.occurrences.get(2).date);
        assertEquals(MONDAY.plusDays(9), snapshot.occurrences.get(3).date);
    }

    @Test
    public void cancellationRemovesOnlyTheSelectedOccurrence() {
        ScheduleDetails monday = schedule(1L, 1, 540, 600, MONDAY, MONDAY.plusDays(14));
        ScheduleExceptionEntity cancellation = exception(
                1L, MONDAY.plusDays(7), ScheduleExceptionEntity.TYPE_CANCEL, null,
                null, null, null, null);

        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Collections.singletonList(monday),
                Collections.singletonList(cancellation), MONDAY, MONDAY.plusDays(14));

        assertEquals(2, snapshot.occurrences.size());
        assertEquals(MONDAY, snapshot.occurrences.get(0).date);
        assertEquals(MONDAY.plusDays(14), snapshot.occurrences.get(1).date);
    }

    @Test
    public void modificationMovesAndOverridesOccurrenceWithoutDuplicate() {
        ScheduleDetails monday = schedule(1L, 1, 540, 600, MONDAY, MONDAY.plusDays(14));
        ScheduleExceptionEntity modification = exception(
                1L, MONDAY.plusDays(7), ScheduleExceptionEntity.TYPE_MODIFY,
                MONDAY.plusDays(10), 600, 690, "B-204", "Đổi phòng");

        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Collections.singletonList(monday),
                Collections.singletonList(modification), MONDAY.plusDays(7), MONDAY.plusDays(10));

        assertEquals(1, snapshot.occurrences.size());
        ClassOccurrence occurrence = snapshot.occurrences.get(0);
        assertEquals(MONDAY.plusDays(10), occurrence.date);
        assertEquals(MONDAY.plusDays(7), occurrence.originalDate);
        assertEquals(ClassOccurrence.Kind.MODIFIED, occurrence.kind);
        assertEquals(600, occurrence.startMinute);
        assertEquals(690, occurrence.endMinute);
        assertEquals("B-204", occurrence.room);
        assertEquals("Đổi phòng", occurrence.note);
    }

    @Test
    public void includesMakeupInRangeWhenItsOriginalDateIsOutsideRange() {
        ScheduleDetails monday = schedule(1L, 1, 540, 600, MONDAY, MONDAY.plusDays(14));
        ScheduleExceptionEntity makeup = exception(
                1L, MONDAY, ScheduleExceptionEntity.TYPE_MAKEUP,
                MONDAY.plusDays(9), 660, 720, null, null);

        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Collections.singletonList(monday),
                Collections.singletonList(makeup), MONDAY.plusDays(9), MONDAY.plusDays(9));

        assertEquals(1, snapshot.occurrences.size());
        assertEquals(ClassOccurrence.Kind.MAKEUP, snapshot.occurrences.get(0).kind);
        assertEquals(660, snapshot.occurrences.get(0).startMinute);
        assertEquals("R101", snapshot.occurrences.get(0).room);
    }

    @Test
    public void keepsRecurringAndMakeupOccurrencesOnSameDateAsDistinctEvents() {
        ScheduleDetails monday = schedule(1L, 1, 540, 600, MONDAY, MONDAY.plusDays(14));
        ScheduleExceptionEntity makeup = exception(
                1L, MONDAY, ScheduleExceptionEntity.TYPE_MAKEUP,
                MONDAY.plusDays(7), 660, 720, null, null);

        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Collections.singletonList(monday),
                Collections.singletonList(makeup), MONDAY.plusDays(7), MONDAY.plusDays(7));

        assertEquals(2, snapshot.occurrences.size());
        assertEquals(540, snapshot.occurrences.get(0).startMinute);
        assertEquals(660, snapshot.occurrences.get(1).startMinute);
        assertTrue(snapshot.occurrences.get(0).date.equals(snapshot.occurrences.get(1).date));
    }

    @Test
    public void reversedRangeReturnsAnEmptySnapshot() {
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Collections.emptyList(),
                Collections.emptyList(), MONDAY.plusDays(1), MONDAY);

        assertTrue(snapshot.occurrences.isEmpty());
    }

    private static ScheduleDetails schedule(long id, int weekday, int start, int end,
                                            LocalDate from, LocalDate to) {
        ScheduleDetails row = new ScheduleDetails();
        row.scheduleId = id;
        row.semesterId = 10L;
        row.dayOfWeek = weekday;
        row.startMinute = start;
        row.endMinute = end;
        row.room = "R101";
        row.note = "Ghi chú gốc";
        row.validFromEpochDay = from.toEpochDay();
        row.validToEpochDay = to.toEpochDay();
        row.subjectName = "Lập trình";
        return row;
    }

    private static ScheduleExceptionEntity exception(long scheduleId, LocalDate original,
                                                       String type, LocalDate replacement,
                                                       Integer start, Integer end, String room,
                                                       String note) {
        ScheduleExceptionEntity exception = new ScheduleExceptionEntity();
        exception.scheduleId = scheduleId;
        exception.originalDateEpochDay = original.toEpochDay();
        exception.exceptionType = type;
        exception.replacementDateEpochDay = replacement == null ? null : replacement.toEpochDay();
        exception.startMinute = start;
        exception.endMinute = end;
        exception.room = room;
        exception.note = note;
        return exception;
    }
}
