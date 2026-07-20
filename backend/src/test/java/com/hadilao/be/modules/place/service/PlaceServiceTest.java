package com.hadilao.be.modules.place.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.place.dto.*;
import com.hadilao.be.modules.place.entity.OpeningHour;
import com.hadilao.be.modules.place.entity.Photo;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.entity.Review;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.repository.OpeningHourRepository;
import com.hadilao.be.modules.place.repository.PhotoRepository;
import com.hadilao.be.modules.place.repository.PlaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for PlaceService")
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private OpeningHourRepository openingHourRepository;

    @Mock
    private PhotoRepository photoRepository;

    @InjectMocks
    private PlaceService placeService;

    private Place createPlace(UUID id, String name, String district, PlaceCategory category,
                              Double lat, Double lng, Double priceMin, Double priceMax,
                              Double rating) {
        return Place.builder()
                .id(id)
                .name(name)
                .address("123 Test Street")
                .district(district)
                .category(category)
                .lat(lat)
                .lng(lng)
                .priceMin(priceMin)
                .priceMax(priceMax)
                .rating(rating)
                .isDeleted(false)
                .build();
    }

    @Nested
    @DisplayName("getMapPlaces Tests")
    class GetMapPlacesTests {

        @Test
        @DisplayName("Should normalize filters and return the lightweight map projection")
        void testGetMapPlaces_WithFilters() {
            PlaceMapDTO place = new PlaceMapDTO(
                    UUID.randomUUID(), "Pho Bo", "Hoan Kiem",
                    PlaceCategory.FOOD, 21.0285, 105.8542);
            when(placeRepository.findMapPlaces("pho", PlaceCategory.FOOD, "hoan kiem"))
                    .thenReturn(List.of(place));

            List<PlaceMapDTO> result = placeService.getMapPlaces(
                    " Phở ", PlaceCategory.FOOD, " Hoan Kiem ", false);

            assertThat(result).containsExactly(place);
            verify(placeRepository).findMapPlaces("pho", PlaceCategory.FOOD, "hoan kiem");
            verifyNoInteractions(openingHourRepository);
        }
    }

    @Nested
    @DisplayName("getPlaces Tests")
    class GetPlacesTests {

        @Test
        @DisplayName("Should return paginated places with no filters")
        void testGetPlaces_NoFilters() {
            // Arrange
            UUID id = UUID.randomUUID();
            Place place = createPlace(id, "Test Place", "Hoan Kiem", PlaceCategory.FOOD,
                    21.0285, 105.8542, 50000.0, 150000.0, 4.5);

            Page<Place> placePage = new PageImpl<>(List.of(place));
            when(placeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(placePage);

            // Act
            Page<PlaceSummaryDTO> result = placeService.getPlaces(
                    null, null, null, null, null, null, null, null, 0, 20);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Test Place");
            assertThat(result.getContent().get(0).getCategory()).isEqualTo(PlaceCategory.FOOD);
            verify(placeRepository).findAll(any(Specification.class), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should filter by category")
        void testGetPlaces_FilterByCategory() {
            // Arrange
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Place foodPlace = createPlace(id1, "Pho Bo", "Hoan Kiem", PlaceCategory.FOOD,
                    21.0285, 105.8542, 30000.0, 80000.0, 4.2);
            Place cafePlace = createPlace(id2, "Cafe Phin", "Tay Ho", PlaceCategory.CAFE,
                    21.0585, 105.8342, 40000.0, 100000.0, 4.5);

            when(placeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(foodPlace)));

            // Act
            Page<PlaceSummaryDTO> result = placeService.getPlaces(
                    null, null, PlaceCategory.FOOD, null, null, null, null, null, 0, 20);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCategory()).isEqualTo(PlaceCategory.FOOD);
        }

        @Test
        @DisplayName("Should filter by search query")
        void testGetPlaces_SearchByQuery() {
            // Arrange
            UUID id = UUID.randomUUID();
            Place place = createPlace(id, "Bun Bo Hue", "Hoan Kiem", PlaceCategory.FOOD,
                    21.0285, 105.8542, 30000.0, 80000.0, 4.2);

            when(placeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(place)));

            // Act
            Page<PlaceSummaryDTO> result = placeService.getPlaces(
                    "bun bo", null, null, null, null, null, null, null, 0, 20);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Bun Bo Hue");
        }

        @Test
        @DisplayName("Should return empty page when no places match")
        void testGetPlaces_EmptyResult() {
            // Arrange
            when(placeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(Page.empty());

            // Act
            Page<PlaceSummaryDTO> result = placeService.getPlaces(
                    "nonexistent", null, null, null, null, null, null, null, 0, 20);

            // Assert
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("getPlaceById Tests")
    class GetPlaceByIdTests {

        @Test
        @DisplayName("Should return place detail when found")
        void testGetPlaceById_Success() {
            // Arrange
            UUID id = UUID.randomUUID();
            Place place = createPlace(id, "Test Place", "Hoan Kiem", PlaceCategory.FOOD,
                    21.0285, 105.8542, 50000.0, 150000.0, 4.5);
            place.setPhone("0123456789");

            OpeningHour hour = OpeningHour.builder()
                    .place(place)
                    .dayOfWeek(1)
                    .openTime(LocalTime.of(8, 0))
                    .closeTime(LocalTime.of(22, 0))
                    .crossesMidnight(false)
                    .build();

            Photo photo = Photo.builder()
                    .place(place)
                    .url("https://example.com/photo.jpg")
                    .sortOrder(0)
                    .build();

            Review review = Review.builder()
                    .place(place)
                    .authorName("John")
                    .rating(5)
                    .text("Great place!")
                    .source("google")
                    .build();

            place.setOpeningHours(List.of(hour));
            place.setPhotos(List.of(photo));
            place.setReviews(List.of(review));

            when(placeRepository.findById(id)).thenReturn(Optional.of(place));

            // Act
            PlaceDetailDTO result = placeService.getPlaceById(id);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Test Place");
            assertThat(result.getPhone()).isEqualTo("0123456789");
            assertThat(result.getOpeningHours()).hasSize(1);
            assertThat(result.getPhotos()).hasSize(1);
            assertThat(result.getReviews()).hasSize(1);
            assertThat(result.getOpeningHours().get(0).getOpenTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(result.getReviews().get(0).getAuthorName()).isEqualTo("John");
            verify(placeRepository).findById(id);
        }

        @Test
        @DisplayName("Should throw RESOURCE_NOT_FOUND when place does not exist")
        void testGetPlaceById_NotFound() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(placeRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> placeService.getPlaceById(id))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(placeRepository).findById(id);
        }

        @Test
        @DisplayName("Should throw RESOURCE_NOT_FOUND when place is deleted")
        void testGetPlaceById_Deleted() {
            // Arrange
            UUID id = UUID.randomUUID();
            Place place = createPlace(id, "Deleted Place", "Hoan Kiem", PlaceCategory.FOOD,
                    21.0285, 105.8542, null, null, null);
            place.setIsDeleted(true);

            when(placeRepository.findById(id)).thenReturn(Optional.of(place));

            // Act & Assert
            assertThatThrownBy(() -> placeService.getPlaceById(id))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getCategories Tests")
    class GetCategoriesTests {

        @Test
        @DisplayName("Should return list of categories with counts")
        void testGetCategories_Success() {
            // Arrange
            List<Object[]> mockResults = List.of(
                    new Object[]{PlaceCategory.FOOD, 10L},
                    new Object[]{PlaceCategory.CAFE, 5L},
                    new Object[]{PlaceCategory.CINEMA, 3L}
            );
            when(placeRepository.countByCategory()).thenReturn(mockResults);

            // Act
            List<CategoryDTO> result = placeService.getCategories();

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).extracting(CategoryDTO::getName)
                    .containsExactly("Ăn uống", "Cà phê", "Rạp phim");
            assertThat(result).extracting(CategoryDTO::getCount)
                    .containsExactly(10L, 5L, 3L);
            verify(placeRepository).countByCategory();
        }

        @Test
        @DisplayName("Should return empty list when no places exist")
        void testGetCategories_Empty() {
            when(placeRepository.countByCategory()).thenReturn(List.of());
            List<CategoryDTO> result = placeService.getCategories();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDistricts Tests")
    class GetDistrictsTests {

        @Test
        @DisplayName("Should return list of districts with counts")
        void testGetDistricts_Success() {
            // Arrange
            List<Object[]> mockResults = List.of(
                    new Object[]{"Hoan Kiem", 20L},
                    new Object[]{"Tay Ho", 15L},
                    new Object[]{"Cau Giay", 10L}
            );
            when(placeRepository.countByDistrict()).thenReturn(mockResults);

            // Act
            List<DistrictDTO> result = placeService.getDistricts();

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getDistrict()).isEqualTo("Hoan Kiem");
            assertThat(result.get(0).getCount()).isEqualTo(20L);
            verify(placeRepository).countByDistrict();
        }
    }
}
