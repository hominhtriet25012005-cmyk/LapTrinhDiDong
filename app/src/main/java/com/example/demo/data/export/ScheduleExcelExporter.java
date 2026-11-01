package com.example.demo.data.export;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.schedule.ClassOccurrence;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Writes the timetable and the subject list as .xlsx workbooks. */
public final class ScheduleExcelExporter {
    private static final String[] DAY_NAMES = {
            "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"
    };

    private ScheduleExcelExporter() {
    }

    public static void writeSchedule(OutputStream output, List<ClassOccurrence> sessions) throws IOException {
        XlsxWriter.Sheet sheet = new XlsxWriter.Sheet("Thời khóa biểu",
                new String[]{"Ngày", "Thứ", "Bắt đầu", "Kết thúc", "Số giờ", "Môn học", "Mã môn",
                        "Lớp", "Phòng", "Giảng viên", "Trạng thái", "Lý do thay đổi", "Ghi chú lịch"},
                new double[]{13, 11, 10, 10, 9, 32, 12, 12, 12, 24, 14, 28, 28});
        for (ClassOccurrence s : sessions) {
            LocalDate date = LocalDate.ofEpochDay(s.epochDay);
            sheet.addRow(XlsxWriter.date(s.epochDay),
                    DAY_NAMES[date.getDayOfWeek().getValue() - 1],
                    clock(s.startMinute), clock(s.endMinute), hours(s.durationMinutes()),
                    s.subjectName, s.subjectCode, s.classCode, s.room, s.lecturer,
                    status(s.status), s.changeNote, s.note);
        }
        XlsxWriter.write(output, Collections.singletonList(sheet));
    }

    /** One row per class section (or per subject without one), with its session count and hours. */
    public static void writeSubjects(OutputStream output, List<SubjectEntity> subjects,
                                     List<ClassSectionEntity> classSections,
                                     List<ClassOccurrence> sessions) throws IOException {
        Map<Long, int[]> totalsBySection = new HashMap<>();
        for (ClassOccurrence s : sessions) {
            if (s.isCancelled()) {
                continue;
            }
            int[] totals = totalsBySection.get(s.classSectionId);
            if (totals == null) {
                totals = new int[2];
                totalsBySection.put(s.classSectionId, totals);
            }
            totals[0]++;
            totals[1] += s.durationMinutes();
        }

        List<SubjectEntity> sortedSubjects = new ArrayList<>(subjects);
        sortedSubjects.sort(Comparator.comparing((SubjectEntity e) -> nonNull(e.name).toLowerCase(Locale.ROOT))
                .thenComparingLong(e -> e.id));
        XlsxWriter.Sheet sheet = new XlsxWriter.Sheet("Môn học",
                new String[]{"Mã môn", "Tên môn", "Tín chỉ", "Giảng viên (môn)", "Lớp học phần",
                        "Giảng viên (lớp)", "Số buổi", "Tổng số giờ", "Ghi chú"},
                new double[]{12, 34, 9, 24, 14, 24, 10, 12, 30});
        for (SubjectEntity subject : sortedSubjects) {
            List<ClassSectionEntity> own = new ArrayList<>();
            for (ClassSectionEntity section : classSections) {
                if (section.subjectId == subject.id) {
                    own.add(section);
                }
            }
            own.sort(Comparator.comparing((ClassSectionEntity e) -> nonNull(e.classCode).toLowerCase(Locale.ROOT))
                    .thenComparingLong(e -> e.id));
            if (own.isEmpty()) {
                sheet.addRow(subject.code, subject.name, subject.credits, subject.defaultLecturer,
                        null, null, 0, hours(0), subject.note);
            }
            for (ClassSectionEntity section : own) {
                int[] totals = totalsBySection.get(section.id);
                int count = totals == null ? 0 : totals[0];
                int minutes = totals == null ? 0 : totals[1];
                sheet.addRow(subject.code, subject.name, subject.credits, subject.defaultLecturer,
                        section.classCode, section.lecturer, count, hours(minutes), subject.note);
            }
        }
        XlsxWriter.write(output, Collections.singletonList(sheet));
    }

    private static String status(int status) {
        switch (status) {
            case ClassOccurrence.STATUS_MODIFIED: return "Đã đổi lịch";
            case ClassOccurrence.STATUS_MAKEUP: return "Học bù";
            case ClassOccurrence.STATUS_CANCELLED: return "Đã hủy";
            default: return "Theo lịch";
        }
    }

    private static String clock(int minuteOfDay) {
        return String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private static Double hours(int minutes) {
        return Math.round(minutes / 60.0 * 100.0) / 100.0;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}