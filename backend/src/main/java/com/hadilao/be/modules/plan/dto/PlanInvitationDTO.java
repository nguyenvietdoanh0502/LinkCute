package com.hadilao.be.modules.plan.dto;

import com.hadilao.be.modules.friendship.dto.FriendUserDTO;
import com.hadilao.be.modules.plan.enums.PlanInvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanInvitationDTO {
    private UUID id;
    private PlanSummaryDTO plan;
    private FriendUserDTO inviter;
    private FriendUserDTO invitee;
    private PlanInvitationStatus status;
    private Instant sentAt;
    private Instant respondedAt;
}
