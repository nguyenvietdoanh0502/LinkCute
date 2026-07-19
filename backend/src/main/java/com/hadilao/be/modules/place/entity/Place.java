package com.hadilao.be.modules.place.entity;

import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.enums.PlaceDataSource;
import com.hadilao.be.modules.place.enums.PlaceSourceCategory;
import com.hadilao.be.modules.place.util.PlaceTextNormalizer;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "places", indexes = {
        @Index(name = "idx_places_lat_lng", columnList = "lat, lng"),
        @Index(name = "idx_places_category", columnList = "category"),
        @Index(name = "idx_places_district", columnList = "district"),
        @Index(name = "idx_places_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"openingHours", "photos", "reviews"})
@ToString(exclude = {"openingHours", "photos", "reviews"})
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "source_id", unique = true, length = 64)
    private String sourceId;

    @Column(name = "osm_id", unique = true)
    private String osmId;

    @Column(name = "overture_id", unique = true, length = 64)
    private String overtureId;

    @Column(name = "google_place_id", unique = true)
    private String googlePlaceId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String district;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PlaceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_category", length = 50)
    private PlaceSourceCategory sourceCategory;

    @Column(name = "source_category_raw", length = 255)
    private String sourceCategoryRaw;

    @Column(name = "overture_category", length = 255)
    private String overtureCategory;

    @Column(name = "osm_category", length = 255)
    private String osmCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", length = 30)
    private PlaceDataSource dataSource;

    @Column(name = "operating_status", length = 50)
    private String operatingStatus;

    private Double confidence;

    @Column(name = "opening_hours_raw", columnDefinition = "TEXT")
    private String openingHoursRaw;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String website;

    @Column(name = "scanned_at_lat")
    private Double scannedAtLat;

    @Column(name = "scanned_at_lng")
    private Double scannedAtLng;

    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "price_level")
    private Integer priceLevel;

    @Column(name = "price_min")
    private Double priceMin;

    @Column(name = "price_max")
    private Double priceMax;

    private Double rating;

    @Column(name = "user_ratings_total")
    private Integer userRatingsTotal;

    @Column(name = "rating_source", length = 50)
    private String ratingSource;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enriched = false;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OpeningHour> openingHours = new ArrayList<>();

    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Photo> photos = new ArrayList<>();

    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        updateSearchText();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        updateSearchText();
    }

    public void updateSearchText() {
        searchText = PlaceTextNormalizer.joinNormalized(
                name,
                address,
                district,
                category != null ? category.name() : null,
                sourceCategory != null ? sourceCategory.getCsvValue() : null,
                sourceCategoryRaw
        );
    }
}
