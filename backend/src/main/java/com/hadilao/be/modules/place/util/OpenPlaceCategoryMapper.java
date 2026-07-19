package com.hadilao.be.modules.place.util;

import com.hadilao.be.modules.place.enums.PlaceCategory;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class OpenPlaceCategoryMapper {

    private OpenPlaceCategoryMapper() {
    }

    public static Optional<PlaceCategory> fromOverture(Collection<String> categories) {
        String value = normalize(categories);
        if (containsAny(value, "cinema", "movie_theater", "movie_theatre")) {
            return Optional.of(PlaceCategory.CINEMA);
        }
        if (containsAny(value, "cafe", "coffee", "tea_house", "bubble_tea")) {
            return Optional.of(PlaceCategory.CAFE);
        }
        if (containsAny(value,
                "food_and_drink", "restaurant", "eatery", "food", "bakery", "dessert",
                "ice_cream", "bar", "pub", "beer_garden")) {
            return Optional.of(PlaceCategory.FOOD);
        }
        if (containsAny(value,
                "arts_and_entertainment", "entertainment", "attraction", "museum", "gallery",
                "theatre", "theater", "karaoke", "nightclub", "amusement", "arcade", "zoo",
                "aquarium", "bowling", "sports_and_recreation", "stadium", "water_park")) {
            return Optional.of(PlaceCategory.ENTERTAINMENT);
        }
        if (containsAny(value,
                "shopping", "retail", "store", "mall", "market", "supermarket",
                "boutique", "clothing", "department_store", "convenience_store")) {
            return Optional.of(PlaceCategory.SHOPPING);
        }
        return Optional.empty();
    }

    public static Optional<PlaceCategory> fromOsm(Map<String, String> tags) {
        String amenity = normalized(tags.get("amenity"));
        String shop = normalized(tags.get("shop"));
        String tourism = normalized(tags.get("tourism"));
        String leisure = normalized(tags.get("leisure"));

        if ("cinema".equals(amenity)) {
            return Optional.of(PlaceCategory.CINEMA);
        }
        if ("cafe".equals(amenity)) {
            return Optional.of(PlaceCategory.CAFE);
        }
        if (containsAny(amenity, "restaurant", "fast_food", "food_court", "bar", "pub", "biergarten")) {
            return Optional.of(PlaceCategory.FOOD);
        }
        if (!shop.isBlank()) {
            return Optional.of(PlaceCategory.SHOPPING);
        }
        if (containsAny(amenity, "theatre", "arts_centre", "nightclub", "music_venue", "casino")
                || containsAny(tourism, "attraction", "museum", "gallery", "theme_park", "zoo", "aquarium")
                || containsAny(leisure, "amusement_arcade", "bowling_alley", "water_park", "sports_centre", "stadium")) {
            return Optional.of(PlaceCategory.ENTERTAINMENT);
        }
        return Optional.empty();
    }

    public static String osmRawCategory(Map<String, String> tags) {
        for (String key : new String[]{"amenity", "shop", "tourism", "leisure"}) {
            String value = tags.get(key);
            if (value != null && !value.isBlank()) {
                return key + "=" + value.trim();
            }
        }
        return null;
    }

    private static String normalize(Collection<String> values) {
        return values == null ? "" : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(OpenPlaceCategoryMapper::normalized)
                .reduce("", (left, right) -> left + " " + right);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean containsAny(String value, String... terms) {
        String[] categories = value.split("\\s+");
        for (String term : terms) {
            for (String category : categories) {
                if (category.equals(term)
                        || category.startsWith(term + "_")
                        || category.endsWith("_" + term)
                        || category.contains("_" + term + "_")) {
                    return true;
                }
            }
        }
        return false;
    }
}
