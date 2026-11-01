package com.example.demo.data.search;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes searchable text so Vietnamese queries work with or without tone marks. */
public final class SearchTextNormalizer {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private SearchTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String vietnameseD = value.replace('Đ', 'D').replace('đ', 'd');
        String decomposed = Normalizer.normalize(vietnameseD, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    public static boolean contains(String text, String query) {
        String normalizedQuery = normalize(query);
        return normalizedQuery.isEmpty() || normalize(text).contains(normalizedQuery);
    }

    public static boolean matchesAny(String query, String... fields) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        if (fields == null) {
            return false;
        }
        for (String field : fields) {
            if (normalize(field).contains(normalizedQuery)) {
                return true;
            }
        }
        return false;
    }
}