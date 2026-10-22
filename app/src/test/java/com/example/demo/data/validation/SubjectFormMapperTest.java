package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SubjectEntity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SubjectFormMapperTest {

    @Test
    public void mapsAndNormalizesSubjectFormValues() {
        SubjectEntity subject = SubjectFormMapper.toEntity(
                8L, 3L, " int2208 ", " Lập trình Web ", "4", " ThS. An ", null, " ghi chú ");

        assertEquals(8L, subject.id);
        assertEquals(3L, subject.semesterId);
        assertEquals("INT2208", subject.code);
        assertEquals("Lập trình Web", subject.name);
        assertEquals(4, subject.credits);
        assertEquals("ThS. An", subject.defaultLecturer);
        assertEquals("blue", subject.colorKey);
        assertEquals("ghi chú", subject.note);
    }

    @Test
    public void blankOptionalCreditsAndTextBecomeSafeDefaults() {
        SubjectEntity subject = SubjectFormMapper.toEntity(
                null, 1L, "CS101", "Thuật toán", "", "  ", "green", null);

        assertEquals(0, subject.credits);
        assertEquals("", subject.defaultLecturer);
        assertEquals("green", subject.colorKey);
        assertEquals("", subject.note);
    }

    @Test
    public void rejectsRequiredFieldsAndMissingSemester() {
        assertInvalid(null, 1L, "CS101", "   ", "3", "Tên môn học là bắt buộc.");
        assertInvalid(null, 1L, "  ", "Thuật toán", "3", "Mã môn học là bắt buộc.");
        assertInvalid(null, 0L, "CS101", "Thuật toán", "3", "Vui lòng chọn học kỳ.");
    }

    @Test
    public void rejectsCreditsThatAreNotNonNegativeIntegers() {
        assertInvalid(null, 1L, "CS101", "Thuật toán", "ba", "Số tín chỉ phải là số nguyên không âm.");
        assertInvalid(null, 1L, "CS101", "Thuật toán", "-1", "Số tín chỉ phải là số nguyên không âm.");
    }

    private static void assertInvalid(Long id, long semesterId, String code, String name,
                                      String credits, String expectedMessage) {
        try {
            SubjectFormMapper.toEntity(id, semesterId, code, name, credits, "", "blue", "");
            fail("Expected invalid subject form.");
        } catch (IllegalArgumentException exception) {
            assertEquals(expectedMessage, exception.getMessage());
        }
    }
}
