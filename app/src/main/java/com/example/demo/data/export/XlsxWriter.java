package com.example.demo.data.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal .xlsx (Office Open XML) writer: text, number and date cells, bold header, frozen header row. */
public final class XlsxWriter {
    private static final long EXCEL_EPOCH_OFFSET_DAYS = 25_569L; // 1970-01-01 as an Excel serial date
    private static final int STYLE_DEFAULT = 0;
    private static final int STYLE_HEADER = 1;
    private static final int STYLE_DATE = 2;
    private static final int STYLE_DECIMAL = 3;

    private XlsxWriter() {
    }

    /** A date cell, given as days since 1970-01-01 (LocalDate.toEpochDay()). */
    public static final class DateValue {
        final long epochDay;

        public DateValue(long epochDay) {
            this.epochDay = epochDay;
        }
    }

    public static DateValue date(long epochDay) {
        return new DateValue(epochDay);
    }

    public static final class Sheet {
        final String name;
        final String[] headers;
        final double[] widths;
        final List<Object[]> rows = new ArrayList<>();

        public Sheet(String name, String[] headers, double[] widths) {
            if (headers == null || headers.length == 0 || headers.length > 100
                    || (widths != null && widths.length != headers.length)) {
                throw new IllegalArgumentException("Invalid sheet columns.");
            }
            this.name = sanitizeSheetName(name);
            this.headers = headers.clone();
            this.widths = widths == null ? null : widths.clone();
        }

        /** Cells may be String, Integer/Long (integer), Double/Float (two decimals), DateValue or null. */
        public Sheet addRow(Object... cells) {
            if (cells.length > headers.length) {
                throw new IllegalArgumentException("Too many cells in row.");
            }
            rows.add(cells.clone());
            return this;
        }
    }

    public static void write(OutputStream output, List<Sheet> sheets) throws IOException {
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("At least one sheet is required.");
        }
        Set<String> names = new HashSet<>();
        for (Sheet sheet : sheets) {
            if (!names.add(sheet.name.toLowerCase())) {
                throw new IllegalArgumentException("Duplicate sheet name: " + sheet.name);
            }
        }
        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
        put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
        put(zip, "_rels/.rels", rootRels());
        put(zip, "xl/workbook.xml", workbook(sheets));
        put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
        put(zip, "xl/styles.xml", styles());
        for (int i = 0; i < sheets.size(); i++) {
            put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", worksheet(sheets.get(i)));
        }
        zip.finish();
        zip.flush();
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static String contentTypes(int sheetCount) {
        StringBuilder out = new StringBuilder(XML_HEADER);
        out.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) {
            out.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return out.append("</Types>").toString();
    }

    private static String rootRels() {
        return XML_HEADER
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"" + REL_NS + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder out = new StringBuilder(XML_HEADER);
        out.append("<workbook xmlns=\"").append(MAIN_NS).append("\" xmlns:r=\"").append(REL_NS).append("\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            out.append("<sheet name=\"").append(escape(sheets.get(i).name))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return out.append("</sheets></workbook>").toString();
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder out = new StringBuilder(XML_HEADER);
        out.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheetCount; i++) {
            out.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(REL_NS)
                    .append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        out.append("<Relationship Id=\"rId").append(sheetCount + 1).append("\" Type=\"").append(REL_NS)
                .append("/styles\" Target=\"styles.xml\"/>");
        return out.append("</Relationships>").toString();
    }

    private static String styles() {
        return XML_HEADER
                + "<styleSheet xmlns=\"" + MAIN_NS + "\">"
                + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"dd/mm/yyyy\"/></numFmts>"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2563EB\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"4\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\"/></xf>"
                + "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String worksheet(Sheet sheet) {
        int columnCount = sheet.headers.length;
        StringBuilder out = new StringBuilder(XML_HEADER);
        out.append("<worksheet xmlns=\"").append(MAIN_NS).append("\">");
        out.append("<sheetViews><sheetView workbookViewId=\"0\">")
                .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews>");
        out.append("<sheetFormatPr defaultRowHeight=\"15\"/>");
        if (sheet.widths != null) {
            out.append("<cols>");
            for (int i = 0; i < columnCount; i++) {
                out.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                        .append("\" width=\"").append(sheet.widths[i]).append("\" customWidth=\"1\"/>");
            }
            out.append("</cols>");
        }
        out.append("<sheetData>");
        out.append("<row r=\"1\">");
        for (int c = 0; c < columnCount; c++) {
            appendText(out, c, 1, sheet.headers[c], STYLE_HEADER);
        }
        out.append("</row>");
        int rowNumber = 2;
        for (Object[] row : sheet.rows) {
            out.append("<row r=\"").append(rowNumber).append("\">");
            for (int c = 0; c < row.length; c++) {
                appendCell(out, c, rowNumber, row[c]);
            }
            out.append("</row>");
            rowNumber++;
        }
        out.append("</sheetData>");
        out.append("<autoFilter ref=\"A1:").append(columnName(columnCount - 1)).append(Math.max(1, rowNumber - 1))
                .append("\"/>");
        return out.append("</worksheet>").toString();
    }

    private static void appendCell(StringBuilder out, int column, int row, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof DateValue) {
            out.append("<c r=\"").append(ref(column, row)).append("\" s=\"").append(STYLE_DATE).append("\"><v>")
                    .append(((DateValue) value).epochDay + EXCEL_EPOCH_OFFSET_DAYS).append("</v></c>");
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                return;
            }
            out.append("<c r=\"").append(ref(column, row)).append("\" s=\"").append(STYLE_DECIMAL).append("\"><v>")
                    .append(number).append("</v></c>");
        } else if (value instanceof Number) {
            out.append("<c r=\"").append(ref(column, row)).append("\" s=\"").append(STYLE_DEFAULT).append("\"><v>")
                    .append(((Number) value).longValue()).append("</v></c>");
        } else {
            String text = value.toString();
            if (!text.isEmpty()) {
                appendText(out, column, row, text, STYLE_DEFAULT);
            }
        }
    }

    private static void appendText(StringBuilder out, int column, int row, String text, int style) {
        out.append("<c r=\"").append(ref(column, row)).append("\" s=\"").append(style)
                .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                .append(escape(text)).append("</t></is></c>");
    }

    private static String ref(int column, int row) {
        return columnName(column) + row;
    }

    static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int n = index;
        do {
            name.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return name.toString();
    }

    static String sanitizeSheetName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "Sheet";
        }
        return cleaned.length() > 31 ? cleaned.substring(0, 31).trim() : cleaned;
    }

    /** Escapes XML special characters and drops characters that are not allowed in XML 1.0. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '"') {
                out.append("&quot;");
            } else if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0xD7FF)
                    || (c >= 0xE000 && c <= 0xFFFD)) {
                out.append(c);
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                out.append(c).append(text.charAt(++i));
            }
        }
        return out.toString();
    }
}