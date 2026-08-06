package com.hadilao.be.modules.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanItemDTO {
    private UUID id;
    private String clientItemId;
    private int position;
    private PlanPlaceDTO place;
    private LocalTime startTime;
    private LocalTime endTime;
}
