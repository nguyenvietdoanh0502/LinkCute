package com.hadilao.be.modules.place.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class PlaceTextNormalizer {

    private PlaceTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static String joinNormalized(String... values) {
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(PlaceTextNormalizer::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }
}
