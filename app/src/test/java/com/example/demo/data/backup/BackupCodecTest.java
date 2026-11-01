package com.example.demo.data.backup;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BackupCodecTest {
    @Test
    public void roundTripPreservesUnicodeNullsAndScheduleExceptions() {
        BackupData original = sampleData();

        byte[] encoded = BackupCodec.encode(original);
        BackupData restored = BackupCodec.decode(encoded);

        assertEquals(BackupData.APPLICATION_ID, restored.appId);
        assertEquals(BackupData.CURRENT_FORMAT_VERSION, restored.formatVersion);
        assertEquals("Học kỳ 1", restored.semesters.get(0).name);
        assertEquals("Lập trình di động 📱", restored.subjects.get(0).name);
        assertEquals("Phòng B204 – tầng 2", restored.scheduleExceptions.get(0).note);
        assertEquals(Long.valueOf(LocalDate.of(2026, 11, 3).toEpochDay()),
                restored.scheduleExceptions.get(0).replacementDateEpochDay);
        assertEquals("B204", restored.scheduleExceptions.get(0).room);
        assertEquals(original.createdAtEpochMillis, restored.createdAtEpochMillis);
    }

    @Test
    public void rejectsAnotherApplicationAndUnsupportedFormatVersion() {
        BackupData wrongApp = sampleData();
        wrongApp.appId = "com.example.other";
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.encode(wrongApp));

        BackupData wrongVersion = sampleData();
        wrongVersion.formatVersion = BackupData.CURRENT_FORMAT_VERSION + 1;
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.encode(wrongVersion));
    }

    @Test
    public void rejectsMissingForeignKeyAndDuplicatePrimaryKey() {
        BackupData missingSubject = sampleData();
        missingSubject.classSections.get(0).subjectId = 999L;
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.encode(missingSubject));

        BackupData duplicateSchedule = sampleData();
        ScheduleEntity duplicate = copySchedule(duplicateSchedule.schedules.get(0));
        duplicateSchedule.schedules.add(duplicate);
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.encode(duplicateSchedule));
    }

    @Test
    public void rejectsInvalidScheduleAndExceptionValues() {
        BackupData invalidTime = sampleData();
        invalidTime.schedules.get(0).endMinute = invalidTime.schedules.get(0).startMinute;
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.encode(invalidTime));

        BackupData invalidException = sampleData();
        invalidException.scheduleExceptions.get(0).startMinute = 900;
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.encode(invalidException));
    }

    @Test
    public void rejectsMalformedTrailingAndOversizedInput() {
        assertThrows(IllegalArgumentException.class,
                () -> BackupCodec.decode("{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        byte[] trailing = (new String(BackupCodec.encode(sampleData()),
                java.nio.charset.StandardCharsets.UTF_8) + " {}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> BackupCodec.decode(new byte[BackupCodec.MAX_BACKUP_BYTES + 1]));
    }

    @Test
    public void rejectsAbsentCollectionsAndMultipleCurrentSemesters() {
        BackupData missingCollection = sampleData();
        missingCollection.schedules = null;
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.encode(missingCollection));

        BackupData multipleCurrent = sampleData();
        SemesterEntity extra = new SemesterEntity("HK 2", "2026-2027", null, null,
                true, null);
        extra.id = 11L;
        multipleCurrent.semesters.add(extra);
        assertThrows(IllegalArgumentException.class, () -> BackupCodec.encode(multipleCurrent));
    }

    @Test
    public void rejectsMissingRequiredNamesAndMalformedAcademicYear() {
        BackupData missingSubjectName = sampleData();
        missingSubjectName.subjects.get(0).name = "  ";
        assertThrows(IllegalArgumentException.class,
                () -> BackupCodec.encode(missingSubjectName));

        BackupData malformedAcademicYear = sampleData();
        malformedAcademicYear.semesters.get(0).academicYear = "2025-2027";
        assertThrows(IllegalArgumentException.class,
                () -> BackupCodec.encode(malformedAcademicYear));
    }

    private static BackupData sampleData() {
        BackupData data = new BackupData();
        data.appId = BackupData.APPLICATION_ID;
        data.formatVersion = BackupData.CURRENT_FORMAT_VERSION;
        data.createdAtEpochMillis = 1_798_920_000_000L;

        LocalDate monday = LocalDate.of(2026, 11, 2);
        SemesterEntity semester = new SemesterEntity("Học kỳ 1", "2026-2027",
                monday.toEpochDay(), monday.plusMonths(4).toEpochDay(), true, "Ghi chú kỳ");
        semester.id = 10L;
        data.semesters = new ArrayList<>(Collections.singletonList(semester));

        SubjectEntity subject = new SubjectEntity(semester.id, "CS101",
                "Lập trình di động 📱", 3, "Cô Nguyễn", "blue", null);
        subject.id = 20L;
        data.subjects = new ArrayList<>(Collections.singletonList(subject));

        ClassSectionEntity classSection = new ClassSectionEntity(subject.id,
                "CTK44", "Thầy Trần", "Lớp sáng");
        classSection.id = 30L;
        data.classSections = new ArrayList<>(Collections.singletonList(classSection));

        ScheduleEntity schedule = new ScheduleEntity(classSection.id, 1, 8 * 60,
                10 * 60, "A101", monday.toEpochDay(), monday.plusWeeks(8).toEpochDay(), null);
        schedule.id = 40L;
        schedule.notificationEnabled = true;
        schedule.reminderMinutesBefore = 1_440;
        data.schedules = new ArrayList<>(Collections.singletonList(schedule));

        ScheduleExceptionEntity exception = new ScheduleExceptionEntity();
        exception.id = 50L;
        exception.scheduleId = schedule.id;
        exception.originalDateEpochDay = monday.toEpochDay();
        exception.exceptionType = ScheduleExceptionEntity.TYPE_MODIFY;
        exception.replacementDateEpochDay = monday.plusDays(1).toEpochDay();
        exception.startMinute = 9 * 60;
        exception.endMinute = 11 * 60;
        exception.room = "B204";
        exception.note = "Phòng B204 – tầng 2";
        data.scheduleExceptions = new ArrayList<>(Collections.singletonList(exception));
        return data;
    }

    private static ScheduleEntity copySchedule(ScheduleEntity source) {
        ScheduleEntity result = new ScheduleEntity();
        result.id = source.id;
        result.classSectionId = source.classSectionId;
        result.dayOfWeek = source.dayOfWeek;
        result.startMinute = source.startMinute;
        result.endMinute = source.endMinute;
        result.room = source.room;
        result.validFromEpochDay = source.validFromEpochDay;
        result.validToEpochDay = source.validToEpochDay;
        result.note = source.note;
        result.notificationEnabled = source.notificationEnabled;
        result.reminderMinutesBefore = source.reminderMinutesBefore;
        return result;
    }
}
