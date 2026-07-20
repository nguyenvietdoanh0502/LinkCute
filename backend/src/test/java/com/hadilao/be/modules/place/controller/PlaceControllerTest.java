package com.hadilao.be.modules.place.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.service.MailService;
import com.hadilao.be.modules.auth.service.OtpService;
import com.hadilao.be.modules.place.dto.*;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.service.PlaceService;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests for PlaceController")
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlaceService placeService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MailService mailService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Nested
    @DisplayName("Place import endpoint security")
    class ImportEndpointSecurity {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/v1/places/import",
                "/api/v1/places/import/open-data",
                "/api/v1/places/import/overture",
                "/api/v1/places/import/osm"
        })
        @DisplayName("Should deny every HTTP import endpoint")
        void shouldDenyEveryImportEndpoint(String path) throws Exception {
            mockMvc.perform(post(path).with(user("test@example.com")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/places/map")
    class GetMapPlaces {

        @Test
        @DisplayName("Should return the lightweight unpaginated map dataset")
        void testGetMapPlaces_Success() throws Exception {
            UUID id = UUID.randomUUID();
            PlaceMapDTO place = new PlaceMapDTO(
                    id, "Pho Bo", "Hoan Kiem", PlaceCategory.FOOD, 21.0285, 105.8542);
            when(placeService.getMapPlaces("pho", PlaceCategory.FOOD, "Hoan Kiem", false))
                    .thenReturn(List.of(place));

            mockMvc.perform(get("/api/v1/places/map")
                            .param("q", "pho")
                            .param("category", "FOOD")
                            .param("district", "Hoan Kiem")
                            .param("openNow", "false")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                    .andExpect(jsonPath("$.data[0].name").value("Pho Bo"))
                    .andExpect(jsonPath("$.data[0].category").value("FOOD"))
                    .andExpect(jsonPath("$.data[0].lat").value(21.0285))
                    .andExpect(jsonPath("$.data[0].lng").value(105.8542))
                    .andExpect(jsonPath("$.data[0].photoUrl").doesNotExist());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/places")
    class GetPlaces {

        @Test
        @DisplayName("Should return paginated list of places")
        void testGetPlaces_Success() throws Exception {
            PlaceSummaryDTO place = PlaceSummaryDTO.builder()
                    .id(UUID.randomUUID())
                    .name("Pho Bo")
                    .address("123 Test Street")
                    .district("Hoan Kiem")
                    .category(PlaceCategory.FOOD)
                    .lat(21.0285)
                    .lng(105.8542)
                    .rating(4.5)
                    .build();

            Page<PlaceSummaryDTO> page = new PageImpl<>(List.of(place));
            when(placeService.getPlaces(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/places")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.content[0].name").value("Pho Bo"))
                    .andExpect(jsonPath("$.data.content[0].category").value("FOOD"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.pageable").doesNotExist());
        }

        @Test
        @DisplayName("Should accept query parameters")
        void testGetPlaces_WithFilters() throws Exception {
            when(placeService.getPlaces(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/v1/places")
                            .param("q", "pho")
                            .param("category", "FOOD")
                            .param("district", "Hoan Kiem")
                            .param("openNow", "true")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("Should return empty list when no places found")
        void testGetPlaces_Empty() throws Exception {
            when(placeService.getPlaces(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/v1/places")
                            .param("q", "nonexistent")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("Should return 400 for an unknown category")
        void testGetPlaces_InvalidCategory() throws Exception {
            mockMvc.perform(get("/api/v1/places")
                            .param("category", "NOPE")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/places/{id}")
    class GetPlaceById {

        @Test
        @DisplayName("Should return place detail when found")
        void testGetPlaceById_Success() throws Exception {
            UUID id = UUID.randomUUID();
            PlaceDetailDTO detail = PlaceDetailDTO.builder()
                    .id(id)
                    .name("Pho Bo")
                    .address("123 Test Street")
                    .district("Hoan Kiem")
                    .category(PlaceCategory.FOOD)
                    .lat(21.0285)
                    .lng(105.8542)
                    .phone("0123456789")
                    .rating(4.5)
                    .openingHours(List.of(
                            OpeningHourDTO.builder()
                                    .dayOfWeek(1)
                                    .openTime(LocalTime.of(8, 0))
                                    .closeTime(LocalTime.of(22, 0))
                                    .build()
                    ))
                    .build();

            when(placeService.getPlaceById(id)).thenReturn(detail);

            mockMvc.perform(get("/api/v1/places/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.name").value("Pho Bo"))
                    .andExpect(jsonPath("$.data.phone").value("0123456789"))
                    .andExpect(jsonPath("$.data.category").value("FOOD"))
                    .andExpect(jsonPath("$.data.openingHours[0].dayOfWeek").value(1));
        }

        @Test
        @DisplayName("Should return 404 when place not found")
        void testGetPlaceById_NotFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(placeService.getPlaceById(id))
                    .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(get("/api/v1/places/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 400 when place id is not a UUID")
        void testGetPlaceById_InvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/places/not-a-uuid")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/categories")
    class GetCategories {

        @Test
        @DisplayName("Should return list of categories")
        void testGetCategories_Success() throws Exception {
            List<CategoryDTO> categories = List.of(
                    CategoryDTO.builder().category(PlaceCategory.FOOD).name("Ăn uống").count(10L).build(),
                    CategoryDTO.builder().category(PlaceCategory.CAFE).name("Cà phê").count(5L).build()
            );

            when(placeService.getCategories()).thenReturn(categories);

            mockMvc.perform(get("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data[0].name").value("Ăn uống"))
                    .andExpect(jsonPath("$.data[0].count").value(10))
                    .andExpect(jsonPath("$.data[1].name").value("Cà phê"));
        }

        @Test
        @DisplayName("Should return empty list when no places")
        void testGetCategories_Empty() throws Exception {
            when(placeService.getCategories()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("Should ignore a malformed bearer token on a public endpoint")
        void testGetCategories_MalformedBearerToken() throws Exception {
            when(placeService.getCategories()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/categories")
                            .header("Authorization", "Bearer not-a-jwt")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/districts")
    class GetDistricts {

        @Test
        @DisplayName("Should return list of districts")
        void testGetDistricts_Success() throws Exception {
            List<DistrictDTO> districts = List.of(
                    DistrictDTO.builder().district("Hoan Kiem").count(20L).build(),
                    DistrictDTO.builder().district("Tay Ho").count(15L).build()
            );

            when(placeService.getDistricts()).thenReturn(districts);

            mockMvc.perform(get("/api/v1/districts")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data[0].district").value("Hoan Kiem"))
                    .andExpect(jsonPath("$.data[1].count").value(15));
        }
    }
}
