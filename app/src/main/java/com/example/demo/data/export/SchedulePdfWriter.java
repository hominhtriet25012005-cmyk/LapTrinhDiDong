package com.example.demo.data.export;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.example.demo.data.local.projection.ScheduleDetails;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws a paginated landscape-A4 timetable directly to the SAF-selected output stream. */
public final class SchedulePdfWriter {
    private static final int PAGE_WIDTH = 842;
    private static final int PAGE_HEIGHT = 595;
    private static final float LEFT = 28f;
    private static final float TABLE_TOP = 96f;
    private static final float HEADER_HEIGHT = 26f;
    private static final float ROW_HEIGHT = 49f;
    private static final int ROWS_PER_PAGE = 8;

    private static final int NAVY = 0xFF203A5C;
    private static final int TEXT = 0xFF202833;
    private static final int MUTED = 0xFF687586;
    private static final int GRID = 0xFFE0E6ED;
    private static final int STRIPE = 0xFFF5F8FB;

    private static final float[] COLUMN_WIDTHS = {104f, 88f, 202f, 94f, 97f, 201f};
    private static final String[] COLUMN_TITLES = {
            "NGÀY HỌC", "THỜI GIAN", "MÔN HỌC", "LỚP", "PHÒNG", "GIẢNG VIÊN"
    };
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE", new Locale("vi", "VN"));

    private SchedulePdfWriter() {
    }

