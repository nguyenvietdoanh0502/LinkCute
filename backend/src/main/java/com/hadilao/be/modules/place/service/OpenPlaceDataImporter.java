package com.hadilao.be.modules.place.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.place.dto.OpenDataImportResultDTO;
import com.hadilao.be.modules.place.dto.SourceImportStatsDTO;
import com.hadilao.be.modules.place.entity.OpeningHour;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.enums.PlaceDataSource;
import com.hadilao.be.modules.place.repository.PlaceRepository;
import com.hadilao.be.modules.place.util.HanoiDistrictExtractor;
import com.hadilao.be.modules.place.util.OpenPlaceCategoryMapper;
import com.hadilao.be.modules.place.util.OsmOpeningHoursParser;
import com.hadilao.be.modules.place.util.PlaceTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenPlaceDataImporter {

    private static final double HANOI_SOUTH = 20.85;
    private static final double HANOI_WEST = 105.60;
    private static final double HANOI_NORTH = 21.18;
    private static final double HANOI_EAST = 106.00;

    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;

    @Value("${roamly.places.overture-path:data/overture-hanoi.geojson}")
    private String overturePath;

    @Value("${roamly.places.osm-path:data/osm-hanoi.json}")
    private String osmPath;

    @Value("${roamly.places.min-overture-confidence:0.60}")
    private double minOvertureConfidence = 0.60;

    @Transactional
    public OpenDataImportResultDTO importConfiguredFiles() {
        Path overture = requiredFile(overturePath, "Overture");
        Path osm = requiredFile(osmPath, "OSM");

        try (Reader overtureReader = Files.newBufferedReader(overture, StandardCharsets.UTF_8);
             Reader osmReader = Files.newBufferedReader(osm, StandardCharsets.UTF_8)) {
            SourceImportStatsDTO overtureResult = importOverture(overtureReader);
            SourceImportStatsDTO osmResult = importOsm(osmReader);
            int archivedLegacy = archiveUnmatchedLegacyData();
            return OpenDataImportResultDTO.builder()
                    .overture(overtureResult)
                    .osm(osmResult)
                    .archivedLegacy(archivedLegacy)
                    .build();
        } catch (IOException | RuntimeException exception) {
            log.error("Could not import Overture + OSM place data", exception);
            if (exception instanceof AppException appException) {
                throw appException;
            }
            throw new AppException(ErrorCode.PLACE_DATA_IMPORT_FAILED);
        }
    }

    @Transactional
    public SourceImportStatsDTO importConfiguredOverture() {
        Path path = requiredFile(overturePath, "Overture");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return importOverture(reader);
        } catch (IOException | RuntimeException exception) {
            log.error("Could not import Overture place data", exception);
            throw new AppException(ErrorCode.PLACE_DATA_IMPORT_FAILED);
        }
    }

    @Transactional
    public SourceImportStatsDTO importConfiguredOsm() {
        Path path = requiredFile(osmPath, "OSM");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return importOsm(reader);
        } catch (IOException | RuntimeException exception) {
            log.error("Could not import OSM place data", exception);
            throw new AppException(ErrorCode.PLACE_DATA_IMPORT_FAILED);
        }
    }

    SourceImportStatsDTO importOverture(Reader reader) throws IOException {
        Map<String, Place> byOvertureId = indexByOvertureId();
        PlaceMatcher matcher = new PlaceMatcher(placeRepository.findAllByIsDeletedFalse());
        Set<Place> touched = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> importedIds = new HashSet<>();
        MutableStats stats = new MutableStats("overture");
        Instant syncedAt = Instant.now();

        forEachArrayItem(reader, "features", feature -> {
            stats.total++;
            try {
                ParsedOverture parsed = parseOverture(feature);
                if (parsed == null) {
                    stats.skipped++;
                    return;
                }

                Place place = byOvertureId.get(parsed.id());
                boolean matched = false;
                if (place == null) {
                    place = matcher.findSpatial(parsed.name(), parsed.district(), parsed.lat(), parsed.lng(),
                            candidate -> candidate.getOvertureId() == null);
                    if (place == null) {
                        place = matcher.findLegacy(parsed.name(), parsed.district());
                    }
                    matched = place != null;
                }

                if (place == null) {
                    place = new Place();
                    place.setIsDeleted(false);
                    place.setEnriched(false);
                    stats.inserted++;
                } else {
                    stats.updated++;
                    if (matched) {
                        stats.matched++;
                    }
                }

                applyOverture(parsed, place, syncedAt);
                byOvertureId.put(parsed.id(), place);
                importedIds.add(parsed.id());
                matcher.add(place);
                touched.add(place);
                stats.categories.merge(parsed.category().name(), 1L, Long::sum);
            } catch (IllegalArgumentException exception) {
                stats.skipped++;
                log.debug("Skipping invalid Overture feature: {}", exception.getMessage());
            }
        });

        byOvertureId.forEach((id, place) -> {
            if (!importedIds.contains(id)) {
                removeOvertureSource(place);
                touched.add(place);
                if (Boolean.TRUE.equals(place.getIsDeleted())) {
                    stats.archived++;
                }
            }
        });

        placeRepository.saveAll(touched);
        return stats.toDto();
    }

    SourceImportStatsDTO importOsm(Reader reader) throws IOException {
        Map<String, Place> byOsmId = indexByOsmId();
        PlaceMatcher matcher = new PlaceMatcher(placeRepository.findAllByIsDeletedFalse());
        Set<Place> touched = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> importedIds = new HashSet<>();
        MutableStats stats = new MutableStats("osm");
        Instant syncedAt = Instant.now();

        forEachArrayItem(reader, "elements", element -> {
            stats.total++;
            try {
                ParsedOsm parsed = parseOsm(element);
                if (parsed == null) {
                    stats.skipped++;
                    return;
                }

                Place place = byOsmId.get(parsed.id());
                boolean matched = false;
                if (place == null) {
                    place = matcher.findSpatial(parsed.name(), parsed.district(), parsed.lat(), parsed.lng(),
                            candidate -> candidate.getOsmId() == null);
                    if (place == null) {
                        place = matcher.findLegacy(parsed.name(), parsed.district());
                    }
                    matched = place != null;
                }

                if (place == null) {
                    place = new Place();
                    place.setIsDeleted(false);
                    place.setEnriched(false);
                    stats.inserted++;
                } else {
                    stats.updated++;
                    if (matched) {
                        stats.matched++;
                    }
                }

                int importedHours = applyOsm(parsed, place, syncedAt);
                stats.openingHoursImported += importedHours;
                byOsmId.put(parsed.id(), place);
                importedIds.add(parsed.id());
                matcher.add(place);
                touched.add(place);
                stats.categories.merge(parsed.category().name(), 1L, Long::sum);
            } catch (IllegalArgumentException exception) {
                stats.skipped++;
                log.debug("Skipping invalid OSM element: {}", exception.getMessage());
            }
        });

        byOsmId.forEach((id, place) -> {
            if (!importedIds.contains(id)) {
                removeOsmSource(place);
                touched.add(place);
                if (Boolean.TRUE.equals(place.getIsDeleted())) {
                    stats.archived++;
                }
            }
        });

        placeRepository.saveAll(touched);
        return stats.toDto();
    }

    private ParsedOverture parseOverture(JsonNode feature) {
        JsonNode properties = feature.path("properties");
        String id = firstNonBlank(text(feature, "id"), text(properties, "id"));
        String name = extractOvertureName(properties.path("names"));
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        if (id == null || name == null || !coordinates.isArray() || coordinates.size() < 2) {
            return null;
        }

        double lng = coordinates.path(0).asDouble(Double.NaN);
        double lat = coordinates.path(1).asDouble(Double.NaN);
        validateCoordinates(lat, lng);

        List<String> categories = extractOvertureCategories(properties);
        PlaceCategory category = OpenPlaceCategoryMapper.fromOverture(categories).orElse(null);
        if (category == null) {
            return null;
        }

        String address = extractOvertureAddress(properties.path("addresses"));
        if (!belongsToHanoi(properties.path("addresses"), address, lat, lng)) {
            return null;
        }
        String district = HanoiDistrictExtractor.extract(address);
        String rawCategory = firstNonBlank(
                text(properties.path("taxonomy"), "primary"),
                text(properties, "basic_category"),
                text(properties.path("categories"), "primary")
        );
        Double confidence = nullableDouble(properties.path("confidence"));
        if (confidence != null && confidence < minOvertureConfidence) {
            return null;
        }
        return new ParsedOverture(
                id, name, address, district, lat, lng, category, rawCategory,
                firstArrayText(properties.path("phones")),
                firstArrayText(properties.path("websites")),
                text(properties, "operating_status"),
                confidence
        );
    }

    private ParsedOsm parseOsm(JsonNode element) {
        String type = text(element, "type");
        String numericId = element.path("id").asText(null);
        JsonNode tagsNode = element.path("tags");
        if (type == null || numericId == null || !tagsNode.isObject()) {
            return null;
        }

        Map<String, String> tags = new HashMap<>();
        tagsNode.fields().forEachRemaining(entry -> tags.put(entry.getKey(), entry.getValue().asText()));
        String name = firstNonBlank(tags.get("name:vi"), tags.get("name"), tags.get("brand"));
        PlaceCategory category = OpenPlaceCategoryMapper.fromOsm(tags).orElse(null);
        if (name == null || category == null) {
            return null;
        }

        JsonNode center = element.has("lat") ? element : element.path("center");
        double lat = center.path("lat").asDouble(Double.NaN);
        double lng = center.path("lon").asDouble(Double.NaN);
        validateCoordinates(lat, lng);

        if (!belongsToHanoiOsm(tags, lat, lng)) {
            return null;
        }

        String address = osmAddress(tags);
        String district = firstNonBlank(
                HanoiDistrictExtractor.extract(tags.get("addr:district")),
                HanoiDistrictExtractor.extract(address)
        );
        return new ParsedOsm(
                type + "/" + numericId,
                name,
                address,
                district,
                lat,
                lng,
                category,
                OpenPlaceCategoryMapper.osmRawCategory(tags),
                firstNonBlank(tags.get("contact:phone"), tags.get("phone")),
                firstNonBlank(tags.get("contact:website"), tags.get("website")),
                tags.get("opening_hours")
        );
    }

    private void applyOverture(ParsedOverture parsed, Place place, Instant syncedAt) {
        place.setOvertureId(parsed.id());
        place.setName(parsed.name());
        setIfPresent(place::setAddress, parsed.address());
        place.setDistrict(parsed.district());
        place.setLat(parsed.lat());
        place.setLng(parsed.lng());
        place.setCategory(parsed.category());
        place.setOvertureCategory(parsed.rawCategory());
        place.setSourceCategoryRaw(parsed.rawCategory());
        setIfBlank(place.getPhone(), place::setPhone, parsed.phone());
        setIfBlank(place.getWebsite(), place::setWebsite, parsed.website());
        place.setOperatingStatus(parsed.operatingStatus());
        place.setConfidence(parsed.confidence());
        place.setLastSyncedAt(syncedAt);
        place.setDataSource(place.getOsmId() == null ? PlaceDataSource.OVERTURE : PlaceDataSource.OVERTURE_OSM);
        place.setIsDeleted("permanently_closed".equalsIgnoreCase(parsed.operatingStatus()));
        place.updateSearchText();
    }

    private int applyOsm(ParsedOsm parsed, Place place, Instant syncedAt) {
        place.setOsmId(parsed.id());
        if (place.getName() == null || place.getName().isBlank()) {
            place.setName(parsed.name());
        }
        setIfBlank(place.getAddress(), place::setAddress, parsed.address());
        if (parsed.district() != null) {
            place.setDistrict(parsed.district());
        } else if (place.getOvertureId() == null) {
            place.setDistrict(parsed.district());
        }
        if (place.getOvertureId() == null) {
            place.setLat(parsed.lat());
            place.setLng(parsed.lng());
            place.setCategory(parsed.category());
            place.setSourceCategoryRaw(parsed.rawCategory());
        }
        place.setOsmCategory(parsed.rawCategory());
        setIfBlank(place.getPhone(), place::setPhone, parsed.phone());
        setIfBlank(place.getWebsite(), place::setWebsite, parsed.website());
        place.setOpeningHoursRaw(parsed.openingHours());
        place.setLastSyncedAt(syncedAt);
        place.setDataSource(place.getOvertureId() == null ? PlaceDataSource.OSM : PlaceDataSource.OVERTURE_OSM);
        place.setIsDeleted(false);

        List<OsmOpeningHoursParser.ParsedOpeningHour> parsedHours =
                OsmOpeningHoursParser.parse(parsed.openingHours());
        if (!parsedHours.isEmpty()) {
            place.getOpeningHours().clear();
            parsedHours.forEach(hour -> place.getOpeningHours().add(OpeningHour.builder()
                    .place(place)
                    .dayOfWeek(hour.dayOfWeek())
                    .openTime(hour.openTime())
                    .closeTime(hour.closeTime())
                    .crossesMidnight(hour.crossesMidnight())
                    .build()));
        }
        place.updateSearchText();
        return parsedHours.size();
    }

    private void removeOvertureSource(Place place) {
        place.setOvertureId(null);
        place.setOvertureCategory(null);
        place.setConfidence(null);
        place.setOperatingStatus(null);
        if (place.getOsmId() == null) {
            place.setIsDeleted(true);
        } else {
            place.setDataSource(PlaceDataSource.OSM);
        }
    }

    private void removeOsmSource(Place place) {
        place.setOsmId(null);
        place.setOsmCategory(null);
        place.setOpeningHoursRaw(null);
        place.getOpeningHours().clear();
        if (place.getOvertureId() == null) {
            place.setIsDeleted(true);
        } else {
            place.setDataSource(PlaceDataSource.OVERTURE);
        }
    }

    private int archiveUnmatchedLegacyData() {
        List<Place> unmatched = placeRepository
                .findAllBySourceIdIsNotNullAndOvertureIdIsNullAndOsmIdIsNullAndIsDeletedFalse();
        unmatched.forEach(place -> place.setIsDeleted(true));
        if (!unmatched.isEmpty()) {
            placeRepository.saveAll(unmatched);
        }
        return unmatched.size();
    }

    private Map<String, Place> indexByOvertureId() {
        Map<String, Place> result = new HashMap<>();
        placeRepository.findAllByOvertureIdIsNotNull().forEach(place -> result.put(place.getOvertureId(), place));
        return result;
    }

    private Map<String, Place> indexByOsmId() {
        Map<String, Place> result = new HashMap<>();
        placeRepository.findAllByOsmIdIsNotNull().forEach(place -> result.put(place.getOsmId(), place));
        return result;
    }

    private void forEachArrayItem(Reader reader, String arrayName, ThrowingJsonConsumer consumer) throws IOException {
        try (JsonParser parser = objectMapper.getFactory().createParser(reader)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && arrayName.equals(parser.currentName())) {
                    if (parser.nextToken() != JsonToken.START_ARRAY) {
                        throw new IOException(arrayName + " must be a JSON array");
                    }
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        consumer.accept(objectMapper.readTree(parser));
                    }
                    return;
                }
            }
        }
        throw new IOException("Missing JSON array: " + arrayName);
    }

    private List<String> extractOvertureCategories(JsonNode properties) {
        Set<String> result = new HashSet<>();
        addText(result, properties.path("basic_category"));
        addText(result, properties.path("taxonomy").path("primary"));
        addArrayText(result, properties.path("taxonomy").path("hierarchy"));
        addArrayText(result, properties.path("taxonomy").path("alternate"));
        addArrayText(result, properties.path("taxonomy").path("alternates"));
        addText(result, properties.path("categories").path("primary"));
        addArrayText(result, properties.path("categories").path("alternate"));
        return new ArrayList<>(result);
    }

    private String extractOvertureName(JsonNode names) {
        String primary = text(names, "primary");
        if (primary != null) {
            return primary;
        }
        JsonNode common = names.path("common");
        if (common.isObject()) {
            for (String language : new String[]{"vi", "und", "en"}) {
                JsonNode values = common.path(language);
                String value = firstArrayText(values);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String extractOvertureAddress(JsonNode addresses) {
        JsonNode address = addresses.isArray() && !addresses.isEmpty() ? addresses.get(0) : addresses;
        if (address == null || !address.isObject()) {
            return null;
        }
        String freeform = text(address, "freeform");
        if (freeform != null) {
            return freeform;
        }
        return joinNonBlank(", ", text(address, "locality"), text(address, "region"), text(address, "country"));
    }

    private boolean belongsToHanoi(JsonNode addresses, String address, double lat, double lng) {
        JsonNode firstAddress = addresses.isArray() && !addresses.isEmpty() ? addresses.get(0) : addresses;
        String location = joinNonBlank(" ",
                address,
                text(firstAddress, "locality"),
                text(firstAddress, "region")
        );
        if (PlaceTextNormalizer.normalize(location).contains("ha noi")) {
            return true;
        }

        return isInsideSafeHanoiCore(lat, lng);
    }

    private boolean belongsToHanoiOsm(Map<String, String> tags, double lat, double lng) {
        String location = joinNonBlank(" ",
                tags.get("addr:city"),
                tags.get("addr:province"),
                tags.get("addr:district"),
                tags.get("is_in"),
                tags.get("is_in:city")
        );
        if (PlaceTextNormalizer.normalize(location).contains("ha noi")) {
            return true;
        }
        return isInsideSafeHanoiCore(lat, lng);
    }

    private boolean isInsideSafeHanoiCore(double lat, double lng) {
        // Some sources only provide a street name. This core is safely inside Hanoi.
        return lat >= 20.90 && lat <= 21.12 && lng >= 105.70 && lng <= 105.93;
    }

    private String osmAddress(Map<String, String> tags) {
        return joinNonBlank(", ",
                joinNonBlank(" ", tags.get("addr:housenumber"), tags.get("addr:street")),
                tags.get("addr:suburb"),
                tags.get("addr:district"),
                tags.get("addr:city")
        );
    }

    private String firstArrayText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        if (node.isArray()) {
            for (JsonNode value : node) {
                String text = blankToNull(value.asText(null));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private void addText(Collection<String> target, JsonNode node) {
        if (node != null && node.isTextual() && !node.asText().isBlank()) {
            target.add(node.asText());
        }
    }

    private void addArrayText(Collection<String> target, JsonNode node) {
        if (node != null && node.isArray()) {
            node.forEach(value -> addText(target, value));
        }
    }

    private Double nullableDouble(JsonNode node) {
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return blankToNull(node.path(field).asText(null));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String joinNonBlank(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join(delimiter, parts);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateCoordinates(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < HANOI_SOUTH || lat > HANOI_NORTH || lng < HANOI_WEST || lng > HANOI_EAST) {
            throw new IllegalArgumentException("Coordinates are outside the Hanoi bounding box");
        }
    }

    private Path requiredFile(String configuredPath, String source) {
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            log.error("{} data file does not exist: {}", source, path);
            throw new AppException(ErrorCode.PLACE_DATA_IMPORT_FAILED);
        }
        return path;
    }

    private <T> void setIfPresent(java.util.function.Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void setIfBlank(String current, java.util.function.Consumer<String> setter, String value) {
        if ((current == null || current.isBlank()) && value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    @FunctionalInterface
    private interface ThrowingJsonConsumer {
        void accept(JsonNode node);
    }

    private record ParsedOverture(
            String id,
            String name,
            String address,
            String district,
            double lat,
            double lng,
            PlaceCategory category,
            String rawCategory,
            String phone,
            String website,
            String operatingStatus,
            Double confidence
    ) {
    }

    private record ParsedOsm(
            String id,
            String name,
            String address,
            String district,
            double lat,
            double lng,
            PlaceCategory category,
            String rawCategory,
            String phone,
            String website,
            String openingHours
    ) {
    }

    private static final class MutableStats {
        private final String source;
        private int total;
        private int inserted;
        private int updated;
        private int matched;
        private int skipped;
        private int archived;
        private int openingHoursImported;
        private final Map<String, Long> categories = new LinkedHashMap<>();

        private MutableStats(String source) {
            this.source = source;
        }

        private SourceImportStatsDTO toDto() {
            return SourceImportStatsDTO.builder()
                    .source(source)
                    .total(total)
                    .inserted(inserted)
                    .updated(updated)
                    .matched(matched)
                    .skipped(skipped)
                    .archived(archived)
                    .openingHoursImported(openingHoursImported)
                    .categories(categories)
                    .build();
        }
    }

    private static final class PlaceMatcher {
        private static final double CELL_SIZE = 0.002;
        private static final double MAX_DISTANCE_METERS = 150;

        private final Map<String, List<Place>> grid = new HashMap<>();
        private final Map<String, List<Place>> legacyByName = new HashMap<>();

        private PlaceMatcher(List<Place> places) {
            places.forEach(this::add);
        }

        private void add(Place place) {
            if (place.getName() == null) {
                return;
            }
            if (place.getLat() != null && place.getLng() != null
                    && (place.getOvertureId() != null || place.getOsmId() != null)) {
                List<Place> cell = grid.computeIfAbsent(cellKey(place.getLat(), place.getLng()), ignored -> new ArrayList<>());
                if (!cell.contains(place)) {
                    cell.add(place);
                }
            }
            if (place.getSourceId() != null && place.getOvertureId() == null && place.getOsmId() == null) {
                legacyByName.computeIfAbsent(PlaceTextNormalizer.normalize(place.getName()), ignored -> new ArrayList<>())
                        .add(place);
            }
        }

        private Place findSpatial(String name,
                                  String district,
                                  double lat,
                                  double lng,
                                  Predicate<Place> available) {
            int latCell = cell(lat);
            int lngCell = cell(lng);
            Place best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int latOffset = -1; latOffset <= 1; latOffset++) {
                for (int lngOffset = -1; lngOffset <= 1; lngOffset++) {
                    for (Place candidate : grid.getOrDefault((latCell + latOffset) + ":" + (lngCell + lngOffset), List.of())) {
                        if (!available.test(candidate) || !namesMatch(name, candidate.getName())) {
                            continue;
                        }
                        double distance = distanceMeters(lat, lng, candidate.getLat(), candidate.getLng());
                        if (distance <= MAX_DISTANCE_METERS && distance < bestDistance
                                && districtsCompatible(district, candidate.getDistrict())) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
            return best;
        }

        private Place findLegacy(String name, String district) {
            return legacyByName.getOrDefault(PlaceTextNormalizer.normalize(name), List.of()).stream()
                    .filter(place -> place.getOvertureId() == null && place.getOsmId() == null)
                    .filter(place -> districtsCompatible(district, place.getDistrict()))
                    .findFirst()
                    .orElse(null);
        }

        private static boolean namesMatch(String first, String second) {
            String left = PlaceTextNormalizer.normalize(first);
            String right = PlaceTextNormalizer.normalize(second);
            if (left.equals(right)) {
                return true;
            }
            Set<String> leftTokens = new HashSet<>(List.of(left.split("\\s+")));
            Set<String> rightTokens = new HashSet<>(List.of(right.split("\\s+")));
            leftTokens.removeIf(String::isBlank);
            rightTokens.removeIf(String::isBlank);
            Set<String> intersection = new HashSet<>(leftTokens);
            intersection.retainAll(rightTokens);
            Set<String> union = new HashSet<>(leftTokens);
            union.addAll(rightTokens);
            return !union.isEmpty() && (double) intersection.size() / union.size() >= 0.8;
        }

        private static boolean districtsCompatible(String first, String second) {
            return first == null || second == null
                    || PlaceTextNormalizer.normalize(first).equals(PlaceTextNormalizer.normalize(second));
        }

        private static String cellKey(double lat, double lng) {
            return cell(lat) + ":" + cell(lng);
        }

        private static int cell(double value) {
            return (int) Math.floor(value / CELL_SIZE);
        }

        private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
            double earthRadius = 6_371_000;
            double dLat = Math.toRadians(lat2 - lat1);
            double dLng = Math.toRadians(lng2 - lng1);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(dLng / 2) * Math.sin(dLng / 2);
            return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        }
    }
}
