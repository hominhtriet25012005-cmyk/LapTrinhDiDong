package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SemesterEntity;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SemesterFormMapperTest {

    @Test
    public void mapsFormFieldsAndDerivesAcademicYearFromStartDate() {
        SemesterEntity semester = SemesterFormMapper.toEntity(
                12L, "  Học kỳ 1  ", "16/03/2026", "30/06/2026", "  Ghi chú  ", true);

        assertEquals(12L, semester.id);
        assertEquals("Học kỳ 1", semester.name);
        assertEquals("2026–2027", semester.academicYear);
        assertEquals(LocalDate.of(2026, 3, 16).toEpochDay(), semester.startDateEpochDay.longValue());
        assertEquals(LocalDate.of(2026, 6, 30).toEpochDay(), semester.endDateEpochDay.longValue());
        assertEquals("Ghi chú", semester.note);
        assertTrue(semester.isCurrent);
    }

    @Test
    public void allowsOptionalDatesAndLeavesAcademicYearUnsetWhenNoStartDateExists() {
        SemesterEntity semester = SemesterFormMapper.toEntity(
                null, "Học kỳ hè", "", "", "", false);

        assertNull(semester.startDateEpochDay);
        assertNull(semester.endDateEpochDay);
        assertNull(semester.academicYear);
        assertFalse(semester.isCurrent);
    }

    @Test
    public void rejectsBlankSemesterName() {
        assertInvalidName(null, "Tên học kỳ là bắt buộc.");
        assertInvalidName("   ", "Tên học kỳ là bắt buộc.");
    }

    @Test
    public void rejectsImpossibleDateRatherThanLenientlyNormalizingIt() {
        assertInvalidStartDate("31/02/2026", "Ngày bắt đầu không hợp lệ: 31/02/2026.");
    }

    @Test
    public void rejectsEndDateBeforeStartDate() {
        try {
            SemesterFormMapper.toEntity(null, "Học kỳ 1", "30/06/2026", "16/03/2026", "", false);
            fail("Expected an invalid semester form.");
        } catch (IllegalArgumentException exception) {
            assertEquals("Ngày kết thúc không được trước ngày bắt đầu.", exception.getMessage());
        }
    }

    private static void assertInvalidName(String name, String expectedMessage) {
        try {
            SemesterFormMapper.toEntity(null, name, "16/03/2026", "30/06/2026", "", false);
            fail("Expected an invalid semester form.");
        } catch (IllegalArgumentException exception) {
            assertEquals(expectedMessage, exception.getMessage());
        }
    }

    private static void assertInvalidStartDate(String startDate, String expectedMessage) {
        try {
            SemesterFormMapper.toEntity(null, "Học kỳ 1", startDate, "30/06/2026", "", false);
            fail("Expected an invalid semester form.");
        } catch (IllegalArgumentException exception) {
            assertEquals(expectedMessage, exception.getMessage());
        }
    }
}
