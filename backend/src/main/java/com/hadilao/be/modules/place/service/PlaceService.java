package com.hadilao.be.modules.place.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.place.dto.CategoryDTO;
import com.hadilao.be.modules.place.dto.DistrictDTO;
import com.hadilao.be.modules.place.dto.OpeningHourDTO;
import com.hadilao.be.modules.place.dto.PhotoDTO;
import com.hadilao.be.modules.place.dto.PlaceDetailDTO;
import com.hadilao.be.modules.place.dto.PlaceSummaryDTO;
import com.hadilao.be.modules.place.dto.ReviewDTO;
import com.hadilao.be.modules.place.entity.OpeningHour;
import com.hadilao.be.modules.place.entity.Photo;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.entity.Review;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.enums.PlaceSourceCategory;
import com.hadilao.be.modules.place.repository.OpeningHourRepository;
import com.hadilao.be.modules.place.repository.PhotoRepository;
import com.hadilao.be.modules.place.repository.PlaceRepository;
import com.hadilao.be.modules.place.util.PlaceTextNormalizer;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_PAGE_SIZE = 200;

    private final PlaceRepository placeRepository;
    private final OpeningHourRepository openingHourRepository;
    private final PhotoRepository photoRepository;

    @Transactional(readOnly = true)
    public Page<PlaceSummaryDTO> getPlaces(String q,
                                           String bbox,
                                           PlaceCategory category,
                                           PlaceSourceCategory sourceCategory,
                                           String district,
                                           Double priceMin,
                                           Double priceMax,
                                           Boolean openNow,
                                           int page,
                                           int size) {
        validatePagination(page, size);
        validatePriceRange(priceMin, priceMax);

        Pageable pageable = PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "name")
        );
        Specification<Place> specification = buildSpecification(
                q, bbox, category, sourceCategory, district, priceMin, priceMax, openNow);

        Page<Place> places = placeRepository.findAll(specification, pageable);
        List<UUID> placeIds = places.getContent().stream().map(Place::getId).toList();
        Map<UUID, String> firstPhotoUrls = placeIds.isEmpty()
                ? Collections.emptyMap()
                : photoRepository.findByPlaceIdInOrderByPlaceIdAscSortOrderAsc(placeIds).stream()
                .filter(photo -> photo.getUrl() != null && !photo.getUrl().isBlank())
                .collect(Collectors.toMap(
                        photo -> photo.getPlace().getId(),
                        Photo::getUrl,
                        (first, ignored) -> first
                ));
        List<PlaceSummaryDTO> content = places.getContent().stream()
                .map(place -> mapToSummaryDTO(place, firstPhotoUrls.get(place.getId())))
                .toList();
        return new PageImpl<>(content, pageable, places.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PlaceDetailDTO getPlaceById(UUID id) {
        Place place = placeRepository.findById(id)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        return mapToDetailDTO(place);
    }

    @Transactional(readOnly = true)
    public List<CategoryDTO> getCategories() {
        return placeRepository.countByCategory().stream()
                .map(row -> {
                    PlaceCategory category = (PlaceCategory) row[0];
                    return CategoryDTO.builder()
                            .category(category)
                            .name(getCategoryDisplayName(category))
                            .count((Long) row[1])
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DistrictDTO> getDistricts() {
        return placeRepository.countByDistrict().stream()
                .map(row -> DistrictDTO.builder()
                        .district((String) row[0])
                        .count((Long) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    private Specification<Place> buildSpecification(String q,
                                                     String bbox,
                                                     PlaceCategory category,
                                                     PlaceSourceCategory sourceCategory,
                                                     String district,
                                                     Double priceMin,
                                                     Double priceMax,
                                                     Boolean openNow) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));

            if (q != null && !q.isBlank()) {
                String normalizedQuery = PlaceTextNormalizer.normalize(q);
                if (!normalizedQuery.isBlank()) {
                    predicates.add(criteriaBuilder.like(root.get("searchText"), "%" + normalizedQuery + "%"));
                }
            }

            if (bbox != null && !bbox.isBlank()) {
                double[] bounds = parseBoundingBox(bbox);
                predicates.add(criteriaBuilder.between(root.get("lat"), bounds[0], bounds[2]));
                predicates.add(criteriaBuilder.between(root.get("lng"), bounds[1], bounds[3]));
            }

            if (category != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), category));
            }
            if (sourceCategory != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceCategory"), sourceCategory));
            }
            if (district != null && !district.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("district")),
                        district.trim().toLowerCase()
                ));
            }

            if (priceMin != null && priceMax != null) {
                predicates.add(criteriaBuilder.and(
                        criteriaBuilder.lessThanOrEqualTo(root.get("priceMin"), priceMax),
                        criteriaBuilder.greaterThanOrEqualTo(root.get("priceMax"), priceMin)
                ));
            } else if (priceMin != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("priceMax"), priceMin));
            } else if (priceMax != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("priceMin"), priceMax));
            }

            if (Boolean.TRUE.equals(openNow)) {
                Set<UUID> openPlaceIds = findOpenPlaceIds();
                predicates.add(openPlaceIds.isEmpty()
                        ? criteriaBuilder.disjunction()
                        : root.get("id").in(openPlaceIds));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private double[] parseBoundingBox(String bbox) {
        String[] parts = bbox.split(",", -1);
        if (parts.length != 4) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        try {
            double south = Double.parseDouble(parts[0].trim());
            double west = Double.parseDouble(parts[1].trim());
            double north = Double.parseDouble(parts[2].trim());
            double east = Double.parseDouble(parts[3].trim());
            boolean finite = Double.isFinite(south) && Double.isFinite(west)
                    && Double.isFinite(north) && Double.isFinite(east);
            boolean validCoordinates = south >= -90 && north <= 90 && west >= -180 && east <= 180;
            if (!finite || !validCoordinates || south > north || west > east) {
                throw new AppException(ErrorCode.INVALID_INPUT);
            }
            return new double[]{south, west, north, east};
        } catch (NumberFormatException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validatePriceRange(Double priceMin, Double priceMax) {
        if ((priceMin != null && (!Double.isFinite(priceMin) || priceMin < 0))
                || (priceMax != null && (!Double.isFinite(priceMax) || priceMax < 0))
                || (priceMin != null && priceMax != null && priceMin > priceMax)) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
    }

    private Set<UUID> findOpenPlaceIds() {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        int currentDay = now.getDayOfWeek().getValue() - 1;
        int previousDay = (currentDay + 6) % 7;
        LocalTime currentTime = now.toLocalTime();

        Set<UUID> result = new HashSet<>();
        result.addAll(openingHourRepository.findOpenPlaceIdsNormal(currentDay, currentTime));
        result.addAll(openingHourRepository.findOpenPlaceIdsCrossMidnight(
                currentDay, previousDay, currentTime));
        return result;
    }

    private PlaceSummaryDTO mapToSummaryDTO(Place place, String photoUrl) {
        return PlaceSummaryDTO.builder()
                .id(place.getId())
                .osmId(place.getOsmId())
                .overtureId(place.getOvertureId())
                .name(place.getName())
                .address(place.getAddress())
                .district(place.getDistrict())
                .category(place.getCategory())
                .sourceCategory(place.getSourceCategory())
                .sourceCategoryRaw(place.getSourceCategoryRaw())
                .overtureCategory(place.getOvertureCategory())
                .osmCategory(place.getOsmCategory())
                .dataSource(place.getDataSource())
                .operatingStatus(place.getOperatingStatus())
                .lat(place.getLat())
                .lng(place.getLng())
                .scannedAtLat(place.getScannedAtLat())
                .scannedAtLng(place.getScannedAtLng())
                .phone(place.getPhone())
                .website(place.getWebsite())
                .priceLevel(place.getPriceLevel())
                .priceMin(place.getPriceMin())
                .priceMax(place.getPriceMax())
                .rating(place.getRating())
                .userRatingsTotal(place.getUserRatingsTotal())
                .photoUrl(photoUrl)
                .build();
    }

    private PlaceDetailDTO mapToDetailDTO(Place place) {
        List<OpeningHourDTO> hours = place.getOpeningHours().stream()
                .sorted(Comparator.comparing(OpeningHour::getDayOfWeek))
                .map(hour -> OpeningHourDTO.builder()
                        .dayOfWeek(hour.getDayOfWeek())
                        .openTime(hour.getOpenTime())
                        .closeTime(hour.getCloseTime())
                        .crossesMidnight(hour.getCrossesMidnight())
                        .build())
                .collect(Collectors.toList());

        List<PhotoDTO> photos = place.getPhotos().stream()
                .sorted(Comparator.comparing(photo -> photo.getSortOrder() == null ? 0 : photo.getSortOrder()))
                .map(photo -> PhotoDTO.builder()
                        .url(photo.getUrl())
                        .googlePhotoRef(photo.getGooglePhotoRef())
                        .width(photo.getWidth())
                        .height(photo.getHeight())
                        .sortOrder(photo.getSortOrder())
                        .build())
                .collect(Collectors.toList());

        List<ReviewDTO> reviews = place.getReviews().stream()
                .filter(review -> review.getText() != null && !review.getText().isBlank())
                .sorted(Comparator.comparing(
                        Review::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(5)
                .map(review -> ReviewDTO.builder()
                        .authorName(review.getAuthorName())
                        .rating(review.getRating())
                        .text(review.getText())
                        .relativeTimeDescription(review.getRelativeTimeDescription())
                        .publishedAt(review.getPublishedAt())
                        .source(review.getSource())
                        .build())
                .collect(Collectors.toList());

        return PlaceDetailDTO.builder()
                .id(place.getId())
                .osmId(place.getOsmId())
                .overtureId(place.getOvertureId())
                .googlePlaceId(place.getGooglePlaceId())
                .name(place.getName())
                .address(place.getAddress())
                .district(place.getDistrict())
                .category(place.getCategory())
                .sourceCategory(place.getSourceCategory())
                .sourceCategoryRaw(place.getSourceCategoryRaw())
                .overtureCategory(place.getOvertureCategory())
                .osmCategory(place.getOsmCategory())
                .dataSource(place.getDataSource())
                .operatingStatus(place.getOperatingStatus())
                .confidence(place.getConfidence())
                .openingHoursRaw(place.getOpeningHoursRaw())
                .lat(place.getLat())
                .lng(place.getLng())
                .scannedAtLat(place.getScannedAtLat())
                .scannedAtLng(place.getScannedAtLng())
                .phone(place.getPhone())
                .website(place.getWebsite())
                .priceLevel(place.getPriceLevel())
                .priceMin(place.getPriceMin())
                .priceMax(place.getPriceMax())
                .rating(place.getRating())
                .userRatingsTotal(place.getUserRatingsTotal())
                .ratingSource(place.getRatingSource())
                .enriched(place.getEnriched())
                .lastSyncedAt(place.getLastSyncedAt())
                .openingHours(hours)
                .photos(photos)
                .reviews(reviews)
                .build();
    }

    private String getCategoryDisplayName(PlaceCategory category) {
        if (category == null) {
            return "Khác";
        }
        return switch (category) {
            case FOOD -> "Ăn uống";
            case CAFE -> "Cà phê";
            case ENTERTAINMENT -> "Giải trí";
            case CINEMA -> "Rạp phim";
            case SHOPPING -> "Mua sắm";
            case OTHER -> "Khác";
        };
    }
}
