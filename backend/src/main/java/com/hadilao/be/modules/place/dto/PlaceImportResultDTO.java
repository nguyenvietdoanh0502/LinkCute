package com.hadilao.be.modules.place.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PlaceImportResultDTO {
    private int totalRows;
    private int inserted;
    private int updated;
    private int skipped;
    private int archivedLegacy;
    private Map<String, Long> sourceCategories;
}
