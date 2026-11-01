package com.example.demo.data.search;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchTextNormalizerTest {
    @Test
    public void removesVietnameseDiacriticsAndNormalizesCase() {
        assertEquals("dien hoc", SearchTextNormalizer.normalize("ĐIỆN HỌC"));
        assertEquals("lap trinh", SearchTextNormalizer.normalize("  Lập trình  "));
    }

    @Test
    public void matchesAccentedAndUnaccentedFormsInEitherDirection() {
        assertTrue(SearchTextNormalizer.contains("Lập trình cơ sở dữ liệu", "lap trinh"));
        assertTrue(SearchTextNormalizer.contains("lap trinh co so du lieu", "LẬP TRÌNH"));
        assertFalse(SearchTextNormalizer.contains("Mạng máy tính", "lap trinh"));
    }

    @Test
    public void nullAndBlankQueriesMatchAllText() {
        assertTrue(SearchTextNormalizer.contains("Mạng máy tính", null));
        assertTrue(SearchTextNormalizer.contains("Mạng máy tính", "   "));
        assertFalse(SearchTextNormalizer.contains(null, "mang"));
    }
}