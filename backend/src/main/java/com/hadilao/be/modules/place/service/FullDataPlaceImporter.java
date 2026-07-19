package com.hadilao.be.modules.place.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.place.dto.PlaceImportResultDTO;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.enums.PlaceDataSource;
import com.hadilao.be.modules.place.enums.PlaceSourceCategory;
import com.hadilao.be.modules.place.repository.PlaceRepository;
import com.hadilao.be.modules.place.util.HanoiDistrictExtractor;
import com.hadilao.be.modules.place.util.PlaceTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FullDataPlaceImporter {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "Name", "Category", "Address", "Rating", "Reviews",
            "Phone", "Website", "Scanned_At_Lat", "Scanned_At_Lng"
    );
    private static final Set<String> LEGACY_TEST_PLACE_NAMES = Set.of(
            "Phở Thìn", "Bún Chả Hương Liên", "Cafe Giảng", "Cafe Đinh",
            "CGV Vincom Center", "Lotte Cinema", "Hồ Gươm Xanh", "Phố đi bộ Hồ Gươm",
            "Vincom Mega Mall Times City", "Chợ Đồng Xuân", "Phở Cuốn Hưng Thiện",
            "Cộng Cà Phê", "Galaxy Cinema Nguyễn Du", "Trà Chanh Quận 5", "AEON Mall Long Biên"
    );

    private final PlaceRepository placeRepository;

    @Value("${roamly.places.csv-path:data/full_data.csv}")
    private String csvPath;

    @Transactional
    public PlaceImportResultDTO importConfiguredCsv() {
        Path path = Path.of(csvPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            log.error("Place CSV does not exist: {}", path);
            throw new AppException(ErrorCode.PLACE_DATA_IMPORT_FAILED);
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return importCsv(reader);
        } catch (IOException exception) {
            log.error("Could not read place CSV: {}", path, exception);
            throw new AppException(ErrorCode.PLACE_DATA_IMPORT_FAILED);
        }
    }

    PlaceImportResultDTO importCsv(Reader reader) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(REQUIRED_HEADERS.toArray(String[]::new))
                .setSkipHeaderRecord(false)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .get();

        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String headerLine;
            do {
                headerLine = bufferedReader.readLine();
                if (headerLine != null) {
                    headerLine = headerLine.replace("\uFEFF", "").trim();
                }
            } while (headerLine != null && headerLine.isBlank());

            if (!String.join(",", REQUIRED_HEADERS).equals(headerLine)) {
                throw new IllegalStateException("Missing required CSV headers");
            }

            try (CSVParser parser = format.parse(bufferedReader)) {

            Map<String, Place> existingPlaces = placeRepository.findAllBySourceIdIsNotNull().stream()
                    .collect(Collectors.toMap(Place::getSourceId, Function.identity()));
            Map<String, Long> categoryCounts = new LinkedHashMap<>();
            int inserted = 0;
            int updated = 0;
            int skipped = 0;
            int totalRows = 0;

            for (CSVRecord record : parser) {
                totalRows++;
                try {
                    ParsedPlace parsed = parse(record);
                    Place place = existingPlaces.get(parsed.sourceId());
                    if (place == null) {
                        place = new Place();
                        place.setSourceId(parsed.sourceId());
                        place.setIsDeleted(false);
                        place.setEnriched(false);
                        existingPlaces.put(parsed.sourceId(), place);
                        inserted++;
                    } else {
                        updated++;
                    }

                    apply(parsed, place);
                    categoryCounts.merge(parsed.sourceCategory().getCsvValue(), 1L, Long::sum);
                } catch (IllegalArgumentException exception) {
                    skipped++;
                    log.warn("Skipping invalid place CSV row {}: {}", record.getRecordNumber(), exception.getMessage());
                }
            }

                placeRepository.saveAll(existingPlaces.values());
                int archivedLegacy = archiveLegacyTestData();
                return PlaceImportResultDTO.builder()
                        .totalRows(totalRows)
                        .inserted(inserted)
                        .updated(updated)
                        .skipped(skipped)
                        .archivedLegacy(archivedLegacy)
                        .sourceCategories(categoryCounts)
                        .build();
            }
        } catch (IOException | IllegalStateException exception) {
            log.error("Could not parse place CSV", exception);
            throw new AppException(ErrorCode.PLACE_DATA_IMPORT_FAILED);
        }
    }

    private int archiveLegacyTestData() {
        List<Place> legacyPlaces = placeRepository.findAllBySourceIdIsNullAndIsDeletedFalse().stream()
                .filter(place -> LEGACY_TEST_PLACE_NAMES.contains(place.getName()))
                .filter(place -> place.getOsmId() == null && place.getGooglePlaceId() == null)
                .toList();
        legacyPlaces.forEach(place -> place.setIsDeleted(true));
        if (!legacyPlaces.isEmpty()) {
            placeRepository.saveAll(legacyPlaces);
        }
        return legacyPlaces.size();
    }

    private ParsedPlace parse(CSVRecord record) {
        String name = required(record, "Name");
        String address = required(record, "Address");
        PlaceSourceCategory sourceCategory = PlaceSourceCategory.fromCsvValue(required(record, "Category"));
        double rating = parseDouble(record, "Rating");
        int reviews = parseInteger(record, "Reviews");
        double scannedAtLat = parseDouble(record, "Scanned_At_Lat");
        double scannedAtLng = parseDouble(record, "Scanned_At_Lng");

        if (name.length() > 255) {
            throw new IllegalArgumentException("Name is longer than 255 characters");
        }
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        }
        if (reviews < 0) {
            throw new IllegalArgumentException("Reviews cannot be negative");
        }
        if (scannedAtLat < -90 || scannedAtLat > 90 || scannedAtLng < -180 || scannedAtLng > 180) {
            throw new IllegalArgumentException("Scanned coordinates are invalid");
        }

        return new ParsedPlace(
                sourceId(name, address),
                name,
                address,
                sourceCategory,
                rating,
                reviews,
                optional(record, "Phone"),
                optional(record, "Website"),
                scannedAtLat,
                scannedAtLng
        );
    }

    private void apply(ParsedPlace parsed, Place place) {
        place.setName(parsed.name());
        place.setAddress(parsed.address());
        place.setDistrict(HanoiDistrictExtractor.extract(parsed.address()));
        place.setCategory(parsed.sourceCategory().getPlaceCategory());
        place.setSourceCategory(parsed.sourceCategory());
        place.setRating(parsed.rating());
        place.setUserRatingsTotal(parsed.reviews());
        place.setPhone(parsed.phone());
        place.setWebsite(parsed.website());
        place.setLat(parsed.scannedAtLat());
        place.setLng(parsed.scannedAtLng());
        place.setScannedAtLat(parsed.scannedAtLat());
        place.setScannedAtLng(parsed.scannedAtLng());
        place.setRatingSource("full_data");
        place.setDataSource(PlaceDataSource.FULL_DATA);
        place.setSourceCategoryRaw(parsed.sourceCategory().getCsvValue());
        place.setIsDeleted(false);
        place.updateSearchText();
    }

    private String required(CSVRecord record, String header) {
        String value = record.get(header).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(header + " is required");
        }
        return value;
    }

    private String optional(CSVRecord record, String header) {
        String value = record.get(header).trim();
        return value.isEmpty() ? null : value;
    }

    private double parseDouble(CSVRecord record, String header) {
        try {
            return Double.parseDouble(required(record, header));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(header + " must be a number");
        }
    }

    private int parseInteger(CSVRecord record, String header) {
        try {
            return Integer.parseInt(required(record, header));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(header + " must be an integer");
        }
    }

    private String sourceId(String name, String address) {
        String value = PlaceTextNormalizer.normalize(name) + "|" + PlaceTextNormalizer.normalize(address);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record ParsedPlace(
            String sourceId,
            String name,
            String address,
            PlaceSourceCategory sourceCategory,
            double rating,
            int reviews,
            String phone,
            String website,
            double scannedAtLat,
            double scannedAtLng
    ) {
    }
}
