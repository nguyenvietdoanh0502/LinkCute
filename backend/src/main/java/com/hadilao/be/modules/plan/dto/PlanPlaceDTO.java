package com.hadilao.be.modules.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanPlaceDTO {
    private UUID id;
    private String name;
    private String address;
    private String district;
    private String category;
    private String photoUrl;
    private Double lat;
    private Double lng;
    private Integer priceLevel;
    private Double priceMin;
    private Double priceMax;
}
