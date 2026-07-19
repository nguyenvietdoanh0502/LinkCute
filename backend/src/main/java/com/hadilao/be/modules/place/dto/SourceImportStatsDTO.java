package com.hadilao.be.modules.place.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class SourceImportStatsDTO {
    private String source;
    private int total;
    private int inserted;
    private int updated;
    private int matched;
    private int skipped;
    private int archived;
    private int openingHoursImported;
    private Map<String, Long> categories;
}
