package com.example.demo.data.export;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes simple, standards-based XLSX workbooks without external spreadsheet dependencies. */
public final class ScheduleExcelExporter {
    public static final String MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String MAIN_NS =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PACKAGE_REL_NS =
            "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CONTENT_TYPES_NS =
            "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String OFFICE_DOCUMENT_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final String WORKSHEET_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet";
    private static final String WORKBOOK_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
    private static final String WORKSHEET_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml";

    private static final int MAX_DATA_ROWS = 10_000;
    private static final int MAX_CELL_CHARACTERS = 32_767;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final String[] WEEKDAYS = {
            "", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"
    };
    private static final Comparator<String> TEXT_ORDER = Comparator
            .nullsLast(String.CASE_INSENSITIVE_ORDER);

    private ScheduleExcelExporter() {
    }

    /**
     * Writes one worksheet with the resolved occurrences in the supplied range. The caller keeps
     * ownership of {@code output}; closing the XLSX writer does not close that stream.
     */
    public static void writeSchedule(OutputStream output, String semesterTitle,
                                     String academicYear, String scopeTitle,
                                     ScheduleExportCalculator.DateRange range,
                                     List<ScheduleExportCalculator.Occurrence> occurrences)
            throws IOException {
        if (output == null || range == null) {
            throw new IllegalArgumentException("Output and date range are required.");
        }
        List<ScheduleExportCalculator.Occurrence> rows = copyOccurrences(occurrences);
        String safeSheet = safeSheetName("TKB " + valueOrEmpty(scopeTitle));
        List<Sheet> sheets = Collections.singletonList(
                new Sheet(safeSheet, "xl/worksheets/sheet1.xml"));

        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(output))) {
            writePackage(zip, sheets);
            writeEntry(zip, sheets.get(0).path, writer ->
                    writeScheduleSheet(writer, semesterTitle, academicYear, scopeTitle, range, rows));
        }
    }

    /**
     * Writes a workbook containing a subject list and its class sections for one semester.
     * Inputs may be the full Room lists; unrelated semesters and their sections are filtered out.
     */
    public static void writeSubjects(OutputStream output, long semesterId, String semesterTitle,
                                     String academicYear, List<SubjectEntity> subjects,
                                     List<ClassSectionEntity> classSections) throws IOException {
        if (output == null || semesterId <= 0L) {
            throw new IllegalArgumentException("Output and a valid semester ID are required.");
        }
        List<SubjectEntity> subjectRows = copySubjects(semesterId, subjects);
        Set<Long> subjectIds = new HashSet<>();
        for (SubjectEntity subject : subjectRows) subjectIds.add(subject.id);
        List<ClassSectionEntity> classRows = copyClassSections(subjectIds, classSections);

        List<Sheet> sheets = Arrays.asList(
                new Sheet("MonHoc", "xl/worksheets/sheet1.xml"),
                new Sheet("LopHocPhan", "xl/worksheets/sheet2.xml"));
        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(output))) {
            writePackage(zip, sheets);
            writeEntry(zip, sheets.get(0).path, writer ->
                    writeSubjectsSheet(writer, semesterTitle, academicYear, subjectRows));
            writeEntry(zip, sheets.get(1).path, writer ->
                    writeClassSectionsSheet(writer, semesterTitle, academicYear,
                            subjectRows, classRows));
        }
    }

    static String safeSheetName(String input) {
        String source = input == null ? "" : input.trim();
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < source.length();) {
            int codePoint = source.codePointAt(offset);
            offset += Character.charCount(codePoint);
            char character = codePoint <= Character.MAX_VALUE ? (char) codePoint : '\0';
            if (character == '\\' || character == '/' || character == '?' || character == '*'
                    || character == '[' || character == ']' || character == ':') {
                result.append('_');
            } else {
                result.appendCodePoint(codePoint);
            }
            if (result.codePointCount(0, result.length()) >= 31) break;
        }
        while (result.length() > 0 && result.charAt(0) == '\'') result.deleteCharAt(0);
        while (result.length() > 0 && result.charAt(result.length() - 1) == '\'') {
            result.deleteCharAt(result.length() - 1);
        }
        return result.length() == 0 ? "LichHoc" : result.toString();
    }

    private static List<ScheduleExportCalculator.Occurrence> copyOccurrences(
            List<ScheduleExportCalculator.Occurrence> source) {
        List<ScheduleExportCalculator.Occurrence> result = source == null
                ? new ArrayList<>() : new ArrayList<>(source);
        if (result.size() > MAX_DATA_ROWS) {
            throw new IllegalArgumentException("Phạm vi vượt quá 10.000 dòng Excel.");
        }
        for (ScheduleExportCalculator.Occurrence occurrence : result) {
            if (occurrence == null || occurrence.date == null || occurrence.schedule == null
                    || occurrence.classOccurrence == null) {
                throw new IllegalArgumentException("Danh sách buổi học có dữ liệu không hợp lệ.");
            }
        }
        result.sort(Comparator
                .comparing((ScheduleExportCalculator.Occurrence row) -> row.date)
                .thenComparingInt(row -> row.classOccurrence.startMinute)
                .thenComparingInt(row -> row.classOccurrence.endMinute)
                .thenComparing(row -> row.schedule.subjectCode, TEXT_ORDER)
                .thenComparing(row -> row.schedule.classCode, TEXT_ORDER));
        return result;
    }

    private static List<SubjectEntity> copySubjects(long semesterId, List<SubjectEntity> source) {
        List<SubjectEntity> result = new ArrayList<>();
        if (source != null) {
            for (SubjectEntity row : source) {
                if (row != null && row.semesterId == semesterId) result.add(row);
            }
        }
        if (result.size() > MAX_DATA_ROWS) {
            throw new IllegalArgumentException("Danh sách môn vượt quá 10.000 dòng Excel.");
        }
        result.sort(Comparator.comparing((SubjectEntity row) -> row.code, TEXT_ORDER)
                .thenComparing(row -> row.name, TEXT_ORDER).thenComparingLong(row -> row.id));
        return result;
    }

    private static List<ClassSectionEntity> copyClassSections(Set<Long> subjectIds,
                                                               List<ClassSectionEntity> source) {
        List<ClassSectionEntity> result = new ArrayList<>();
        if (source != null) {
            for (ClassSectionEntity row : source) {
                if (row != null && subjectIds.contains(row.subjectId)) result.add(row);
            }
        }
        if (result.size() > MAX_DATA_ROWS) {
            throw new IllegalArgumentException("Danh sách lớp vượt quá 10.000 dòng Excel.");
        }
        result.sort(Comparator.comparingLong((ClassSectionEntity row) -> row.subjectId)
                .thenComparing(row -> row.classCode, TEXT_ORDER).thenComparingLong(row -> row.id));
        return result;
    }

    private static void writePackage(ZipOutputStream zip, List<Sheet> sheets) throws IOException {
        writeEntry(zip, "[Content_Types].xml", writer -> {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            writer.write("<Types xmlns=\""); writer.write(CONTENT_TYPES_NS); writer.write("\">");
            writer.write("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
            writer.write("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
            writer.write("<Override PartName=\"/xl/workbook.xml\" ContentType=\"");
            writer.write(WORKBOOK_CONTENT_TYPE); writer.write("\"/>");
            for (int index = 0; index < sheets.size(); index++) {
                writer.write("<Override PartName=\"/xl/worksheets/sheet");
                writer.write(Integer.toString(index + 1));
                writer.write(".xml\" ContentType=\""); writer.write(WORKSHEET_CONTENT_TYPE);
                writer.write("\"/>");
            }
            writer.write("</Types>");
        });
        writeEntry(zip, "_rels/.rels", writer -> {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            writer.write("<Relationships xmlns=\""); writer.write(PACKAGE_REL_NS); writer.write("\">");
            writer.write("<Relationship Id=\"rId1\" Type=\""); writer.write(OFFICE_DOCUMENT_REL);
            writer.write("\" Target=\"xl/workbook.xml\"/></Relationships>");
        });
        writeEntry(zip, "xl/workbook.xml", writer -> {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            writer.write("<workbook xmlns=\""); writer.write(MAIN_NS); writer.write("\" xmlns:r=\"");
            writer.write(REL_NS); writer.write("\"><sheets>");
            for (int index = 0; index < sheets.size(); index++) {
                writer.write("<sheet name=\""); writeAttribute(writer, sheets.get(index).name);
                writer.write("\" sheetId=\""); writer.write(Integer.toString(index + 1));
                writer.write("\" r:id=\"rId"); writer.write(Integer.toString(index + 1));
                writer.write("\"/>");
            }
            writer.write("</sheets></workbook>");
        });
        writeEntry(zip, "xl/_rels/workbook.xml.rels", writer -> {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            writer.write("<Relationships xmlns=\""); writer.write(PACKAGE_REL_NS); writer.write("\">");
            for (int index = 0; index < sheets.size(); index++) {
                writer.write("<Relationship Id=\"rId"); writer.write(Integer.toString(index + 1));
                writer.write("\" Type=\""); writer.write(WORKSHEET_REL);
                writer.write("\" Target=\"worksheets/sheet");
                writer.write(Integer.toString(index + 1)); writer.write(".xml\"/>");
            }
            writer.write("</Relationships>");
        });
    }

    private static void writeScheduleSheet(Writer writer, String semesterTitle,
                                           String academicYear, String scopeTitle,
                                           ScheduleExportCalculator.DateRange range,
                                           List<ScheduleExportCalculator.Occurrence> rows)
            throws IOException {
        startSheet(writer);
        writeTextRow(writer, 1, new String[]{"Thời khóa biểu"});
        writeTextRow(writer, 2, new String[]{semesterLabel(semesterTitle, academicYear)});
        writeTextRow(writer, 3, new String[]{valueOrEmpty(scopeTitle)});
        writeTextRow(writer, 4, new String[]{formatRange(range)});
        writeTextRow(writer, 5, new String[]{"STT", "Ngày", "Thứ", "Bắt đầu", "Kết thúc",
                "Mã môn", "Môn học", "Mã lớp", "Phòng", "Giảng viên", "Loại buổi", "Ghi chú"});
        for (int index = 0; index < rows.size(); index++) {
            ScheduleExportCalculator.Occurrence occurrence = rows.get(index);
            ScheduleDetails details = occurrence.schedule;
            int rowNumber = index + 6;
            writer.write("<row r=\""); writer.write(Integer.toString(rowNumber)); writer.write("\">");
            writeNumberCell(writer, "A", rowNumber, index + 1);
            writeTextCell(writer, "B", rowNumber, DATE_FORMAT.format(occurrence.date));
            writeTextCell(writer, "C", rowNumber,
                    WEEKDAYS[occurrence.date.getDayOfWeek().getValue()]);
            writeTextCell(writer, "D", rowNumber, formatTime(occurrence.classOccurrence.startMinute));
            writeTextCell(writer, "E", rowNumber, formatTime(occurrence.classOccurrence.endMinute));
            writeTextCell(writer, "F", rowNumber, details.subjectCode);
            writeTextCell(writer, "G", rowNumber, details.subjectName);
            writeTextCell(writer, "H", rowNumber, details.classCode);
            writeTextCell(writer, "I", rowNumber, occurrence.classOccurrence.room);
            writeTextCell(writer, "J", rowNumber, details.lecturer);
            writeTextCell(writer, "K", rowNumber, kindLabel(occurrence.classOccurrence.kind));
            writeTextCell(writer, "L", rowNumber, occurrence.classOccurrence.note);
            writer.write("</row>");
        }
        writer.write("</sheetData></worksheet>");
    }

    private static void writeSubjectsSheet(Writer writer, String semesterTitle, String academicYear,
                                           List<SubjectEntity> subjects) throws IOException {
        startSheet(writer);
        writeTextRow(writer, 1, new String[]{"Danh sách môn học"});
        writeTextRow(writer, 2, new String[]{semesterLabel(semesterTitle, academicYear)});
        writeTextRow(writer, 4, new String[]{"STT", "Mã môn", "Tên môn", "Tín chỉ",
                "Giảng viên", "Ghi chú"});
        for (int index = 0; index < subjects.size(); index++) {
            SubjectEntity subject = subjects.get(index);
            int rowNumber = index + 5;
            writer.write("<row r=\""); writer.write(Integer.toString(rowNumber)); writer.write("\">");
            writeNumberCell(writer, "A", rowNumber, index + 1);
            writeTextCell(writer, "B", rowNumber, subject.code);
            writeTextCell(writer, "C", rowNumber, subject.name);
            writeNumberCell(writer, "D", rowNumber, subject.credits);
            writeTextCell(writer, "E", rowNumber, subject.defaultLecturer);
            writeTextCell(writer, "F", rowNumber, subject.note);
            writer.write("</row>");
        }
        writer.write("</sheetData></worksheet>");
    }

    private static void writeClassSectionsSheet(Writer writer, String semesterTitle,
                                                String academicYear, List<SubjectEntity> subjects,
                                                List<ClassSectionEntity> classSections)
            throws IOException {
        startSheet(writer);
        writeTextRow(writer, 1, new String[]{"Danh sách lớp học phần"});
        writeTextRow(writer, 2, new String[]{semesterLabel(semesterTitle, academicYear)});
        writeTextRow(writer, 4, new String[]{"Mã môn", "Tên môn", "Mã lớp học phần",
                "Giảng viên", "Ghi chú"});
        java.util.Map<Long, SubjectEntity> subjectsById = new java.util.HashMap<>();
        for (SubjectEntity subject : subjects) subjectsById.put(subject.id, subject);
        for (int index = 0; index < classSections.size(); index++) {
            ClassSectionEntity section = classSections.get(index);
            SubjectEntity subject = subjectsById.get(section.subjectId);
            int rowNumber = index + 5;
            writer.write("<row r=\""); writer.write(Integer.toString(rowNumber)); writer.write("\">");
            writeTextCell(writer, "A", rowNumber, subject == null ? "" : subject.code);
            writeTextCell(writer, "B", rowNumber, subject == null ? "" : subject.name);
            writeTextCell(writer, "C", rowNumber, section.classCode);
            writeTextCell(writer, "D", rowNumber, section.lecturer);
            writeTextCell(writer, "E", rowNumber, section.note);
            writer.write("</row>");
        }
        writer.write("</sheetData></worksheet>");
    }

    private static void startSheet(Writer writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        writer.write("<worksheet xmlns=\""); writer.write(MAIN_NS); writer.write("\"><sheetData>");
    }

    private static void writeTextRow(Writer writer, int rowNumber, String[] values)
            throws IOException {
        writer.write("<row r=\""); writer.write(Integer.toString(rowNumber)); writer.write("\">");
        for (int index = 0; index < values.length; index++) {
            writeTextCell(writer, columnName(index + 1), rowNumber, values[index]);
        }
        writer.write("</row>");
    }

    private static void writeTextCell(Writer writer, String column, int row, String value)
            throws IOException {
        String text = xmlSafe(valueOrEmpty(value));
        if (text.length() > MAX_CELL_CHARACTERS) {
            throw new IOException("Một ô Excel vượt quá giới hạn 32.767 ký tự.");
        }
        writer.write("<c r=\""); writer.write(column); writer.write(Integer.toString(row));
        writer.write("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">");
        writeText(writer, text);
        writer.write("</t></is></c>");
    }

    private static void writeNumberCell(Writer writer, String column, int row, int value)
            throws IOException {
        writer.write("<c r=\""); writer.write(column); writer.write(Integer.toString(row));
        writer.write("\"><v>"); writer.write(Integer.toString(value)); writer.write("</v></c>");
    }

    private static void writeEntry(ZipOutputStream zip, String name, XmlContent content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Writer writer = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
        content.write(writer);
        writer.flush();
        zip.closeEntry();
    }

    private static String columnName(int column) {
        StringBuilder name = new StringBuilder();
        int current = column;
        while (current > 0) {
            current--;
            name.insert(0, (char) ('A' + current % 26));
            current /= 26;
        }
        return name.toString();
    }

    private static String semesterLabel(String semesterTitle, String academicYear) {
        String name = valueOrEmpty(semesterTitle).trim();
        String year = valueOrEmpty(academicYear).trim();
        if (name.isEmpty()) name = "Học kỳ";
        return year.isEmpty() ? name : name + " · " + year;
    }

    private static String formatRange(ScheduleExportCalculator.DateRange range) {
        if (range.isEmpty()) return "Chưa có ngày trong phạm vi đã chọn";
        try {
            return DATE_FORMAT.format(LocalDate.ofEpochDay(range.startEpochDay)) + " – "
                    + DATE_FORMAT.format(LocalDate.ofEpochDay(range.endEpochDay));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Khoảng ngày xuất Excel không hợp lệ.", exception);
        }
    }

    private static String formatTime(int minute) {
        if (minute < 0 || minute > 1_440) {
            throw new IllegalArgumentException("Giờ học không hợp lệ trong dữ liệu xuất.");
        }
        int displayMinute = minute == 1_440 ? 0 : minute;
        return String.format(Locale.ROOT, "%02d:%02d", displayMinute / 60,
                displayMinute % 60);
    }

    private static String kindLabel(com.example.demo.data.model.ClassOccurrence.Kind kind) {
        if (kind == com.example.demo.data.model.ClassOccurrence.Kind.MAKEUP) return "Học bù";
        if (kind == com.example.demo.data.model.ClassOccurrence.Kind.MODIFIED) return "Đổi lịch";
        return "Lịch thường";
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void writeAttribute(Writer writer, String value) throws IOException {
        writeText(writer, xmlSafe(value));
    }

    private static void writeText(Writer writer, String value) throws IOException {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            switch (codePoint) {
                case '&': writer.write("&amp;"); break;
                case '<': writer.write("&lt;"); break;
                case '>': writer.write("&gt;"); break;
                case '\"': writer.write("&quot;"); break;
                case '\'': writer.write("&apos;"); break;
                default: writer.write(Character.toChars(codePoint));
            }
        }
    }

    private static String xmlSafe(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean allowed = codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                    || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                    || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                    || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
            result.appendCodePoint(allowed ? codePoint : 0xFFFD);
        }
        return result.toString();
    }

    private interface XmlContent {
        void write(Writer writer) throws IOException;
    }

    private static final class Sheet {
        final String name;
        final String path;

        Sheet(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
