package com.example.demo.data.validation;

import com.example.demo.data.local.entity.SubjectEntity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SubjectValidatorTest {
    @Test
    public void normalizesAndValidatesEntityAtPersistenceBoundary() {
        SubjectEntity input = new SubjectEntity();
        input.id = 7L;
        input.semesterId = 3L;
        input.code = " cs101 ";
        input.name = " Lập trình Java ";
        input.credits = 4;
        input.defaultLecturer = " ThS. An ";
        input.colorKey = " ";
        input.note = " Ghi chú ";

        SubjectEntity normalized = SubjectValidator.normalizeAndValidate(input);

        assertEquals(7L, normalized.id);
        assertEquals(3L, normalized.semesterId);
        assertEquals("CS101", normalized.code);
        assertEquals("Lập trình Java", normalized.name);
        assertEquals("ThS. An", normalized.defaultLecturer);
        assertEquals("blue", normalized.colorKey);
        assertEquals("Ghi chú", normalized.note);
        assertEquals(" cs101 ", input.code);
    }

    @Test
    public void rejectsMissingRequiredValuesAndNegativeCredits() {
        SubjectEntity missingSemester = validSubject();
        missingSemester.semesterId = 0L;
        assertThrows(IllegalArgumentException.class,
                () -> SubjectValidator.normalizeAndValidate(missingSemester));

        SubjectEntity blankCode = validSubject();
        blankCode.code = " ";
        assertThrows(IllegalArgumentException.class,
                () -> SubjectValidator.normalizeAndValidate(blankCode));

        SubjectEntity blankName = validSubject();
        blankName.name = " ";
        assertThrows(IllegalArgumentException.class,
                () -> SubjectValidator.normalizeAndValidate(blankName));

        SubjectEntity negativeCredits = validSubject();
        negativeCredits.credits = -1;
        assertThrows(IllegalArgumentException.class,
                () -> SubjectValidator.normalizeAndValidate(negativeCredits));
    }

    private static SubjectEntity validSubject() {
        return new SubjectEntity(1L, "CS101", "Lập trình Java", 3, "", null, "");
    }
}