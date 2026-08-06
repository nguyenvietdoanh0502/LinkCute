package com.hadilao.be.modules.plan.controller;

import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.plan.dto.PlanDTO;
import com.hadilao.be.modules.plan.dto.PlanSyncRequest;
import com.hadilao.be.modules.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class PlanController {

    private final PlanService planService;

    @GetMapping(UrlConstant.Plan.BASE)
    public ResponseEntity<ApiResponse<List<PlanDTO>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlans()));
    }

    @PostMapping(UrlConstant.Plan.SYNC)
    public ResponseEntity<ApiResponse<PlanDTO>> syncPlan(
            @Valid @RequestBody PlanSyncRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Plan synchronized",
                planService.syncPlan(request)));
    }

    @GetMapping(UrlConstant.Plan.BY_ID)
    public ResponseEntity<ApiResponse<PlanDTO>> getPlan(
            @PathVariable("planId") UUID planId) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlan(planId)));
    }

    @DeleteMapping(UrlConstant.Plan.BY_ID)
    public ResponseEntity<ApiResponse<Void>> deletePlan(
            @PathVariable("planId") UUID planId) {
        planService.deletePlan(planId);
        return ResponseEntity.ok(ApiResponse.success("Plan deleted", null));
    }

    @DeleteMapping(UrlConstant.Plan.MEMBER)
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("planId") UUID planId,
            @PathVariable("userId") UUID userId) {
        planService.removeMember(planId, userId);
        return ResponseEntity.ok(ApiResponse.success("Plan member removed", null));
    }

    @DeleteMapping(UrlConstant.Plan.MEMBERSHIP)
    public ResponseEntity<ApiResponse<Void>> leavePlan(
            @PathVariable("planId") UUID planId) {
        planService.leavePlan(planId);
        return ResponseEntity.ok(ApiResponse.success("Plan membership removed", null));
    }
}
