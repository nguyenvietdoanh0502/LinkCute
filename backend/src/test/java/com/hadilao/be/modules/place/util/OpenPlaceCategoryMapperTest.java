package com.hadilao.be.modules.place.util;

import com.hadilao.be.modules.place.enums.PlaceCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenPlaceCategoryMapperTest {

    @Test
    void mapsOvertureTaxonomyWithoutTreatingBarberAsBar() {
        assertThat(OpenPlaceCategoryMapper.fromOverture(List.of("food_and_drink", "vietnamese_restaurant")))
                .contains(PlaceCategory.FOOD);
        assertThat(OpenPlaceCategoryMapper.fromOverture(List.of("coffee_shop")))
                .contains(PlaceCategory.CAFE);
        assertThat(OpenPlaceCategoryMapper.fromOverture(List.of("barber_shop"))).isEmpty();
    }

    @Test
    void mapsOsmTagsIntoPrdCategories() {
        assertThat(OpenPlaceCategoryMapper.fromOsm(Map.of("amenity", "cinema")))
                .contains(PlaceCategory.CINEMA);
        assertThat(OpenPlaceCategoryMapper.fromOsm(Map.of("shop", "clothes")))
                .contains(PlaceCategory.SHOPPING);
    }
}
