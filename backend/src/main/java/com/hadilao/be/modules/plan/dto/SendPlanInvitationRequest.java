package com.hadilao.be.modules.plan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendPlanInvitationRequest {

    @NotNull(message = "MISSING_PLAN_INVITEE_ID")
    private UUID inviteeId;
}
