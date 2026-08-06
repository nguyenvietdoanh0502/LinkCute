package com.hadilao.be.modules.plan.dto;

import com.hadilao.be.modules.friendship.dto.FriendUserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSummaryDTO {
    private UUID id;
    private String name;
    private LocalDate date;
    private long itemCount;
    private FriendUserDTO owner;
}
