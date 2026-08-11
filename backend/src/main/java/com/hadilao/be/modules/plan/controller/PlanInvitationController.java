package com.hadilao.be.modules.plan.controller;

import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.plan.dto.PlanDTO;
import com.hadilao.be.modules.plan.dto.PlanInvitationDTO;
import com.hadilao.be.modules.plan.dto.SendPlanInvitationRequest;
import com.hadilao.be.modules.plan.service.PlanInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RestApiV1
@RequiredArgsConstructor
public class PlanInvitationController {

    private final PlanInvitationService planInvitationService;

    @GetMapping(UrlConstant.PlanInvitation.INCOMING)
    public ResponseEntity<ApiResponse<List<PlanInvitationDTO>>> getIncomingInvitations() {
        return ResponseEntity.ok(ApiResponse.success(
                planInvitationService.getIncomingInvitations()));
    }

    @GetMapping(UrlConstant.PlanInvitation.OUTGOING)
    public ResponseEntity<ApiResponse<List<PlanInvitationDTO>>> getOutgoingInvitations() {
        return ResponseEntity.ok(ApiResponse.success(
                planInvitationService.getOutgoingInvitations()));
    }

    @PostMapping(UrlConstant.Plan.INVITATIONS)
    public ResponseEntity<ApiResponse<PlanInvitationDTO>> sendInvitation(
            @PathVariable("planId") UUID planId,
            @Valid @RequestBody SendPlanInvitationRequest request) {
        PlanInvitationDTO invitation = planInvitationService.sendInvitation(
                planId,
                request.getInviteeId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Plan invitation sent", invitation));
    }

    @PostMapping(UrlConstant.PlanInvitation.ACCEPT)
    public ResponseEntity<ApiResponse<PlanDTO>> acceptInvitation(
            @PathVariable("id") UUID invitationId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Plan invitation accepted",
                planInvitationService.acceptInvitation(invitationId)));
    }

    @PostMapping(UrlConstant.PlanInvitation.DECLINE)
    public ResponseEntity<ApiResponse<PlanInvitationDTO>> declineInvitation(
            @PathVariable("id") UUID invitationId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Plan invitation declined",
                planInvitationService.declineInvitation(invitationId)));
    }

    @DeleteMapping(UrlConstant.PlanInvitation.BY_ID)
    public ResponseEntity<ApiResponse<Void>> cancelInvitation(
            @PathVariable("id") UUID invitationId) {
        planInvitationService.cancelInvitation(invitationId);
        return ResponseEntity.ok(ApiResponse.success("Plan invitation cancelled", null));
    }
}
