package com.example.demo.data.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleSnapshot;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

public class ScheduleExcelExporterTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Test
    public void scheduleWorkbookIsWellFormedAndHoldsEverySession() throws Exception {
        ExportSessions.Result result = ExportSessions.collect(snapshot(), semester(),
                ScheduleExportCalculator.Scope.SEMESTER, MONDAY, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ScheduleExcelExporter.writeSchedule(out, result.sessions);

        Map<String, byte[]> parts = unzip(out.toByteArray());
        for (String name : Arrays.asList("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml")) {
            assertNotNull("missing " + name, parts.get(name));
            parse(parts.get(name)); // throws if not well-formed XML
        }
        Document sheet = parse(parts.get("xl/worksheets/sheet1.xml"));
        NodeList rows = sheet.getElementsByTagName("row");
        // header + 3 Mondays (one cancelled, one modified) + 1 makeup = 5 rows
        assertEquals(5, rows.getLength());
        String text = new String(parts.get("xl/worksheets/sheet1.xml"), "UTF-8");
        assertTrue(text.contains("Đã hủy"));
        assertTrue(text.contains("Đã đổi lịch"));
        assertTrue(text.contains("Học bù"));
        assertTrue(text.contains("GV &lt;bận&gt; &amp; nghỉ")); // escaped reason
        // 2026-10-19 as an Excel serial date
        assertTrue(text.contains("<v>" + (MONDAY.toEpochDay() + 25569) + "</v>"));
    }

    @Test
    public void excludingCancelledDropsThatSession() {
        ExportSessions.Result with = ExportSessions.collect(snapshot(), semester(),
                ScheduleExportCalculator.Scope.SEMESTER, MONDAY, true);
        ExportSessions.Result without = ExportSessions.collect(snapshot(), semester(),
                ScheduleExportCalculator.Scope.SEMESTER, MONDAY, false);

        assertEquals(4, with.sessions.size());
        assertEquals(3, without.sessions.size());
        for (ClassOccurrence s : without.sessions) {
            assertFalse(s.isCancelled());
        }
    }

    @Test
    public void pdfRowsCarryResolvedTimeAndRoom() {
        ExportSessions.Result result = ExportSessions.collect(snapshot(), semester(),
                ScheduleExportCalculator.Scope.SEMESTER, MONDAY, false);

        List<ScheduleExportCalculator.Occurrence> rows = ExportSessions.toPdfRows(result.sessions);

        assertEquals(3, rows.size());
        // the modified Monday moved to 09:00-11:00 in room B2
        boolean foundModified = false;
        for (ScheduleExportCalculator.Occurrence row : rows) {
            if (row.schedule.startMinute == 9 * 60 && "B2".equals(row.schedule.room)) {
                foundModified = true;
            }
        }
        assertTrue(foundModified);
    }

    @Test
    public void subjectWorkbookCountsOnlyRealSessions() throws Exception {
        ExportSessions.Result result = ExportSessions.collect(snapshot(), semester(),
                ScheduleExportCalculator.Scope.SEMESTER, MONDAY, true);
        SubjectEntity subject = new SubjectEntity();
        subject.id = 100;
        subject.semesterId = 1;
        subject.code = "INT2208";
        subject.name = "Lập trình Web";
        subject.credits = 3;
        SubjectEntity noClass = new SubjectEntity();
        noClass.id = 101;
        noClass.semesterId = 1;
        noClass.code = "MAT1";
        noClass.name = "Toán";
        noClass.credits = 2;
        ClassSectionEntity section = new ClassSectionEntity();
        section.id = 10;
        section.subjectId = 100;
        section.classCode = "L01";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ScheduleExcelExporter.writeSubjects(out, Arrays.asList(subject, noClass),
                Collections.singletonList(section), result.sessions);

        String text = new String(unzip(out.toByteArray()).get("xl/worksheets/sheet1.xml"), "UTF-8");
        assertEquals(3, parse(text.getBytes("UTF-8")).getElementsByTagName("row").getLength());
        // 3 real sessions (cancelled one skipped): 2h + 2h + 2h = 6 hours
        assertTrue(text.contains("<v>3</v>"));
        assertTrue(text.contains("<v>6.0</v>"));
    }

    @Test
    public void xlsxWriterHandlesColumnNamesSheetNamesAndIllegalCharacters() throws Exception {
        assertEquals("A", XlsxWriter.columnName(0));
        assertEquals("Z", XlsxWriter.columnName(25));
        assertEquals("AA", XlsxWriter.columnName(26));
        assertEquals("AB", XlsxWriter.columnName(27));
        assertEquals("Ban nhap", XlsxWriter.sanitizeSheetName("Ban/nhap").replace("  ", " "));
        assertEquals(31, XlsxWriter.sanitizeSheetName("x".repeat(60)).length());
        assertEquals("ab", XlsxWriter.escape("a\u0000\u0001b"));

        XlsxWriter.Sheet sheet = new XlsxWriter.Sheet("Test", new String[]{"A", "B", "C"}, null);
        sheet.addRow("giá trị \u0007 lạ", null, 5);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XlsxWriter.write(out, Collections.singletonList(sheet));
        String xml = new String(unzip(out.toByteArray()).get("xl/worksheets/sheet1.xml"), "UTF-8");
        parse(xml.getBytes("UTF-8"));
        assertTrue(xml.contains("giá trị  lạ"));
        assertFalse(xml.contains("\u0007"));
        assertTrue(xml.contains("<v>5</v>"));
    }

    @Test
    public void rejectsTooManyCellsAndDuplicateSheetNames() {
        XlsxWriter.Sheet sheet = new XlsxWriter.Sheet("S", new String[]{"A"}, null);
        assertThrows(IllegalArgumentException.class, () -> sheet.addRow("1", "2"));
        XlsxWriter.Sheet a = new XlsxWriter.Sheet("Same", new String[]{"A"}, null);
        XlsxWriter.Sheet b = new XlsxWriter.Sheet("same", new String[]{"A"}, null);
        assertThrows(IllegalArgumentException.class,
                () -> XlsxWriter.write(new ByteArrayOutputStream(), Arrays.asList(a, b)));
    }

    // ---- fixtures and helpers ---------------------------------------------------------

    private static SemesterEntity semester() {
        SemesterEntity semester = new SemesterEntity();
        semester.id = 1;
        semester.name = "HK1";
        return semester;
    }

    private static ScheduleSnapshot snapshot() {
        ScheduleDetails monday = new ScheduleDetails();
        monday.scheduleId = 1;
        monday.classSectionId = 10;
        monday.dayOfWeek = 1;
        monday.startMinute = 7 * 60;
        monday.endMinute = 9 * 60;
        monday.room = "A101";
        monday.validFromEpochDay = MONDAY.toEpochDay();
        monday.validToEpochDay = MONDAY.plusDays(14).toEpochDay();
        monday.classCode = "L01";
        monday.lecturer = "GV A";
        monday.subjectId = 100;
        monday.subjectCode = "INT2208";
        monday.subjectName = "Lập trình Web";
        monday.semesterId = 1;
        monday.semesterName = "HK1";
        monday.notificationEnabled = true;
        monday.reminderMinutesBefore = 1440;

        ClassSectionDetails section = new ClassSectionDetails();
        section.classSectionId = 10;
        section.classCode = "L01";
        section.lecturer = "GV A";
        section.subjectId = 100;
        section.subjectCode = "INT2208";
        section.subjectName = "Lập trình Web";
        section.semesterId = 1;
        section.semesterName = "HK1";

        return new ScheduleSnapshot(Collections.singletonList(monday),
                Arrays.asList(
                        ScheduleExceptionEntity.cancel(1, 10, MONDAY.toEpochDay(), "GV <bận> & nghỉ"),
                        ScheduleExceptionEntity.modify(1, 10, MONDAY.plusDays(7).toEpochDay(), null,
                                9 * 60, 11 * 60, "B2", "đổi giờ"),
                        ScheduleExceptionEntity.makeup(10, MONDAY.plusDays(3).toEpochDay(),
                                13 * 60, 15 * 60, "C3", "học bù")),
                Collections.singletonList(section));
    }

    private static Map<String, byte[]> unzip(byte[] data) throws Exception {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                zip.transferTo(buffer);
                parts.put(entry.getName(), buffer.toByteArray());
            }
        }
        return parts;
    }

    private static Document parse(byte[] xml) throws Exception {
        try (InputStream in = new ByteArrayInputStream(xml)) {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
    }
}