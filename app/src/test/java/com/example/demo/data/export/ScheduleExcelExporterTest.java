package com.example.demo.data.export;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ScheduleExcelExporterTest {
    @Test
    public void scheduleWorkbookContainsValidOpenXmlAndEscapedUnicodeText() throws Exception {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        ScheduleDetails schedule = schedule(1L, 10L, monday, monday,
                "CS<&101", "Lập trình di động 📱", "Lớp A", "A&B <201>", "ThS. O'Neil",
                "Ghi chú\u0001 cuối dòng");
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(
                Collections.singletonList(schedule), Collections.emptyList(), monday, monday);
        ScheduleExportCalculator.DateRange range = new ScheduleExportCalculator.DateRange(
                monday.toEpochDay(), monday.toEpochDay());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ScheduleExcelExporter.writeSchedule(output, "HK 1 & 2", "2026-2027",
                "Tuần/đặc biệt", range,
                ScheduleExportCalculator.occurrences(1L, snapshot, range));

        Map<String, String> entries = unzip(output.toByteArray());
        assertTrue(entries.keySet().containsAll(Arrays.asList(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml")));
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry.getKey().endsWith(".xml") || entry.getKey().endsWith(".rels")) {
                parseXml(entry.getValue());
            }
        }
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("Lập trình di động 📱"));
        assertTrue(sheet.contains("CS&lt;&amp;101"));
        assertTrue(sheet.contains("A&amp;B &lt;201&gt;"));
        assertTrue(sheet.contains("ThS. O&apos;Neil"));
        assertTrue(sheet.contains("Ghi chú� cuối dòng"));
        assertEquals("Tuần_đặc biệt", ScheduleExcelExporter.safeSheetName("Tuần/đặc biệt"));
        assertTrue(output.size() > 0);
    }

    @Test
    public void scheduleWorkbookUsesResolvedMakeupAndOmitsCancelledMeeting() throws Exception {
        LocalDate firstMonday = LocalDate.of(2026, 11, 2);
        LocalDate lastMonday = firstMonday.plusDays(14);
        ScheduleDetails schedule = schedule(1L, 10L, firstMonday, lastMonday,
                "CS101", "Cơ sở dữ liệu", "CTK44", "B204", "Cô An", "Nội dung");
        ScheduleExceptionEntity makeup = new ScheduleExceptionEntity();
        makeup.id = 2L;
        makeup.scheduleId = schedule.scheduleId;
        makeup.originalDateEpochDay = firstMonday.toEpochDay();
        makeup.exceptionType = ScheduleExceptionEntity.TYPE_MAKEUP;
        makeup.replacementDateEpochDay = firstMonday.plusDays(2).toEpochDay();
        makeup.startMinute = 10 * 60;
        makeup.endMinute = 11 * 60;
        makeup.room = "C303";
        makeup.note = "Buổi bù";
        ScheduleExceptionEntity cancelled = new ScheduleExceptionEntity();
        cancelled.id = 3L;
        cancelled.scheduleId = schedule.scheduleId;
        cancelled.originalDateEpochDay = firstMonday.plusDays(7).toEpochDay();
        cancelled.exceptionType = ScheduleExceptionEntity.TYPE_CANCEL;
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Collections.singletonList(schedule),
                Arrays.asList(makeup, cancelled), firstMonday, lastMonday);
        ScheduleExportCalculator.DateRange range = new ScheduleExportCalculator.DateRange(
                firstMonday.toEpochDay(), lastMonday.toEpochDay());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ScheduleExcelExporter.writeSchedule(output, "HK1", "2026-2027", "Học kỳ",
                range, ScheduleExportCalculator.occurrences(1L, snapshot, range));

        String sheet = unzip(output.toByteArray()).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("04/11/2026"));
        assertTrue(sheet.contains("Học bù"));
        assertTrue(sheet.contains("C303"));
        assertTrue(sheet.contains("16/11/2026"));
        assertFalse(sheet.contains("09/11/2026"));
        assertTrue(sheet.indexOf("r=\"B6\"") < sheet.indexOf("r=\"B7\""));
        assertTrue(sheet.substring(sheet.indexOf("r=\"B6\""), sheet.indexOf("</c>",
                sheet.indexOf("r=\"B6\""))).contains("04/11/2026"));
        assertTrue(sheet.substring(sheet.indexOf("r=\"B7\""), sheet.indexOf("</c>",
                sheet.indexOf("r=\"B7\""))).contains("16/11/2026"));
    }

    @Test
    public void scheduleWorkbookOrdersSameDayMeetingsByStartTime() throws Exception {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        ScheduleDetails late = schedule(1L, 20L, monday, monday,
                "LATE", "Lớp muộn", "L2", "B202", "GV 2", "");
        late.startMinute = 10 * 60;
        late.endMinute = 11 * 60;
        ScheduleDetails early = schedule(1L, 10L, monday, monday,
                "EARLY", "Lớp sớm", "L1", "B101", "GV 1", "");
        early.startMinute = 8 * 60;
        early.endMinute = 9 * 60;
        ScheduleExportCalculator.DateRange range = new ScheduleExportCalculator.DateRange(
                monday.toEpochDay(), monday.toEpochDay());
        ScheduleSnapshot snapshot = ScheduleResolver.resolve(Arrays.asList(late, early),
                Collections.emptyList(), monday, monday);
        java.util.List<ScheduleExportCalculator.Occurrence> occurrences =
                ScheduleExportCalculator.occurrences(1L, snapshot, range);
        Collections.reverse(occurrences);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ScheduleExcelExporter.writeSchedule(output, "HK 1", "2026-2027", "Ngày",
                range, occurrences);

        String sheet = unzip(output.toByteArray()).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.indexOf("EARLY") < sheet.indexOf("LATE"));
    }

    @Test
    public void subjectWorkbookExportsOnlySelectedSemesterAndIncludesClassSheet() throws Exception {
        SubjectEntity selected = new SubjectEntity(1L, "CS101", "Lập trình Android", 3,
                "Cô Lan", "blue", "Ghi chú & tài liệu");
        selected.id = 11L;
        SubjectEntity otherSemester = new SubjectEntity(2L, "CS202", "Mạng máy tính", 4,
                "Thầy Nam", "green", "Không xuất");
        otherSemester.id = 22L;
        ClassSectionEntity selectedClass = new ClassSectionEntity(
                selected.id, "CS101-01", "Cô Lan", "Thứ Hai");
        selectedClass.id = 31L;
        ClassSectionEntity otherClass = new ClassSectionEntity(
                otherSemester.id, "CS202-02", "Thầy Nam", "Không xuất");
        otherClass.id = 32L;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ScheduleExcelExporter.writeSubjects(output, 1L, "HK 1", "2026-2027",
                Arrays.asList(selected, otherSemester), Arrays.asList(selectedClass, otherClass));

        Map<String, String> entries = unzip(output.toByteArray());
        Document workbook = parseXml(entries.get("xl/workbook.xml"));
        NodeList sheets = workbook.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "sheet");
        assertEquals(2, sheets.getLength());
        String subjects = entries.get("xl/worksheets/sheet1.xml");
        String classes = entries.get("xl/worksheets/sheet2.xml");
        assertTrue(subjects.contains("Lập trình Android"));
        assertTrue(subjects.contains("Ghi chú &amp; tài liệu"));
        assertFalse(subjects.contains("Mạng máy tính"));
        assertTrue(classes.contains("CS101-01"));
        assertFalse(classes.contains("CS202-02"));
    }

    @Test
    public void writerSanitizesSheetNamesAndRejectsOversizedCells() {
        String sanitized = ScheduleExcelExporter.safeSheetName("'A/very:long*sheet[name]?'");
        assertTrue(sanitized.length() <= 31);
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains(":"));

        LocalDate monday = LocalDate.of(2026, 11, 2);
        ScheduleExportCalculator.DateRange range = new ScheduleExportCalculator.DateRange(
                monday.toEpochDay(), monday.toEpochDay());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String tooLong = String.join("", Collections.nCopies(32_768, "x"));
        assertThrows(IOException.class, () -> ScheduleExcelExporter.writeSchedule(output,
                "HK1", "2026-2027", tooLong, range, Collections.emptyList()));
    }

    private static ScheduleDetails schedule(long semesterId, long scheduleId, LocalDate from,
                                            LocalDate to, String subjectCode, String subjectName,
                                            String classCode, String room, String lecturer,
                                            String note) {
        ScheduleDetails schedule = new ScheduleDetails();
        schedule.semesterId = semesterId;
        schedule.scheduleId = scheduleId;
        schedule.classSectionId = scheduleId + 100L;
        schedule.dayOfWeek = 1;
        schedule.startMinute = 8 * 60;
        schedule.endMinute = 9 * 60;
        schedule.room = room;
        schedule.validFromEpochDay = from.toEpochDay();
        schedule.validToEpochDay = to.toEpochDay();
        schedule.note = note;
        schedule.subjectCode = subjectCode;
        schedule.subjectName = subjectName;
        schedule.classCode = classCode;
        schedule.lecturer = lecturer;
        return schedule;
    }

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                int count;
                while ((count = zip.read(buffer)) != -1) data.write(buffer, 0, count);
                result.put(entry.getName(), new String(data.toByteArray(), StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return result;
    }

    private static Document parseXml(String xml) throws Exception {
        assertNotNull(xml);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
