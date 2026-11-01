package com.example.demo.data.search;

import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ClassOccurrence;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SearchFiltersTest {
    @Test
    public void filtersSubjectsByAccentInsensitiveCodeOrName() {
        SubjectEntity subject = subject(4L, "CS101", "Lập trình Java");
        SubjectEntity other = subject(4L, "NET202", "Mạng máy tính");

        List<SubjectEntity> matches = SearchFilters.filterSubjects(
                Arrays.asList(subject, other), "lap trinh");

        assertEquals(Collections.singletonList(subject), matches);
        assertEquals(1, SearchFilters.filterSubjects(Arrays.asList(subject, other), "cs101").size());
        assertEquals(2, SearchFilters.filterSubjects(Arrays.asList(subject, other), "  ").size());
    }

    @Test
    public void filtersScheduleTextAndCombinesSemesterDayAndRoom() {
        ScheduleDetails matching = schedule(4L, 2, "Lập trình Java", "LTĐ-01", "Phòng A1");
        ScheduleDetails wrongSemester = schedule(9L, 2, "Lập trình Java", "LTĐ-02", "Phòng A1");
        ScheduleDetails wrongDay = schedule(4L, 3, "Lập trình Java", "LTĐ-03", "Phòng A1");
        ScheduleDetails wrongRoom = schedule(4L, 2, "Mạng máy tính", "NET-01", "Phòng B2");

        List<ScheduleDetails> rows = Arrays.asList(matching, wrongSemester, wrongDay, wrongRoom);
        List<ScheduleDetails> result = SearchFilters.filterScheduleDetails(
                rows, "lap trinh", 4L, 2, "phong a1");

        assertEquals(Collections.singletonList(matching), result);
        assertEquals(1, SearchFilters.filterScheduleDetails(
                rows, "ltd", 4L, 2, "").size());
        assertEquals(1, SearchFilters.filterScheduleDetails(
                rows, "phong b2", 0L, 0, "").size());
        assertEquals(0, SearchFilters.filterScheduleDetails(
                rows, "", 4L, 2, "phòng C3").size());
    }

    @Test
    public void nullSourceReturnsAnEmptyList() {
        assertEquals(0, SearchFilters.filterSubjects(null, "lap").size());
        assertEquals(0, SearchFilters.filterScheduleDetails(null, "", 0L, 0, "").size());
    }

    @Test
    public void dateRangeRequiresAnActualRecurringClassDayWithinTheInclusiveRange() {
        ScheduleDetails monday = schedule(4L, 1, "Lập trình Java", "LTĐ-01", "Phòng A1");
        monday.validFromEpochDay = LocalDate.of(2026, 3, 16).toEpochDay();
        monday.validToEpochDay = LocalDate.of(2026, 3, 30).toEpochDay();
        List<ScheduleDetails> rows = Collections.singletonList(monday);

        assertEquals(0, SearchFilters.filterScheduleDetails(rows, "", 0L, 0, "",
                LocalDate.of(2026, 3, 17).toEpochDay(),
                LocalDate.of(2026, 3, 22).toEpochDay()).size());
        assertEquals(1, SearchFilters.filterScheduleDetails(rows, "", 0L, 0, "",
                LocalDate.of(2026, 3, 23).toEpochDay(),
                LocalDate.of(2026, 3, 23).toEpochDay()).size());
        assertEquals(0, SearchFilters.filterScheduleDetails(rows, "", 0L, 0, "",
                LocalDate.of(2026, 3, 31).toEpochDay(),
                LocalDate.of(2026, 4, 2).toEpochDay()).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsReversedDateRange() {
        SearchFilters.filterScheduleDetails(Collections.emptyList(), "", 0L, 0, "",
                LocalDate.of(2026, 3, 20).toEpochDay(),
                LocalDate.of(2026, 3, 19).toEpochDay());
    }

    @Test
    public void filtersResolvedOccurrencesByTheirActualDateAndOverrideRoom() {
        LocalDate monday = LocalDate.of(2026, 3, 16);
        ScheduleDetails schedule = schedule(4L, 1, "Lập trình Java", "LTĐ-01", "Phòng A1");
        schedule.scheduleId = 101L;
        schedule.validFromEpochDay = monday.toEpochDay();
        schedule.validToEpochDay = monday.plusDays(14).toEpochDay();
        ScheduleExceptionEntity moved = new ScheduleExceptionEntity();
        moved.scheduleId = schedule.scheduleId;
        moved.originalDateEpochDay = monday.toEpochDay();
        moved.exceptionType = ScheduleExceptionEntity.TYPE_MODIFY;
        moved.replacementDateEpochDay = monday.plusDays(2).toEpochDay();
        moved.room = "Phòng B2";
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Collections.singletonList(schedule),
                Collections.singletonList(moved), monday, monday.plusDays(2));

        List<ClassOccurrence> result = SearchFilters.filterOccurrences(snapshot.occurrences,
                "lap trinh", 4L, 3, "phong b2",
                monday.plusDays(2).toEpochDay(), monday.plusDays(2).toEpochDay());

        assertEquals(1, result.size());
        assertEquals(monday.plusDays(2), result.get(0).date);
        assertEquals("Phòng B2", result.get(0).room);
        assertEquals(0, SearchFilters.filterOccurrences(snapshot.occurrences,
                "", 4L, 1, "", monday.toEpochDay(), monday.toEpochDay()).size());
    }

    private static SubjectEntity subject(long semesterId, String code, String name) {
        SubjectEntity subject = new SubjectEntity();
        subject.semesterId = semesterId;
        subject.code = code;
        subject.name = name;
        return subject;
    }

    private static ScheduleDetails schedule(long semesterId, int day, String name,
                                            String classCode, String room) {
        ScheduleDetails details = new ScheduleDetails();
        details.semesterId = semesterId;
        details.dayOfWeek = day;
        details.startMinute = 540;
        details.endMinute = 600;
        details.subjectCode = "CS101";
        details.subjectName = name;
        details.classCode = classCode;
        details.room = room;
        return details;
    }
}
