package com.example.demo.data.backup;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.validation.ScheduleConflictValidator;
import com.example.demo.data.validation.ScheduleExceptionConflictValidator;
import com.example.demo.data.validation.ScheduleExceptionValidator;
import com.example.demo.data.validation.ScheduleValidator;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates backup metadata, row values, IDs and all parent-child references before restore. */
public final class BackupValidator {
    public static final int MAX_ENTITY_COUNT = 50_000;
    private static final Pattern ACADEMIC_YEAR_PATTERN =
            Pattern.compile("^(\\d{4})\\s*[-–—]\\s*(\\d{4})$");

    private BackupValidator() {
    }

    public static void validate(BackupData data) {
        if (data == null) {
            throw invalid("Tệp sao lưu không có dữ liệu.");
        }
        if (!BackupData.APPLICATION_ID.equals(data.appId)) {
            throw invalid("Tệp sao lưu không thuộc ứng dụng này.");
        }
        if (data.formatVersion != BackupData.CURRENT_FORMAT_VERSION) {
            throw invalid("Phiên bản tệp sao lưu không được hỗ trợ.");
        }
        if (data.createdAtEpochMillis <= 0L) {
            throw invalid("Thời điểm tạo tệp sao lưu không hợp lệ.");
        }
        if (data.semesters == null || data.subjects == null || data.classSections == null
                || data.schedules == null || data.scheduleExceptions == null) {
            throw invalid("Tệp sao lưu thiếu một hoặc nhiều danh sách dữ liệu.");
        }
        long rowCount = (long) data.semesters.size() + data.subjects.size()
                + data.classSections.size() + data.schedules.size()
                + data.scheduleExceptions.size();
        if (rowCount > MAX_ENTITY_COUNT) {
            throw invalid("Tệp sao lưu có quá nhiều bản ghi.");
        }

        Map<Long, SemesterEntity> semesters = validateSemesters(data);
        Map<Long, SubjectEntity> subjects = validateSubjects(data, semesters);
        Map<Long, ClassSectionEntity> classSections = validateClassSections(data, subjects);
        Map<Long, ScheduleEntity> schedules = validateSchedules(data, classSections);
        validateScheduleExceptions(data, schedules);
    }

    private static Map<Long, SemesterEntity> validateSemesters(BackupData data) {
        Map<Long, SemesterEntity> result = new HashMap<>();
        int currentCount = 0;
        for (SemesterEntity semester : data.semesters) {
            if (semester == null || semester.id <= 0L
                    || result.put(semester.id, semester) != null) {
                throw invalid("Học kỳ có ID trống hoặc bị trùng.");
            }
            if (semester.isCurrent && ++currentCount > 1) {
                throw invalid("Tệp sao lưu đánh dấu nhiều học kỳ hiện tại.");
            }
            validateSemesterText(semester);
            validateNullableDate(semester.startDateEpochDay, "Ngày bắt đầu học kỳ");
            validateNullableDate(semester.endDateEpochDay, "Ngày kết thúc học kỳ");
            ScheduleValidator.validateSemesterDates(
                    semester.startDateEpochDay, semester.endDateEpochDay);
        }
        return result;
    }

    private static Map<Long, SubjectEntity> validateSubjects(
            BackupData data, Map<Long, SemesterEntity> semesters) {
        Map<Long, SubjectEntity> result = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (SubjectEntity subject : data.subjects) {
            if (subject == null || subject.id <= 0L
                    || result.put(subject.id, subject) != null) {
                throw invalid("Môn học có ID trống hoặc bị trùng.");
            }
            if (!semesters.containsKey(subject.semesterId)) {
                throw invalid("Môn học tham chiếu đến học kỳ không tồn tại.");
            }
            if (normalized(subject.name).isEmpty() || normalized(subject.code).isEmpty()) {
                throw invalid("Môn học thiếu tên hoặc mã môn.");
            }
            if (subject.credits < 0) {
                throw invalid("Số tín chỉ không hợp lệ.");
            }
            String code = normalized(subject.code);
            if (!code.isEmpty() && !codes.add(subject.semesterId + "|" + code)) {
                throw invalid("Mã môn bị trùng trong cùng học kỳ.");
            }
        }
        return result;
    }

    private static Map<Long, ClassSectionEntity> validateClassSections(
            BackupData data, Map<Long, SubjectEntity> subjects) {
        Map<Long, ClassSectionEntity> result = new HashMap<>();
        Set<String> classCodes = new HashSet<>();
        for (ClassSectionEntity classSection : data.classSections) {
            if (classSection == null || classSection.id <= 0L
                    || result.put(classSection.id, classSection) != null) {
                throw invalid("Lớp học phần có ID trống hoặc bị trùng.");
            }
            if (!subjects.containsKey(classSection.subjectId)) {
                throw invalid("Lớp học phần tham chiếu đến môn không tồn tại.");
            }
            if (normalized(classSection.classCode).isEmpty()) {
                throw invalid("Lớp học phần thiếu mã lớp.");
            }
            String code = normalized(classSection.classCode);
            if (!code.isEmpty()
                    && !classCodes.add(classSection.subjectId + "|" + code)) {
                throw invalid("Mã lớp học phần bị trùng trong cùng môn.");
            }
        }
        return result;
    }

