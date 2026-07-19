package com.hadilao.be.modules.place.controller;

import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.place.dto.PlaceImportResultDTO;
import com.hadilao.be.modules.place.dto.OpenDataImportResultDTO;
import com.hadilao.be.modules.place.dto.SourceImportStatsDTO;
import com.hadilao.be.modules.place.service.FullDataPlaceImporter;
import com.hadilao.be.modules.place.service.OpenPlaceDataImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RestApiV1
@Profile("local")
@PreAuthorize("denyAll()")
@RequiredArgsConstructor
public class LocalPlaceDataController {

    private final FullDataPlaceImporter importer;
    private final OpenPlaceDataImporter openPlaceDataImporter;

    @PostMapping(UrlConstant.Place.IMPORT)
    public ResponseEntity<ApiResponse<PlaceImportResultDTO>> importFullData() {
        PlaceImportResultDTO result = importer.importConfiguredCsv();
        return ResponseEntity.ok(ApiResponse.success("Place data imported", result));
    }

    @PostMapping(UrlConstant.Place.IMPORT_OPEN_DATA)
    public ResponseEntity<ApiResponse<OpenDataImportResultDTO>> importOpenData() {
        OpenDataImportResultDTO result = openPlaceDataImporter.importConfiguredFiles();
        return ResponseEntity.ok(ApiResponse.success("Overture + OSM place data imported", result));
    }

    @PostMapping(UrlConstant.Place.IMPORT_OVERTURE)
    public ResponseEntity<ApiResponse<SourceImportStatsDTO>> importOverture() {
        return ResponseEntity.ok(ApiResponse.success(
                "Overture place data imported", openPlaceDataImporter.importConfiguredOverture()));
    }

    @PostMapping(UrlConstant.Place.IMPORT_OSM)
    public ResponseEntity<ApiResponse<SourceImportStatsDTO>> importOsm() {
        return ResponseEntity.ok(ApiResponse.success(
                "OSM place data imported", openPlaceDataImporter.importConfiguredOsm()));
    }
}
