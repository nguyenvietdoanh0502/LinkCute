package com.hadilao.be.modules.place.repository;

import com.hadilao.be.modules.place.dto.PlaceMapDTO;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:place_repository;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PlaceRepositoryTest {

    @Autowired
    private PlaceRepository placeRepository;

    @Test
    void returnsActivePlacesWhenSearchIsAbsent() {
        Place cafe = savePlace("Cafe Giang", "Hoan Kiem", PlaceCategory.CAFE, false);
        savePlace("Deleted Cafe", "Hoan Kiem", PlaceCategory.CAFE, true);

        List<PlaceMapDTO> places = placeRepository.findMapPlacesWithoutSearch(null, null);

        assertThat(places)
                .extracting(PlaceMapDTO::id)
                .containsExactly(cafe.getId());
    }

    @Test
    void appliesSearchAlongsideOptionalMapFilters() {
        Place cafe = savePlace("Cafe Giang", "Hoan Kiem", PlaceCategory.CAFE, false);
        savePlace("Cafe Other", "Ba Dinh", PlaceCategory.CAFE, false);
        savePlace("Pho Thin", "Hoan Kiem", PlaceCategory.FOOD, false);

        List<PlaceMapDTO> places = placeRepository.findMapPlacesBySearch(
                "cafe", PlaceCategory.CAFE, "hoan kiem");

        assertThat(places)
                .extracting(PlaceMapDTO::id)
                .containsExactly(cafe.getId());
    }

    private Place savePlace(String name,
                            String district,
                            PlaceCategory category,
                            boolean deleted) {
        return placeRepository.saveAndFlush(Place.builder()
                .name(name)
                .district(district)
                .lat(21.0285)
                .lng(105.8542)
                .category(category)
                .isDeleted(deleted)
                .build());
    }
}
