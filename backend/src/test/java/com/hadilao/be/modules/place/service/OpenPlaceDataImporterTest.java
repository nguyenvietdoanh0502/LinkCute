package com.hadilao.be.modules.place.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.enums.PlaceDataSource;
import com.hadilao.be.modules.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenPlaceDataImporterTest {

    @Mock
    private PlaceRepository placeRepository;

    @Test
    void reusesLegacyRecordThenMatchesOsmAndImportsOpeningHours() throws Exception {
        Place legacy = Place.builder()
                .sourceId("legacy-id")
                .name("Phở Test")
                .address("1 Tràng Tiền, Hoàn Kiếm, Hà Nội")
                .district("Hoàn Kiếm")
                .lat(21.03)
                .lng(105.85)
                .category(PlaceCategory.FOOD)
                .rating(4.5)
                .isDeleted(false)
                .build();
        AtomicReference<List<Place>> saved = new AtomicReference<>(List.of());
        when(placeRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Place> result = new ArrayList<>();
            ((Iterable<Place>) invocation.getArgument(0)).forEach(result::add);
            saved.set(result);
            return result;
        });
        when(placeRepository.findAllByOvertureIdIsNotNull()).thenReturn(List.of());
        when(placeRepository.findAllByOsmIdIsNotNull()).thenReturn(List.of());
        when(placeRepository.findAllByIsDeletedFalse()).thenReturn(List.of(legacy));

        OpenPlaceDataImporter importer = new OpenPlaceDataImporter(placeRepository, new ObjectMapper());
        String overture = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":"ov-1","geometry":{"type":"Point","coordinates":[105.8542,21.0285]},
                   "properties":{"names":{"primary":"Phở Test"},"basic_category":"restaurant",
                     "taxonomy":{"primary":"vietnamese_restaurant","hierarchy":["food_and_drink","restaurant","vietnamese_restaurant"]},
                     "addresses":[{"freeform":"1 Tràng Tiền, Hoàn Kiếm, Hà Nội"}],"phones":["+84123"],"operating_status":"open","confidence":0.9}},
                  {"type":"Feature","id":"ov-2","geometry":{"type":"Point","coordinates":[105.85,21.03]},
                   "properties":{"names":{"primary":"Hotel Test"},"basic_category":"hotel"}}
                ]}
                """;

        var overtureResult = importer.importOverture(new StringReader(overture));

        assertThat(overtureResult.getTotal()).isEqualTo(2);
        assertThat(overtureResult.getMatched()).isEqualTo(1);
        assertThat(overtureResult.getSkipped()).isEqualTo(1);
        assertThat(legacy.getOvertureId()).isEqualTo("ov-1");
        assertThat(legacy.getLat()).isEqualTo(21.0285);
        assertThat(legacy.getRating()).isEqualTo(4.5);

        when(placeRepository.findAllByIsDeletedFalse()).thenReturn(List.of(legacy));
        String osm = """
                {"version":0.6,"elements":[
                  {"type":"node","id":123,"lat":21.02851,"lon":105.85421,
                   "tags":{"name":"Phở Test","amenity":"restaurant","opening_hours":"Mo-Su 08:00-22:00","contact:website":"https://example.vn"}}
                ]}
                """;

        var osmResult = importer.importOsm(new StringReader(osm));

        assertThat(osmResult.getMatched()).isEqualTo(1);
        assertThat(osmResult.getOpeningHoursImported()).isEqualTo(7);
        assertThat(legacy.getOsmId()).isEqualTo("node/123");
        assertThat(legacy.getDataSource()).isEqualTo(PlaceDataSource.OVERTURE_OSM);
        assertThat(legacy.getOpeningHours()).hasSize(7);
        assertThat(legacy.getWebsite()).isEqualTo("https://example.vn");
        assertThat(saved.get()).contains(legacy);
    }
}