    private static Map<Long, ScheduleEntity> validateSchedules(
            BackupData data, Map<Long, ClassSectionEntity> classSections) {
        Map<Long, ScheduleEntity> result = new HashMap<>();
        for (ScheduleEntity schedule : data.schedules) {
            if (schedule == null || schedule.id <= 0L
                    || result.put(schedule.id, schedule) != null) {
                throw invalid("Lịch học có ID trống hoặc bị trùng.");
            }
            if (!classSections.containsKey(schedule.classSectionId)) {
                throw invalid("Lịch học tham chiếu đến lớp học phần không tồn tại.");
            }
            try {
                ScheduleValidator.validateSchedule(schedule.dayOfWeek, schedule.startMinute,
                        schedule.endMinute, schedule.validFromEpochDay,
                        schedule.validToEpochDay);
                validateDate(schedule.validFromEpochDay, "Ngày bắt đầu áp dụng lịch");
                validateDate(schedule.validToEpochDay, "Ngày kết thúc áp dụng lịch");
            } catch (IllegalArgumentException exception) {
                throw invalid("Thông tin ngày hoặc giờ của lịch học không hợp lệ.", exception);
            }
            if (schedule.reminderMinutesBefore < 0) {
                throw invalid("Thời gian nhắc lịch không hợp lệ.");
            }
        }
        for (ScheduleEntity schedule : data.schedules) {
            if (ScheduleConflictValidator.hasConflict(schedule, data.schedules)) {
                throw invalid("Tệp sao lưu có các lịch học lặp bị trùng giờ.");
            }
        }
        return result;
    }

    private static void validateScheduleExceptions(
            BackupData data, Map<Long, ScheduleEntity> schedules) {
        Set<Long> ids = new HashSet<>();
        Set<String> originalOccurrences = new HashSet<>();
        for (ScheduleExceptionEntity exception : data.scheduleExceptions) {
            if (exception == null || exception.id <= 0L || !ids.add(exception.id)) {
                throw invalid("Ngoại lệ lịch học có ID trống hoặc bị trùng.");
            }
            ScheduleEntity schedule = schedules.get(exception.scheduleId);
            if (schedule == null) {
                throw invalid("Ngoại lệ tham chiếu đến lịch học không tồn tại.");
            }
            String occurrenceKey = exception.scheduleId + "|"
                    + exception.originalDateEpochDay;
            if (!originalOccurrences.add(occurrenceKey)) {
                throw invalid("Một buổi học có nhiều ngoại lệ trùng nhau.");
            }
            try {
                ScheduleExceptionEntity normalized =
                        ScheduleExceptionValidator.normalizeAndValidate(exception, schedule);
                if (!normalized.exceptionType.equals(exception.exceptionType)) {
                    throw invalid("Loại ngoại lệ lịch học phải dùng giá trị chuẩn.");
                }
            } catch (IllegalArgumentException validationError) {
                throw invalid("Thông tin ngoại lệ lịch học không hợp lệ.", validationError);
            }
        }
        for (ScheduleExceptionEntity exception : data.scheduleExceptions) {
            if (ScheduleExceptionConflictValidator.hasConflict(
                    exception, data.schedules, data.scheduleExceptions)) {
                throw invalid("Tệp sao lưu có buổi đổi/học bù bị trùng giờ.");
            }
        }
    }

    private static void validateNullableDate(Long epochDay, String label) {
        if (epochDay != null) {
            validateDate(epochDay, label);
        }
    }

    private static void validateSemesterText(SemesterEntity semester) {
        if (normalized(semester.name).isEmpty() || semester.academicYear == null) {
            throw invalid("Học kỳ thiếu tên hoặc năm học.");
        }
        Matcher matcher = ACADEMIC_YEAR_PATTERN.matcher(semester.academicYear.trim());
        if (!matcher.matches()
                || Integer.parseInt(matcher.group(2))
                != Integer.parseInt(matcher.group(1)) + 1) {
            throw invalid("Năm học trong tệp sao lưu không hợp lệ.");
        }
    }

    private static void validateDate(long epochDay, String label) {
        try {
            LocalDate.ofEpochDay(epochDay);
        } catch (DateTimeException exception) {
            throw invalid(label + " không hợp lệ.", exception);
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }
}
