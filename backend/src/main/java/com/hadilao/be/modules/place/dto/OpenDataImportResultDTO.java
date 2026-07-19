package com.hadilao.be.modules.place.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenDataImportResultDTO {
    private SourceImportStatsDTO overture;
    private SourceImportStatsDTO osm;
    private int archivedLegacy;
}
