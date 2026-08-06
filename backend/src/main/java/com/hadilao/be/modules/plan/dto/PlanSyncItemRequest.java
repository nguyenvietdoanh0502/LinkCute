package com.hadilao.be.modules.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSyncItemRequest {

    @NotBlank(message = "INVALID_PLAN_DATA")
    @Size(max = 128, message = "INVALID_PLAN_DATA")
    private String clientItemId;

    @NotNull(message = "INVALID_PLAN_DATA")
    @Min(value = 0, message = "INVALID_PLAN_DATA")
    private Integer position;

    @Valid
    @NotNull(message = "INVALID_PLAN_DATA")
    private PlanSyncPlaceRequest place;

    private LocalTime startTime;
    private LocalTime endTime;
}
