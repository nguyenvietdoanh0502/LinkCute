package com.hadilao.be.modules.place.service;

import com.hadilao.be.modules.place.dto.PlaceImportResultDTO;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.enums.PlaceSourceCategory;
import com.hadilao.be.modules.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FullDataPlaceImporterTest {

    @Mock
    private PlaceRepository placeRepository;

    @Test
    void importsEveryRowFromFixtureAndMapsSourceFields() throws Exception {
        AtomicReference<List<Place>> savedPlaces = captureSavedPlaces();
        when(placeRepository.findAllBySourceIdIsNotNull()).thenReturn(List.of());
        FullDataPlaceImporter importer = new FullDataPlaceImporter(placeRepository);

        PlaceImportResultDTO result;
        try (InputStream inputStream = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/full_data_sample.csv")
        ); Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            result = importer.importCsv(reader);
        }

        assertThat(result.getTotalRows()).isEqualTo(6);
        assertThat(result.getInserted()).isEqualTo(6);
        assertThat(result.getUpdated()).isZero();
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getSourceCategories()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "Restaurant", 2L,
                "Cafe", 1L,
                "Hotel", 1L,
                "Clothing Store", 1L,
                "Supermarket", 1L
        ));

        assertThat(savedPlaces.get()).hasSize(6);
        assertThat(savedPlaces.get()).extracting(Place::getSourceId).doesNotHaveDuplicates();
        assertThat(savedPlaces.get()).filteredOn(place -> place.getWebsite() == null).hasSize(3);
        assertThat(savedPlaces.get()).filteredOn(place -> place.getPhone() == null).hasSize(3);

        Place restaurant = savedPlaces.get().stream()
                .filter(place -> place.getName().equals("Thai Market Restaurant - Le Van Thiem"))
                .findFirst()
                .orElseThrow();
        assertThat(restaurant.getCategory()).isEqualTo(PlaceCategory.FOOD);
        assertThat(restaurant.getSourceCategory()).isEqualTo(PlaceSourceCategory.RESTAURANT);
        assertThat(restaurant.getDistrict()).isEqualTo("Thanh Xuân");
        assertThat(restaurant.getScannedAtLat()).isEqualTo(21.0);
        assertThat(restaurant.getLat()).isEqualTo(restaurant.getScannedAtLat());
        assertThat(restaurant.getSearchText()).contains("thai market restaurant", "le van thiem");

        assertThat(savedPlaces.get())
                .filteredOn(place -> place.getSourceCategory() == PlaceSourceCategory.HOTEL)
                .allMatch(place -> place.getCategory() == PlaceCategory.OTHER);
    }

    @Test
    void reimportUpdatesExistingRowsWithoutCreatingDuplicates() throws Exception {
        AtomicReference<List<Place>> firstSave = captureSavedPlaces();
        when(placeRepository.findAllBySourceIdIsNotNull()).thenReturn(List.of());
        FullDataPlaceImporter importer = new FullDataPlaceImporter(placeRepository);
        String csv = "Name,Category,Address,Rating,Reviews,Phone,Website,Scanned_At_Lat,Scanned_At_Lng\n"
                + "Cafe Test,Cafe,1 Cau Giay Ha Noi,4.5,10,,,21.02,105.8\n";

        importer.importCsv(new java.io.StringReader(csv));
        Place existing = firstSave.get().get(0);
        when(placeRepository.findAllBySourceIdIsNotNull()).thenReturn(List.of(existing));

        PlaceImportResultDTO result = importer.importCsv(new java.io.StringReader(csv.replace("4.5,10", "4.8,20")));

        assertThat(result.getInserted()).isZero();
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(existing.getRating()).isEqualTo(4.8);
        assertThat(existing.getUserRatingsTotal()).isEqualTo(20);
    }

    private AtomicReference<List<Place>> captureSavedPlaces() {
        AtomicReference<List<Place>> captured = new AtomicReference<>(List.of());
        when(placeRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<Place> places = invocation.getArgument(0);
            List<Place> copy = new ArrayList<>();
            places.forEach(copy::add);
            captured.set(copy);
            return copy;
        });
        return captured;
    }
}
