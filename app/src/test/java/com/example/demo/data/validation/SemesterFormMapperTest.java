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
    public void mapsExplicitAcademicYearIndependentlyFromStartDate() {
        SemesterEntity semester = SemesterFormMapper.toEntity(
                12L, "  HK 2 NH  ", " 2025-2026 ", "16/03/2026", "30/06/2026", "  Ghi chú  ", true);

        assertEquals(12L, semester.id);
        assertEquals("HK 2 NH", semester.name);
        assertEquals("2025–2026", semester.academicYear);
        assertEquals(LocalDate.of(2026, 3, 16).toEpochDay(), semester.startDateEpochDay.longValue());
        assertEquals(LocalDate.of(2026, 6, 30).toEpochDay(), semester.endDateEpochDay.longValue());
        assertEquals("Ghi chú", semester.note);
        assertTrue(semester.isCurrent);
    }

    @Test
    public void acceptsOptionalDatesWhenAcademicYearIsProvided() {
        SemesterEntity semester = SemesterFormMapper.toEntity(
                null, "HK 3 NH", "2025–2026", "", "", "", false);

        assertNull(semester.startDateEpochDay);
        assertNull(semester.endDateEpochDay);
        assertEquals("2025–2026", semester.academicYear);
        assertFalse(semester.isCurrent);
    }

    @Test
    public void rejectsBlankSemesterName() {
        assertInvalidForm(null, "2025-2026", "Tên học kỳ là bắt buộc.");
        assertInvalidForm("   ", "2025-2026", "Tên học kỳ là bắt buộc.");
    }

    @Test
    public void requiresAcademicYear() {
        assertInvalidForm("HK 1 NH", "", "Năm học là bắt buộc.");
        assertInvalidForm("HK 1 NH", null, "Năm học là bắt buộc.");
    }

    @Test
    public void rejectsMalformedOrNonconsecutiveAcademicYear() {
        assertInvalidForm("HK 1 NH", "2025/2026", "Năm học không hợp lệ. Nhập theo dạng 2025-2026.");
        assertInvalidForm("HK 1 NH", "2025-2027", "Năm học không hợp lệ. Nhập theo dạng 2025-2026.");
    }

    @Test
    public void rejectsImpossibleDateRatherThanLenientlyNormalizingIt() {
        try {
            SemesterFormMapper.toEntity(null, "HK 1 NH", "2025-2026", "31/02/2026", "30/06/2026", "", false);
            fail("Expected an invalid semester form.");
        } catch (IllegalArgumentException exception) {
            assertEquals("Ngày bắt đầu không hợp lệ: 31/02/2026.", exception.getMessage());
        }
    }

    @Test
    public void rejectsEndDateBeforeStartDate() {
        try {
            SemesterFormMapper.toEntity(null, "HK 1 NH", "2025-2026", "30/06/2026", "16/03/2026", "", false);
            fail("Expected an invalid semester form.");
        } catch (IllegalArgumentException exception) {
            assertEquals("Ngày kết thúc không được trước ngày bắt đầu.", exception.getMessage());
        }
    }

    private static void assertInvalidForm(String name, String academicYear, String expectedMessage) {
        try {
            SemesterFormMapper.toEntity(null, name, academicYear, "16/03/2026", "30/06/2026", "", false);
            fail("Expected an invalid semester form.");
        } catch (IllegalArgumentException exception) {
            assertEquals(expectedMessage, exception.getMessage());
        }
    }
}
