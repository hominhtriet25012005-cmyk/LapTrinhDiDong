package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;

public class ScheduleExceptionValidatorTest {
    private static final LocalDate ORIGINAL_MONDAY = LocalDate.of(2026, 3, 16);

    @Test
    public void normalizesValidSameDayModificationAndInheritsUnsetFields() {
        ScheduleExceptionEntity input = exception(ScheduleExceptionEntity.TYPE_MODIFY);
        input.startMinute = 600;
        input.endMinute = 660;
        input.room = " Room B2 ";
        input.note = "  đổi phòng  ";

        ScheduleExceptionEntity result = ScheduleExceptionValidator.normalizeAndValidate(
                input, schedule());

        assertEquals(8L, result.scheduleId);
        assertEquals(ORIGINAL_MONDAY.toEpochDay(), result.originalDateEpochDay);
        assertNull(result.replacementDateEpochDay);
        assertEquals(Integer.valueOf(600), result.startMinute);
        assertEquals(Integer.valueOf(660), result.endMinute);
        assertEquals("Room B2", result.room);
        assertEquals("đổi phòng", result.note);
        assertEquals(" Room B2 ", input.room);
    }

    @Test
    public void acceptsCancellationWithoutReplacementValues() {
        ScheduleExceptionEntity result = ScheduleExceptionValidator.normalizeAndValidate(
                exception(ScheduleExceptionEntity.TYPE_CANCEL), schedule());

        assertEquals(ScheduleExceptionEntity.TYPE_CANCEL, result.exceptionType);
        assertNull(result.replacementDateEpochDay);
        assertNull(result.startMinute);
        assertNull(result.endMinute);
        assertNull(result.room);
    }

    @Test
    public void makeupRequiresDifferentValidDateAndCanInheritOriginalTime() {
        ScheduleExceptionEntity input = exception(ScheduleExceptionEntity.TYPE_MAKEUP);
        input.replacementDateEpochDay = ORIGINAL_MONDAY.plusDays(1).toEpochDay();

        ScheduleExceptionEntity result = ScheduleExceptionValidator.normalizeAndValidate(
                input, schedule());

        assertEquals(ORIGINAL_MONDAY.plusDays(1).toEpochDay(),
                result.replacementDateEpochDay.longValue());
        assertNull(result.startMinute);
        assertNull(result.endMinute);
    }

    @Test
    public void rejectsWrongOriginalWeekdayAndDateOutsideScheduleValidity() {
        ScheduleExceptionEntity wrongWeekday = exception(ScheduleExceptionEntity.TYPE_CANCEL);
        wrongWeekday.originalDateEpochDay = ORIGINAL_MONDAY.plusDays(1).toEpochDay();
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleExceptionValidator.normalizeAndValidate(wrongWeekday, schedule()));

        ScheduleExceptionEntity outsideValidity = exception(ScheduleExceptionEntity.TYPE_CANCEL);
        outsideValidity.originalDateEpochDay = ORIGINAL_MONDAY.minusDays(14).toEpochDay();
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleExceptionValidator.normalizeAndValidate(outsideValidity, schedule()));
    }

    @Test
    public void rejectsInvalidTypeDateTimeAndTypeSpecificFields() {
        ScheduleExceptionEntity unknownType = exception("holiday");
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleExceptionValidator.normalizeAndValidate(unknownType, schedule()));

        ScheduleExceptionEntity invalidDate = exception(ScheduleExceptionEntity.TYPE_CANCEL);
        invalidDate.originalDateEpochDay = Long.MAX_VALUE;
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleExceptionValidator.normalizeAndValidate(invalidDate, schedule()));

        ScheduleExceptionEntity partialTime = exception(ScheduleExceptionEntity.TYPE_MODIFY);
        partialTime.startMinute = 600;
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleExceptionValidator.normalizeAndValidate(partialTime, schedule()));

        ScheduleExceptionEntity cancelWithOverride = exception(ScheduleExceptionEntity.TYPE_CANCEL);
        cancelWithOverride.room = "Room B2";
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleExceptionValidator.normalizeAndValidate(cancelWithOverride, schedule()));

        ScheduleExceptionEntity makeupWithoutDate = exception(ScheduleExceptionEntity.TYPE_MAKEUP);
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleExceptionValidator.normalizeAndValidate(makeupWithoutDate, schedule()));
    }

    private static ScheduleExceptionEntity exception(String type) {
        ScheduleExceptionEntity result = new ScheduleExceptionEntity();
        result.scheduleId = 8L;
        result.originalDateEpochDay = ORIGINAL_MONDAY.toEpochDay();
        result.exceptionType = type;
        return result;
    }

    private static ScheduleEntity schedule() {
        ScheduleEntity result = new ScheduleEntity();
        result.id = 8L;
        result.dayOfWeek = 1;
        result.startMinute = 540;
        result.endMinute = 600;
        result.validFromEpochDay = ORIGINAL_MONDAY.minusDays(7).toEpochDay();
        result.validToEpochDay = ORIGINAL_MONDAY.plusDays(21).toEpochDay();
        result.room = "Room A1";
        return result;
    }
}
