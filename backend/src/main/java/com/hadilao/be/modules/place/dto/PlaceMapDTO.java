package com.hadilao.be.modules.place.dto;

import com.hadilao.be.modules.place.enums.PlaceCategory;

import java.util.UUID;

public record PlaceMapDTO(
        UUID id,
        String name,
        String district,
        PlaceCategory category,
        Double lat,
        Double lng
) {
}
