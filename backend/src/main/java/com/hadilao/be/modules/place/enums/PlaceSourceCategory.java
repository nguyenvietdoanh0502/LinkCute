package com.hadilao.be.modules.place.enums;

import java.util.Locale;

public enum PlaceSourceCategory {
    RESTAURANT("Restaurant", PlaceCategory.FOOD),
    CAFE("Cafe", PlaceCategory.CAFE),
    HOTEL("Hotel", PlaceCategory.OTHER),
    CLOTHING_STORE("Clothing Store", PlaceCategory.SHOPPING),
    SUPERMARKET("Supermarket", PlaceCategory.SHOPPING);

    private final String csvValue;
    private final PlaceCategory placeCategory;

    PlaceSourceCategory(String csvValue, PlaceCategory placeCategory) {
        this.csvValue = csvValue;
        this.placeCategory = placeCategory;
    }

    public String getCsvValue() {
        return csvValue;
    }

    public PlaceCategory getPlaceCategory() {
        return placeCategory;
    }

    public static PlaceSourceCategory fromCsvValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Source category is required");
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_');
        return PlaceSourceCategory.valueOf(normalized);
    }
}
