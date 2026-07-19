package com.hadilao.be.modules.place.dto;

import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.enums.PlaceDataSource;
import com.hadilao.be.modules.place.enums.PlaceSourceCategory;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlaceDetailDTO {
    private UUID id;
    private String osmId;
    private String overtureId;
    private String googlePlaceId;
    private String name;
    private String address;
    private String district;
    private PlaceCategory category;
    private PlaceSourceCategory sourceCategory;
    private String sourceCategoryRaw;
    private String overtureCategory;
    private String osmCategory;
    private PlaceDataSource dataSource;
    private String operatingStatus;
    private Double confidence;
    private String openingHoursRaw;
    private Double lat;
    private Double lng;
    private Double scannedAtLat;
    private Double scannedAtLng;
    private String phone;
    private String website;
    private Integer priceLevel;
    private Double priceMin;
    private Double priceMax;
    private Double rating;
    private Integer userRatingsTotal;
    private String ratingSource;
    private Boolean enriched;
    private Instant lastSyncedAt;

    private List<OpeningHourDTO> openingHours;
    private List<PhotoDTO> photos;
    private List<ReviewDTO> reviews;
}
