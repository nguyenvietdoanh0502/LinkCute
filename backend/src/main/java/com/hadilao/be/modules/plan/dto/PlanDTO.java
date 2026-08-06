package com.hadilao.be.modules.plan.dto;

import com.hadilao.be.modules.friendship.dto.FriendUserDTO;
import com.hadilao.be.modules.plan.enums.PlanAccessRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanDTO {
    private UUID id;
    private String clientPlanId;
    private String name;
    private LocalDate date;
    private Instant clientUpdatedAt;
    private long version;
    private PlanAccessRole accessRole;
    private FriendUserDTO owner;
    private List<PlanMemberDTO> members;
    private List<PlanItemDTO> items;
    private Instant createdAt;
    private Instant updatedAt;
}