    public static void write(OutputStream output, String semesterTitle, String academicYear,
                             String scopeTitle, ScheduleExportCalculator.DateRange range,
                             List<ScheduleExportCalculator.Occurrence> occurrences) throws IOException {
        if (output == null || range == null) throw new IllegalArgumentException("Output and date range are required.");
        List<ScheduleExportCalculator.Occurrence> rows = occurrences == null
                ? new ArrayList<>() : occurrences;
        int pageCount = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        PdfDocument document = new PdfDocument();
        try {
            for (int pageNumber = 0; pageNumber < pageCount; pageNumber++) {
                PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH, PAGE_HEIGHT, pageNumber + 1).create());
                drawPage(page.getCanvas(), pageNumber, pageCount, semesterTitle, academicYear,
                        scopeTitle, range, rows);
                document.finishPage(page);
            }
            document.writeTo(output);
            output.flush();
        } finally {
            document.close();
        }
    }

    private static void drawPage(Canvas canvas, int pageIndex, int pageCount, String semesterTitle,
                                 String academicYear, String scopeTitle,
                                 ScheduleExportCalculator.DateRange range,
                                 List<ScheduleExportCalculator.Occurrence> rows) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(TEXT);
        paint.setTextSize(17f);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        canvas.drawText("Thời khóa biểu", LEFT, 34f, paint);

        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(MUTED);
        paint.setTextSize(10f);
        String title = nonEmpty(semesterTitle, "Học kỳ")
                + (isEmpty(academicYear) ? "" : " · " + academicYear);
        canvas.drawText(title, LEFT, 53f, paint);
        String rangeLabel = range.isEmpty() ? "Chưa có ngày áp dụng"
                : formatDate(range.startEpochDay) + " – " + formatDate(range.endEpochDay);
        canvas.drawText(nonEmpty(scopeTitle, "Phạm vi") + " · " + rangeLabel, LEFT, 70f, paint);

        drawTableHeader(canvas, paint);
        int firstRow = pageIndex * ROWS_PER_PAGE;
        int lastRow = Math.min(rows.size(), firstRow + ROWS_PER_PAGE);
        if (firstRow >= lastRow) {
            paint.setColor(MUTED);
            paint.setTextSize(12f);
            canvas.drawText("Không có buổi học trong phạm vi đã chọn.", LEFT + 10f,
                    TABLE_TOP + HEADER_HEIGHT + 34f, paint);
        } else {
            for (int index = firstRow; index < lastRow; index++) {
                float top = TABLE_TOP + HEADER_HEIGHT + (index - firstRow) * ROW_HEIGHT;
                drawRow(canvas, paint, top, index - firstRow, rows.get(index));
            }
        }

        paint.setColor(MUTED);
        paint.setTextSize(9f);
        canvas.drawText("UniSchedule · Trang " + (pageIndex + 1) + " / " + pageCount,
                PAGE_WIDTH - 160f, PAGE_HEIGHT - 17f, paint);
    }

    private static void drawTableHeader(Canvas canvas, Paint paint) {
        paint.setColor(NAVY);
        canvas.drawRect(LEFT, TABLE_TOP, PAGE_WIDTH - LEFT, TABLE_TOP + HEADER_HEIGHT, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(8f);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        float x = LEFT;
        for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
            canvas.drawText(COLUMN_TITLES[i], x + 6f, TABLE_TOP + 17f, paint);
            x += COLUMN_WIDTHS[i];
        }
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    private static void drawRow(Canvas canvas, Paint paint, float top, int rowIndex,
                                ScheduleExportCalculator.Occurrence occurrence) {
        float bottom = top + ROW_HEIGHT;
        paint.setColor(rowIndex % 2 == 0 ? 0xFFFFFFFF : STRIPE);
        canvas.drawRect(LEFT, top, PAGE_WIDTH - LEFT, bottom, paint);
        paint.setColor(GRID);
        paint.setStrokeWidth(0.6f);
        canvas.drawLine(LEFT, bottom, PAGE_WIDTH - LEFT, bottom, paint);

        ScheduleDetails schedule = occurrence.schedule;
        List<String[]> cells = new ArrayList<>();
        String day = DAY_FORMAT.format(occurrence.date);
        if (!isEmpty(day)) day = Character.toUpperCase(day.charAt(0)) + day.substring(1);
        cells.add(new String[]{day, DATE_FORMAT.format(occurrence.date)});
        cells.add(new String[]{formatTime(schedule.startMinute) + " – " + formatTime(schedule.endMinute), ""});
        cells.add(new String[]{nonEmpty(schedule.subjectName, "Chưa có tên môn"), schedule.subjectCode});
        cells.add(new String[]{schedule.classCode, ""});
        cells.add(new String[]{schedule.room, ""});
        cells.add(new String[]{schedule.lecturer, ""});

        paint.setColor(TEXT);
        paint.setTextSize(8.5f);
        float x = LEFT;
        for (int i = 0; i < cells.size(); i++) {
            float cellWidth = COLUMN_WIDTHS[i] - 12f;
            drawCellLines(canvas, paint, cells.get(i), x + 6f, top + 18f, cellWidth, i == 2);
            x += COLUMN_WIDTHS[i];
        }
    }

    private static void drawCellLines(Canvas canvas, Paint paint, String[] values, float x,
                                     float baseline, float maxWidth, boolean useSecondaryLine) {
        List<String> lines = wrap(values[0], paint, maxWidth, 2);
        paint.setColor(TEXT);
        canvas.drawText(lines.get(0), x, baseline, paint);
        if (lines.size() > 1) {
            paint.setColor(MUTED);
            paint.setTextSize(7.5f);
            canvas.drawText(lines.get(1), x, baseline + 13f, paint);
            paint.setTextSize(8.5f);
        } else if (useSecondaryLine && !isEmpty(values[1])) {
            paint.setColor(MUTED);
            paint.setTextSize(7.5f);
            canvas.drawText(ellipsize(values[1], paint, maxWidth), x, baseline + 13f, paint);
            paint.setTextSize(8.5f);
        }
    }

    private static List<String> wrap(String value, Paint paint, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        String remaining = value == null ? "" : value.trim();
        if (remaining.isEmpty()) remaining = "—";
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (paint.measureText(remaining) <= maxWidth) {
                lines.add(remaining);
                break;
            }
            int cut = 0;
            for (int index = 1; index <= remaining.length(); index++) {
                if (paint.measureText(remaining.substring(0, index)) > maxWidth) break;
                if (Character.isWhitespace(remaining.charAt(index - 1))) cut = index - 1;
            }
            if (cut <= 0) {
                cut = 1;
                while (cut < remaining.length()
                        && paint.measureText(remaining.substring(0, cut + 1)) <= maxWidth) cut++;
            }
            lines.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        if (!remaining.isEmpty() && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, ellipsize(lines.get(last) + " " + remaining, paint, maxWidth));
        }
        if (lines.isEmpty()) lines.add("—");
        return lines;
    }

    private static String ellipsize(String value, Paint paint, float maxWidth) {
        String result = value == null ? "" : value.trim();
        while (!result.isEmpty() && paint.measureText(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result.isEmpty() ? "…" : result + "…";
    }

    private static String formatDate(long epochDay) {
        return DATE_FORMAT.format(LocalDate.ofEpochDay(epochDay));
    }

    private static String formatTime(int minutes) {
        int normalized = minutes == 1440 ? 0 : Math.max(0, Math.min(1439, minutes));
        return String.format(Locale.getDefault(), "%02d:%02d", normalized / 60, normalized % 60);
    }

    private static String nonEmpty(String value, String fallback) {
        return isEmpty(value) ? fallback : value.trim();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
