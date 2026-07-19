package com.hadilao.be.modules.place.controller;

import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.common.PageResponse;
import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.place.dto.*;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.enums.PlaceSourceCategory;
import com.hadilao.be.modules.place.service.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RestApiV1
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping(UrlConstant.Place.BASE)
    public ResponseEntity<ApiResponse<PageResponse<PlaceSummaryDTO>>> getPlaces(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String bbox,
            @RequestParam(required = false) PlaceCategory category,
            @RequestParam(required = false) PlaceSourceCategory sourceCategory,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) Double priceMin,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(required = false) Boolean openNow,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<PlaceSummaryDTO> places = placeService.getPlaces(
                q, bbox, category, sourceCategory, district, priceMin, priceMax, openNow, page, size);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(places)));
    }

    @GetMapping(UrlConstant.Place.DETAIL)
    public ResponseEntity<ApiResponse<PlaceDetailDTO>> getPlaceById(@PathVariable UUID id) {
        PlaceDetailDTO place = placeService.getPlaceById(id);
        return ResponseEntity.ok(ApiResponse.success(place));
    }

    @GetMapping(UrlConstant.Place.CATEGORIES)
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getCategories() {
        List<CategoryDTO> categories = placeService.getCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping(UrlConstant.Place.DISTRICTS)
    public ResponseEntity<ApiResponse<List<DistrictDTO>>> getDistricts() {
        List<DistrictDTO> districts = placeService.getDistricts();
        return ResponseEntity.ok(ApiResponse.success(districts));
    }

}
