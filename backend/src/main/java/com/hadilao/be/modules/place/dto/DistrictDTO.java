package com.hadilao.be.modules.place.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DistrictDTO {
    private String district;
    private Long count;
}
