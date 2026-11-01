package com.example.demo.data.backup;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.validation.ScheduleExceptionValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts app data to and from the backup JSON text and validates a file before restore. */
public final class BackupCodec {
    public static final String APP_NAME = "UniSchedule";
    public static final int FORMAT_VERSION = 1;

    private BackupCodec() {
    }

    // ---- writing ----------------------------------------------------------------------

    public static String toJson(BackupData data) {
        StringBuilder out = new StringBuilder(4096);
        out.append("{\n");
        out.append("  \"app\": ").append(quote(APP_NAME)).append(",\n");
        out.append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n");
        out.append("  \"createdAtMillis\": ").append(data.createdAtMillis).append(",\n");

        out.append("  \"semesters\": [");
        boolean first = true;
        for (SemesterEntity e : data.semesters) {
            first = separator(out, first);
            out.append("{\"id\":").append(e.id)
                    .append(",\"name\":").append(quote(e.name))
                    .append(",\"academicYear\":").append(quote(e.academicYear))
                    .append(",\"startDateEpochDay\":").append(num(e.startDateEpochDay))
                    .append(",\"endDateEpochDay\":").append(num(e.endDateEpochDay))
                    .append(",\"isCurrent\":").append(e.isCurrent)
                    .append(",\"note\":").append(quote(e.note)).append('}');
        }
        out.append(first ? "],\n" : "\n  ],\n");

        out.append("  \"subjects\": [");
        first = true;
        for (SubjectEntity e : data.subjects) {
            first = separator(out, first);
            out.append("{\"id\":").append(e.id)
                    .append(",\"semesterId\":").append(e.semesterId)
                    .append(",\"code\":").append(quote(e.code))
                    .append(",\"name\":").append(quote(e.name))
                    .append(",\"credits\":").append(e.credits)
                    .append(",\"defaultLecturer\":").append(quote(e.defaultLecturer))
                    .append(",\"colorKey\":").append(quote(e.colorKey))
                    .append(",\"note\":").append(quote(e.note)).append('}');
        }
        out.append(first ? "],\n" : "\n  ],\n");

        out.append("  \"classSections\": [");
        first = true;
        for (ClassSectionEntity e : data.classSections) {
            first = separator(out, first);
            out.append("{\"id\":").append(e.id)
                    .append(",\"subjectId\":").append(e.subjectId)
                    .append(",\"classCode\":").append(quote(e.classCode))
                    .append(",\"lecturer\":").append(quote(e.lecturer))
                    .append(",\"note\":").append(quote(e.note)).append('}');
        }
        out.append(first ? "],\n" : "\n  ],\n");

        out.append("  \"schedules\": [");
        first = true;
        for (ScheduleEntity e : data.schedules) {
            first = separator(out, first);
            out.append("{\"id\":").append(e.id)
                    .append(",\"classSectionId\":").append(e.classSectionId)
                    .append(",\"dayOfWeek\":").append(e.dayOfWeek)
                    .append(",\"startMinute\":").append(e.startMinute)
                    .append(",\"endMinute\":").append(e.endMinute)
                    .append(",\"room\":").append(quote(e.room))
                    .append(",\"validFromEpochDay\":").append(e.validFromEpochDay)
                    .append(",\"validToEpochDay\":").append(e.validToEpochDay)
                    .append(",\"note\":").append(quote(e.note))
                    .append(",\"notificationEnabled\":").append(e.notificationEnabled)
                    .append(",\"reminderMinutesBefore\":").append(e.reminderMinutesBefore)
                    .append('}');
        }
        out.append(first ? "],\n" : "\n  ],\n");

        out.append("  \"exceptions\": [");
        first = true;
        for (ScheduleExceptionEntity e : data.exceptions) {
            first = separator(out, first);
            out.append("{\"id\":").append(e.id)
                    .append(",\"type\":").append(quote(e.type))
                    .append(",\"scheduleId\":").append(num(e.scheduleId))
                    .append(",\"classSectionId\":").append(e.classSectionId)
                    .append(",\"originalEpochDay\":").append(num(e.originalEpochDay))
                    .append(",\"newEpochDay\":").append(num(e.newEpochDay))
                    .append(",\"newStartMinute\":").append(num(e.newStartMinute))
                    .append(",\"newEndMinute\":").append(num(e.newEndMinute))
                    .append(",\"newRoom\":").append(quote(e.newRoom))
                    .append(",\"note\":").append(quote(e.note))
                    .append(",\"createdAtMillis\":").append(e.createdAtMillis)
                    .append('}');
        }
        out.append(first ? "]\n" : "\n  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static boolean separator(StringBuilder out, boolean first) {
        out.append(first ? "\n    " : ",\n    ");
        return false;
    }

    private static String num(Number value) {
        return value == null ? "null" : value.toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    // ---- reading and validation -------------------------------------------------------

    public static BackupData fromJson(String text) throws BackupFormatException {
        Object root;
        try {
            root = new Parser(text).parseDocument();
        } catch (IllegalArgumentException exception) {
            throw new BackupFormatException("File không phải là bản sao lưu hợp lệ (không đọc được nội dung).");
        }
        if (!(root instanceof Map)) {
            throw new BackupFormatException("File không phải là bản sao lưu UniSchedule.");
        }
        Map<String, Object> doc = asMap(root);
        if (!APP_NAME.equals(doc.get("app"))) {
            throw new BackupFormatException("File không phải là bản sao lưu UniSchedule.");
        }
        Object version = doc.get("formatVersion");
        if (!(version instanceof Long) || (Long) version != FORMAT_VERSION) {
            throw new BackupFormatException("Phiên bản bản sao lưu không được hỗ trợ.");
        }
        long createdAt = doc.get("createdAtMillis") instanceof Long ? (Long) doc.get("createdAtMillis") : 0L;

        List<SemesterEntity> semesters = new ArrayList<>();
        for (Map<String, Object> o : rows(doc, "semesters")) {
            SemesterEntity e = new SemesterEntity();
            e.id = reqLong(o, "id", "học kỳ");
            e.name = reqString(o, "name", "học kỳ");
            e.academicYear = optString(o, "academicYear", "học kỳ");
            e.startDateEpochDay = optLong(o, "startDateEpochDay", "học kỳ");
            e.endDateEpochDay = optLong(o, "endDateEpochDay", "học kỳ");
            e.isCurrent = reqBool(o, "isCurrent", "học kỳ");
            e.note = optString(o, "note", "học kỳ");
            semesters.add(e);
        }

        List<SubjectEntity> subjects = new ArrayList<>();
        for (Map<String, Object> o : rows(doc, "subjects")) {
            SubjectEntity e = new SubjectEntity();
            e.id = reqLong(o, "id", "môn học");
            e.semesterId = reqLong(o, "semesterId", "môn học");
            e.code = reqString(o, "code", "môn học");
            e.name = reqString(o, "name", "môn học");
            e.credits = reqInt(o, "credits", "môn học");
            e.defaultLecturer = optString(o, "defaultLecturer", "môn học");
            e.colorKey = optString(o, "colorKey", "môn học");
            e.note = optString(o, "note", "môn học");
            subjects.add(e);
        }

        List<ClassSectionEntity> sections = new ArrayList<>();
        for (Map<String, Object> o : rows(doc, "classSections")) {
            ClassSectionEntity e = new ClassSectionEntity();
            e.id = reqLong(o, "id", "lớp học phần");
            e.subjectId = reqLong(o, "subjectId", "lớp học phần");
            e.classCode = optString(o, "classCode", "lớp học phần");
            e.lecturer = optString(o, "lecturer", "lớp học phần");
            e.note = optString(o, "note", "lớp học phần");
            sections.add(e);
        }

        List<ScheduleEntity> schedules = new ArrayList<>();
        for (Map<String, Object> o : rows(doc, "schedules")) {
            ScheduleEntity e = new ScheduleEntity();
            e.id = reqLong(o, "id", "lịch học");
            e.classSectionId = reqLong(o, "classSectionId", "lịch học");
            e.dayOfWeek = reqInt(o, "dayOfWeek", "lịch học");
            e.startMinute = reqInt(o, "startMinute", "lịch học");
            e.endMinute = reqInt(o, "endMinute", "lịch học");
            e.room = optString(o, "room", "lịch học");
            e.validFromEpochDay = reqLong(o, "validFromEpochDay", "lịch học");
            e.validToEpochDay = reqLong(o, "validToEpochDay", "lịch học");
            e.note = optString(o, "note", "lịch học");
            e.notificationEnabled = reqBool(o, "notificationEnabled", "lịch học");
            e.reminderMinutesBefore = reqInt(o, "reminderMinutesBefore", "lịch học");
            schedules.add(e);
        }

        List<ScheduleExceptionEntity> exceptions = new ArrayList<>();
        for (Map<String, Object> o : rows(doc, "exceptions")) {
            ScheduleExceptionEntity e = new ScheduleExceptionEntity();
            e.id = reqLong(o, "id", "thay đổi buổi học");
            e.type = reqString(o, "type", "thay đổi buổi học");
            e.scheduleId = optLong(o, "scheduleId", "thay đổi buổi học");
            e.classSectionId = reqLong(o, "classSectionId", "thay đổi buổi học");
            e.originalEpochDay = optLong(o, "originalEpochDay", "thay đổi buổi học");
            e.newEpochDay = optLong(o, "newEpochDay", "thay đổi buổi học");
            e.newStartMinute = optInt(o, "newStartMinute", "thay đổi buổi học");
            e.newEndMinute = optInt(o, "newEndMinute", "thay đổi buổi học");
            e.newRoom = optString(o, "newRoom", "thay đổi buổi học");
            e.note = optString(o, "note", "thay đổi buổi học");
            e.createdAtMillis = reqLong(o, "createdAtMillis", "thay đổi buổi học");
            exceptions.add(e);
        }

        validate(semesters, subjects, sections, schedules, exceptions);
        return new BackupData(createdAt, semesters, subjects, sections, schedules, exceptions);
    }

    private static void validate(List<SemesterEntity> semesters, List<SubjectEntity> subjects,
                                 List<ClassSectionEntity> sections, List<ScheduleEntity> schedules,
                                 List<ScheduleExceptionEntity> exceptions) throws BackupFormatException {
        Set<Long> semesterIds = new HashSet<>();
        int currentCount = 0;
        for (SemesterEntity e : semesters) {
            requireNewId(semesterIds, e.id, "học kỳ");
            if (isBlank(e.name)) {
                throw new BackupFormatException("Có học kỳ không có tên.");
            }
            if (e.startDateEpochDay != null && e.endDateEpochDay != null
                    && e.startDateEpochDay > e.endDateEpochDay) {
                throw new BackupFormatException("Học kỳ \"" + e.name + "\" có ngày bắt đầu sau ngày kết thúc.");
            }
            if (e.isCurrent) {
                currentCount++;
            }
        }
        if (currentCount > 1) {
            throw new BackupFormatException("Có nhiều hơn một học kỳ hiện tại.");
        }

        Set<Long> subjectIds = new HashSet<>();
        Set<String> subjectKeys = new HashSet<>();
        for (SubjectEntity e : subjects) {
            requireNewId(subjectIds, e.id, "môn học");
            if (!semesterIds.contains(e.semesterId)) {
                throw new BackupFormatException("Môn học \"" + e.name + "\" thuộc học kỳ không tồn tại.");
            }
            if (isBlank(e.code) || isBlank(e.name)) {
                throw new BackupFormatException("Có môn học thiếu mã hoặc tên.");
            }
            if (e.credits < 0) {
                throw new BackupFormatException("Môn học \"" + e.name + "\" có số tín chỉ âm.");
            }
            if (!subjectKeys.add(e.semesterId + "|" + e.code)) {
                throw new BackupFormatException("Mã môn \"" + e.code + "\" bị trùng trong cùng học kỳ.");
            }
        }

        Set<Long> sectionIds = new HashSet<>();
        Map<Long, ClassSectionEntity> sectionsById = new HashMap<>();
        for (ClassSectionEntity e : sections) {
            requireNewId(sectionIds, e.id, "lớp học phần");
            if (!subjectIds.contains(e.subjectId)) {
                throw new BackupFormatException("Có lớp học phần thuộc môn học không tồn tại.");
            }
            sectionsById.put(e.id, e);
        }

        Set<Long> scheduleIds = new HashSet<>();
        Map<Long, ScheduleEntity> schedulesById = new HashMap<>();
        for (ScheduleEntity e : schedules) {
            requireNewId(scheduleIds, e.id, "lịch học");
            if (!sectionIds.contains(e.classSectionId)) {
                throw new BackupFormatException("Có lịch học thuộc lớp học phần không tồn tại.");
            }
            if (e.dayOfWeek < 1 || e.dayOfWeek > 7) {
                throw new BackupFormatException("Có lịch học với thứ không hợp lệ.");
            }
            if (e.startMinute < 0 || e.startMinute >= 24 * 60
                    || e.endMinute <= e.startMinute || e.endMinute > 24 * 60) {
                throw new BackupFormatException("Có lịch học với giờ bắt đầu/kết thúc không hợp lệ.");
            }
            if (e.validFromEpochDay > e.validToEpochDay) {
                throw new BackupFormatException("Có lịch học với ngày bắt đầu sau ngày kết thúc.");
            }
            if (e.reminderMinutesBefore < 0) {
                throw new BackupFormatException("Có lịch học với thời gian nhắc không hợp lệ.");
            }
            schedulesById.put(e.id, e);
        }

        Set<Long> exceptionIds = new HashSet<>();
        Set<String> occurrenceKeys = new HashSet<>();
        for (ScheduleExceptionEntity e : exceptions) {
            requireNewId(exceptionIds, e.id, "thay đổi buổi học");
            if (!sectionIds.contains(e.classSectionId)) {
                throw new BackupFormatException("Có thay đổi buổi học thuộc lớp học phần không tồn tại.");
            }
            try {
                ScheduleExceptionValidator.validate(e.type, e.scheduleId, e.classSectionId,
                        e.originalEpochDay, e.newEpochDay, e.newStartMinute, e.newEndMinute, e.newRoom);
                if (e.scheduleId != null) {
                    ScheduleEntity schedule = schedulesById.get(e.scheduleId);
                    if (schedule == null) {
                        throw new BackupFormatException("Có thay đổi buổi học thuộc lịch học không tồn tại.");
                    }
                    if (schedule.classSectionId != e.classSectionId) {
                        throw new BackupFormatException("Có thay đổi buổi học không khớp lớp học phần của lịch.");
                    }
                    ScheduleExceptionValidator.validateOccurrence(schedule.dayOfWeek,
                            schedule.validFromEpochDay, schedule.validToEpochDay, e.originalEpochDay);
                    if (!occurrenceKeys.add(e.scheduleId + "|" + e.originalEpochDay)) {
                        throw new BackupFormatException("Có hai thay đổi cho cùng một buổi học.");
                    }
                }
            } catch (IllegalArgumentException exception) {
                throw new BackupFormatException("Có thay đổi buổi học không hợp lệ: " + exception.getMessage());
            }
        }
    }

    private static void requireNewId(Set<Long> ids, long id, String what) throws BackupFormatException {
        if (id <= 0 || !ids.add(id)) {
            throw new BackupFormatException("Mã (id) của " + what + " không hợp lệ hoặc bị trùng.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ---- typed access to parsed JSON ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> doc, String key)
            throws BackupFormatException {
        Object value = doc.get(key);
        if (!(value instanceof List)) {
            throw new BackupFormatException("Bản sao lưu thiếu mục \"" + key + "\".");
        }
        for (Object item : (List<Object>) value) {
            if (!(item instanceof Map)) {
                throw new BackupFormatException("Mục \"" + key + "\" có dữ liệu không hợp lệ.");
            }
        }
        return (List<Map<String, Object>>) value;
    }

    private static BackupFormatException bad(String key, String where) {
        return new BackupFormatException("Thiếu hoặc sai dữ liệu \"" + key + "\" của " + where + ".");
    }

    private static long reqLong(Map<String, Object> o, String key, String where) throws BackupFormatException {
        Object value = o.get(key);
        if (!(value instanceof Long)) {
            throw bad(key, where);
        }
        return (Long) value;
    }

    private static Long optLong(Map<String, Object> o, String key, String where) throws BackupFormatException {
        Object value = o.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Long)) {
            throw bad(key, where);
        }
        return (Long) value;
    }

    private static int reqInt(Map<String, Object> o, String key, String where) throws BackupFormatException {
        long value = reqLong(o, key, where);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw bad(key, where);
        }
        return (int) value;
    }

    private static Integer optInt(Map<String, Object> o, String key, String where) throws BackupFormatException {
        Long value = optLong(o, key, where);
        if (value == null) {
            return null;
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw bad(key, where);
        }
        return value.intValue();
    }

    private static boolean reqBool(Map<String, Object> o, String key, String where) throws BackupFormatException {
        Object value = o.get(key);
        if (!(value instanceof Boolean)) {
            throw bad(key, where);
        }
        return (Boolean) value;
    }

    private static String reqString(Map<String, Object> o, String key, String where) throws BackupFormatException {
        Object value = o.get(key);
        if (!(value instanceof String)) {
            throw bad(key, where);
        }
        return (String) value;
    }

    private static String optString(Map<String, Object> o, String key, String where) throws BackupFormatException {
        Object value = o.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw bad(key, where);
        }
        return (String) value;
    }

    // ---- minimal JSON parser ------------------------------------------------------------

    /** Parses objects, arrays, strings, integers, booleans and null. Throws IllegalArgumentException. */
    private static final class Parser {
        private static final int MAX_DEPTH = 16;
        private final String text;
        private int pos;

        Parser(String text) {
            if (text == null) {
                throw new IllegalArgumentException("empty");
            }
            this.text = text.startsWith("\uFEFF") ? text.substring(1) : text;
        }

        Object parseDocument() {
            Object value = parseValue(0);
            skipWhitespace();
            if (pos != text.length()) {
                throw new IllegalArgumentException("trailing data");
            }
            return value;
        }

        private Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("too deep");
            }
            skipWhitespace();
            if (pos >= text.length()) {
                throw new IllegalArgumentException("unexpected end");
            }
            char c = text.charAt(pos);
            if (c == '{') {
                return parseObject(depth);
            } else if (c == '[') {
                return parseArray(depth);
            } else if (c == '"') {
                return parseString();
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            } else if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            } else if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("unexpected character");
        }

        private Map<String, Object> parseObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new IllegalArgumentException("key expected");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                map.put(key, parseValue(depth + 1));
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("comma expected");
                }
            }
        }

        private List<Object> parseArray(int depth) {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue(depth + 1));
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("comma expected");
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    char escape = next();
                    switch (escape) {
                        case '"': out.append('"'); break;
                        case '\\': out.append('\\'); break;
                        case '/': out.append('/'); break;
                        case 'b': out.append('\b'); break;
                        case 'f': out.append('\f'); break;
                        case 'n': out.append('\n'); break;
                        case 'r': out.append('\r'); break;
                        case 't': out.append('\t'); break;
                        case 'u':
                            if (pos + 4 > text.length()) {
                                throw new IllegalArgumentException("bad unicode escape");
                            }
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("bad escape");
                    }
                } else if (c < 0x20) {
                    throw new IllegalArgumentException("control character in string");
                } else {
                    out.append(c);
                }
            }
        }

        private Long parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            int digits = 0;
            while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                pos++;
                digits++;
            }
            if (digits == 0 || digits > 18) {
                throw new IllegalArgumentException("bad number");
            }
            if (pos < text.length() && (text.charAt(pos) == '.' || text.charAt(pos) == 'e'
                    || text.charAt(pos) == 'E')) {
                throw new IllegalArgumentException("only integers are allowed");
            }
            return Long.parseLong(text.substring(start, pos));
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("unexpected end");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char expected) {
            if (next() != expected) {
                throw new IllegalArgumentException("'" + expected + "' expected");
            }
        }
    }
}