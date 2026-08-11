package com.hadilao.be.modules.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSyncRequest {

    @NotBlank(message = "INVALID_PLAN_DATA")
    @Size(max = 128, message = "INVALID_PLAN_DATA")
    private String clientPlanId;

    @NotBlank(message = "INVALID_PLAN_DATA")
    @Size(max = 200, message = "INVALID_PLAN_DATA")
    private String name;

    @NotNull(message = "INVALID_PLAN_DATA")
    private LocalDate date;

    @NotNull(message = "INVALID_PLAN_DATA")
    private Instant clientUpdatedAt;

    private Long expectedVersion;

    @Valid
    @NotNull(message = "INVALID_PLAN_DATA")
    @Size(max = 100, message = "INVALID_PLAN_DATA")
    private List<PlanSyncItemRequest> items;
}
