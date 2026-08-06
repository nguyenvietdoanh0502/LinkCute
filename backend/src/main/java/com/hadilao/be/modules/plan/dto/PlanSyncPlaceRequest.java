package com.hadilao.be.modules.plan.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSyncPlaceRequest {

    @NotNull(message = "INVALID_PLAN_DATA")
    private UUID id;

    @NotBlank(message = "INVALID_PLAN_DATA")
    @Size(max = 255, message = "INVALID_PLAN_DATA")
    private String name;

    private String address;

    @Size(max = 100, message = "INVALID_PLAN_DATA")
    private String district;

    @NotBlank(message = "INVALID_PLAN_DATA")
    @Size(max = 50, message = "INVALID_PLAN_DATA")
    private String category;

    private String photoUrl;

    @DecimalMin(value = "-90", message = "INVALID_PLAN_DATA")
    @DecimalMax(value = "90", message = "INVALID_PLAN_DATA")
    private Double lat;

    @DecimalMin(value = "-180", message = "INVALID_PLAN_DATA")
    @DecimalMax(value = "180", message = "INVALID_PLAN_DATA")
    private Double lng;

    @DecimalMin(value = "0", message = "INVALID_PLAN_DATA")
    private Integer priceLevel;

    @DecimalMin(value = "0", message = "INVALID_PLAN_DATA")
    private Double priceMin;

    @DecimalMin(value = "0", message = "INVALID_PLAN_DATA")
    private Double priceMax;
}
