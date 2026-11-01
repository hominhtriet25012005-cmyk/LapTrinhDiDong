package com.example.demo.data.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

public class BackupCodecTest {
    private static final long MONDAY = LocalDate.of(2026, 10, 19).toEpochDay();

    @Test
    public void roundTripKeepsAllFieldsIncludingNullsAndSpecialCharacters() throws Exception {
        BackupData original = sampleData();

        BackupData restored = BackupCodec.fromJson(BackupCodec.toJson(original));

        assertEquals(1234L, restored.createdAtMillis);
        assertEquals(1, restored.semesters.size());
        SemesterEntity semester = restored.semesters.get(0);
        assertEquals("Học kỳ \"1\"\nDòng 2 \\ tab\t", semester.name);
        assertNull(semester.academicYear);
        assertEquals(Long.valueOf(MONDAY), semester.startDateEpochDay);
        assertTrue(semester.isCurrent);
        assertEquals("Lập trình Web", restored.subjects.get(0).name);
        assertEquals(3, restored.subjects.get(0).credits);
        assertEquals("L01", restored.classSections.get(0).classCode);
        ScheduleEntity schedule = restored.schedules.get(0);
        assertEquals(1, schedule.dayOfWeek);
        assertEquals(7 * 60, schedule.startMinute);
        assertEquals(1440, schedule.reminderMinutesBefore);
        assertEquals(3, restored.exceptions.size());
        ScheduleExceptionEntity cancel = restored.exceptions.get(0);
        assertEquals("CANCEL", cancel.type);
        assertEquals(Long.valueOf(1), cancel.scheduleId);
        assertEquals("GV bận", cancel.note);
        ScheduleExceptionEntity makeup = restored.exceptions.get(2);
        assertNull(makeup.scheduleId);
        assertEquals(Integer.valueOf(9 * 60), makeup.newStartMinute);
    }

    @Test
    public void emptyBackupRoundTrips() throws Exception {
        BackupData empty = new BackupData(0, null, null, null, null, null);

        BackupData restored = BackupCodec.fromJson(BackupCodec.toJson(empty));

        assertTrue(restored.semesters.isEmpty());
        assertTrue(restored.exceptions.isEmpty());
    }

    @Test
    public void rejectsFilesThatAreNotBackups() {
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(""));
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson("not json"));
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson("[1,2,3]"));
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson("{\"app\":\"Other\"}"));
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(
                BackupCodec.toJson(sampleData()).substring(0, 200)));
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(
                BackupCodec.toJson(sampleData()) + "extra"));
    }

    @Test
    public void rejectsUnsupportedVersion() {
        String json = BackupCodec.toJson(sampleData()).replace("\"formatVersion\": 1", "\"formatVersion\": 99");
        BackupFormatException error = assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(json));
        assertTrue(error.getMessage().contains("Phiên bản"));
    }

    @Test
    public void rejectsDanglingReferences() {
        BackupData data = sampleData();
        data.subjects.get(0).semesterId = 99;
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(data)));

        BackupData data2 = sampleData();
        data2.schedules.get(0).classSectionId = 99;
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(data2)));
    }

    @Test
    public void rejectsInvalidScheduleValues() {
        BackupData badDay = sampleData();
        badDay.schedules.get(0).dayOfWeek = 8;
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(badDay)));

        BackupData badTime = sampleData();
        badTime.schedules.get(0).endMinute = badTime.schedules.get(0).startMinute;
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(badTime)));

        BackupData badRange = sampleData();
        badRange.schedules.get(0).validToEpochDay = badRange.schedules.get(0).validFromEpochDay - 1;
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(badRange)));
    }

    @Test
    public void rejectsDuplicateIdsAndTwoCurrentSemesters() {
        BackupData dup = sampleData();
        SemesterEntity second = new SemesterEntity();
        second.id = 1;
        second.name = "Trùng id";
        BackupData withDup = new BackupData(0, Arrays.asList(dup.semesters.get(0), second),
                dup.subjects, dup.classSections, dup.schedules, dup.exceptions);
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(withDup)));

        SemesterEntity otherCurrent = new SemesterEntity();
        otherCurrent.id = 2;
        otherCurrent.name = "Hiện tại 2";
        otherCurrent.isCurrent = true;
        BackupData twoCurrent = new BackupData(0, Arrays.asList(dup.semesters.get(0), otherCurrent),
                dup.subjects, dup.classSections, dup.schedules, dup.exceptions);
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(twoCurrent)));
    }

    @Test
    public void rejectsInvalidExceptions() {
        // Cancel on a Tuesday for a Monday schedule.
        BackupData wrongDay = sampleData();
        wrongDay.exceptions.get(0).originalEpochDay = MONDAY + 1;
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(wrongDay)));

        BackupData unknownType = sampleData();
        unknownType.exceptions.get(0).type = "DELETE_ALL";
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(unknownType)));

        BackupData unknownSchedule = sampleData();
        unknownSchedule.exceptions.get(0).scheduleId = 77L;
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(BackupCodec.toJson(unknownSchedule)));
    }

    @Test
    public void acceptsByteOrderMarkAndRejectsFractionalNumbers() throws Exception {
        String json = BackupCodec.toJson(sampleData());
        assertEquals(1, BackupCodec.fromJson("\uFEFF" + json).semesters.size());
        assertThrows(BackupFormatException.class, () -> BackupCodec.fromJson(
                json.replace("\"credits\":3", "\"credits\":3.5")));
    }

    private static BackupData sampleData() {
        SemesterEntity semester = new SemesterEntity();
        semester.id = 1;
        semester.name = "Học kỳ \"1\"\nDòng 2 \\ tab\t";
        semester.startDateEpochDay = MONDAY;
        semester.endDateEpochDay = MONDAY + 100;
        semester.isCurrent = true;

        SubjectEntity subject = new SubjectEntity();
        subject.id = 1;
        subject.semesterId = 1;
        subject.code = "INT2208";
        subject.name = "Lập trình Web";
        subject.credits = 3;
        subject.colorKey = "blue";

        ClassSectionEntity section = new ClassSectionEntity();
        section.id = 1;
        section.subjectId = 1;
        section.classCode = "L01";
        section.lecturer = "Nguyễn Văn A";

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.id = 1;
        schedule.classSectionId = 1;
        schedule.dayOfWeek = 1;
        schedule.startMinute = 7 * 60;
        schedule.endMinute = 9 * 60;
        schedule.room = "A101";
        schedule.validFromEpochDay = MONDAY - 7;
        schedule.validToEpochDay = MONDAY + 60;
        schedule.notificationEnabled = true;
        schedule.reminderMinutesBefore = 1440;

        ScheduleExceptionEntity cancel = ScheduleExceptionEntity.cancel(1, 1, MONDAY, "GV bận");
        cancel.id = 1;
        cancel.createdAtMillis = 5;
        ScheduleExceptionEntity modify = ScheduleExceptionEntity.modify(
                1, 1, MONDAY + 7, null, null, null, "B2", "đổi phòng");
        modify.id = 2;
        ScheduleExceptionEntity makeup = ScheduleExceptionEntity.makeup(
                1, MONDAY + 2, 9 * 60, 11 * 60, "C3", "học bù");
        makeup.id = 3;

        return new BackupData(1234L, Collections.singletonList(semester),
                Collections.singletonList(subject), Collections.singletonList(section),
                Collections.singletonList(schedule), Arrays.asList(cancel, modify, makeup));
    }
}