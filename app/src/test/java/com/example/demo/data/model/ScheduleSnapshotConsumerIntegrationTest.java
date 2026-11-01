package com.example.demo.data.model;

import com.example.demo.data.backup.BackupCodec;
import com.example.demo.data.backup.BackupData;
import com.example.demo.data.export.ScheduleExcelExporter;
import com.example.demo.data.export.ScheduleExportCalculator;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.search.SearchFilters;
import com.example.demo.data.validation.HomeDashboardCalculator;
import com.example.demo.data.validation.SemesterStatisticsCalculator;
import com.example.demo.notifications.DailySummaryFormatter;
import com.example.demo.notifications.ScheduleReminderPlanner;
import com.example.demo.settings.WeekCalculator;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScheduleSnapshotConsumerIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    public void allConsumersAndBackupUseTheSameEffectiveOccurrence() throws Exception {
        LocalDate start = LocalDate.of(2026, 3, 2);
        LocalDate end = start.plusDays(6);
        BackupData backup = sampleBackup(start, end);
        ScheduleSnapshot snapshot = snapshotFrom(backup, start, end);
        LocalDate actualDate = start.plusDays(2);

        assertEquals(1, snapshot.occurrences.size());
        ClassOccurrence occurrence = snapshot.occurrences.get(0);
        assertEquals(actualDate, occurrence.date);
        assertEquals(660, occurrence.startMinute);
        assertEquals(720, occurrence.endMinute);
        assertEquals("B-204", occurrence.room);
        assertEquals(ClassOccurrence.Kind.MODIFIED, occurrence.kind);

        HomeDashboardCalculator.DashboardSummary dashboard = HomeDashboardCalculator.calculate(
                snapshot, actualDate, 8 * 60, WeekCalculator.MONDAY);
        SemesterEntity semester = backup.semesters.get(0);
        SemesterStatisticsCalculator.Statistics statistics =
                SemesterStatisticsCalculator.calculate(semester, 1, snapshot);
        ScheduleExportCalculator.DateRange range = new ScheduleExportCalculator.DateRange(
                start.toEpochDay(), end.toEpochDay());
        List<ScheduleExportCalculator.Occurrence> exportRows =
                ScheduleExportCalculator.occurrences(semester.id, snapshot, range);
        List<ClassOccurrence> searchRows = SearchFilters.filterOccurrences(snapshot.occurrences,
                "cs101", semester.id, actualDate.getDayOfWeek().getValue(), "b-204",
                actualDate.toEpochDay(), actualDate.toEpochDay());
        Instant now = LocalDateTime.of(2026, 3, 1, 8, 0).atZone(ZONE).toInstant();
        List<ScheduleReminderPlanner.Reminder> reminders =
                ScheduleReminderPlanner.planNextPerSchedule(snapshot, now, ZONE, true);
        ByteArrayOutputStream excelOutput = new ByteArrayOutputStream();
        ScheduleExcelExporter.writeSchedule(excelOutput, semester.name, semester.academicYear,
                "Học kỳ", range, exportRows);
        String excelSheet = readZipEntry(excelOutput.toByteArray(),
                "xl/worksheets/sheet1.xml");

        assertEquals(1, dashboard.todayOccurrences.size());
        assertEquals(660, dashboard.nextOccurrence.startMinute);
        assertEquals(1, statistics.totalClassOccurrences);
        assertEquals(60, statistics.totalSemesterMinutes);
        assertEquals(1, searchRows.size());
        assertEquals(1, exportRows.size());
        assertEquals(actualDate, exportRows.get(0).date);
        assertEquals(1, reminders.size());
        assertEquals(actualDate.toEpochDay(), reminders.get(0).occurrenceDateEpochDay);
        assertEquals(660, reminders.get(0).occurrence.startMinute);
        assertEquals("B-204", reminders.get(0).occurrence.room);
        assertTrue(DailySummaryFormatter.formatAgenda(snapshot.on(actualDate), "—")
                .contains("11:00–12:00 · Lập trình Android · B-204"));
        assertTrue(excelSheet.contains("04/03/2026"));
        assertTrue(excelSheet.contains("11:00"));
        assertTrue(excelSheet.contains("B-204"));

        BackupData restored = BackupCodec.decode(BackupCodec.encode(backup));
        ScheduleSnapshot afterRestore = snapshotFrom(restored, start, end);
        assertEquals(snapshot.occurrences.size(), afterRestore.occurrences.size());
        assertEquals(occurrence.date, afterRestore.occurrences.get(0).date);
        assertEquals(occurrence.startMinute, afterRestore.occurrences.get(0).startMinute);
        assertEquals(occurrence.endMinute, afterRestore.occurrences.get(0).endMinute);
        assertEquals(occurrence.room, afterRestore.occurrences.get(0).room);
        assertEquals(occurrence.kind, afterRestore.occurrences.get(0).kind);
    }

    private static BackupData sampleBackup(LocalDate start, LocalDate end) {
        SemesterEntity semester = new SemesterEntity("HK1", "2025-2026",
                start.toEpochDay(), end.toEpochDay(), true, "");
        semester.id = 8L;
        SubjectEntity subject = new SubjectEntity(semester.id, "CS101",
                "Lập trình Android", 3, "Cô Lan", "blue", "");
        subject.id = 9L;
        ClassSectionEntity classSection = new ClassSectionEntity(
                subject.id, "CTK44", "Cô Lan", "");
        classSection.id = 10L;
        ScheduleEntity schedule = new ScheduleEntity(classSection.id, 1, 540, 600,
                "R101", start.toEpochDay(), end.toEpochDay(), "");
        schedule.id = 88L;
        schedule.notificationEnabled = true;
        schedule.reminderMinutesBefore = 1_440;
        ScheduleExceptionEntity modification = new ScheduleExceptionEntity();
        modification.id = 90L;
        modification.scheduleId = schedule.id;
        modification.originalDateEpochDay = start.toEpochDay();
        modification.exceptionType = ScheduleExceptionEntity.TYPE_MODIFY;
        modification.replacementDateEpochDay = start.plusDays(2).toEpochDay();
        modification.startMinute = 660;
        modification.endMinute = 720;
        modification.room = "B-204";
        modification.note = "Đổi phòng";
        return BackupData.create(1_798_920_000_000L,
                Collections.singletonList(semester), Collections.singletonList(subject),
                Collections.singletonList(classSection), Collections.singletonList(schedule),
                Collections.singletonList(modification));
    }

    private static ScheduleSnapshot snapshotFrom(BackupData data, LocalDate from, LocalDate to) {
        ScheduleDetails schedule = new ScheduleDetails();
        SemesterEntity semester = data.semesters.get(0);
        SubjectEntity subject = data.subjects.get(0);
        ClassSectionEntity classSection = data.classSections.get(0);
        ScheduleEntity row = data.schedules.get(0);
        schedule.scheduleId = row.id;
        schedule.classSectionId = classSection.id;
        schedule.dayOfWeek = row.dayOfWeek;
        schedule.startMinute = row.startMinute;
        schedule.endMinute = row.endMinute;
        schedule.room = row.room;
        schedule.validFromEpochDay = row.validFromEpochDay;
        schedule.validToEpochDay = row.validToEpochDay;
        schedule.note = row.note;
        schedule.notificationEnabled = row.notificationEnabled;
        schedule.reminderMinutesBefore = row.reminderMinutesBefore;
        schedule.classCode = classSection.classCode;
        schedule.lecturer = classSection.lecturer;
        schedule.subjectId = subject.id;
        schedule.subjectCode = subject.code;
        schedule.subjectName = subject.name;
        schedule.subjectColorKey = subject.colorKey;
        schedule.semesterId = semester.id;
        schedule.semesterName = semester.name;
        schedule.semesterAcademicYear = semester.academicYear;
        return ScheduleResolver.resolve(Collections.singletonList(schedule),
                data.scheduleExceptions, from, to);
    }

    private static String readZipEntry(byte[] zipBytes, String expectedName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[2048];
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int count;
                while ((count = zip.read(buffer)) != -1) content.write(buffer, 0, count);
                if (expectedName.equals(entry.getName())) {
                    return new String(content.toByteArray(), StandardCharsets.UTF_8);
                }
                zip.closeEntry();
            }
        }
        throw new IOException("Missing XLSX entry: " + expectedName);
    }
}
