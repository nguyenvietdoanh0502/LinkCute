package com.hadilao.be.modules.plan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.service.MailService;
import com.hadilao.be.modules.auth.service.OtpService;
import com.hadilao.be.modules.friendship.dto.FriendUserDTO;
import com.hadilao.be.modules.plan.dto.PlanDTO;
import com.hadilao.be.modules.plan.dto.PlanInvitationDTO;
import com.hadilao.be.modules.plan.dto.PlanSummaryDTO;
import com.hadilao.be.modules.plan.dto.PlanSyncRequest;
import com.hadilao.be.modules.plan.enums.PlanAccessRole;
import com.hadilao.be.modules.plan.enums.PlanInvitationStatus;
import com.hadilao.be.modules.plan.service.PlanInvitationService;
import com.hadilao.be.modules.plan.service.PlanService;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlanControllerTest {

    private static final String AUTHENTICATED_EMAIL = "owner@example.com";
    private static final Instant NOW = Instant.parse("2026-08-05T08:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlanService planService;

    @MockBean
    private PlanInvitationService planInvitationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MailService mailService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Test
    void anonymousPlanRequestsReceiveTheStructuredAuthenticationError() throws Exception {
        mockMvc.perform(get("/api/v1/plans").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        verify(planService, never()).getPlans();
    }

    @Test
    void syncReturnsTheServerIdentityVersionAndAccessRole() throws Exception {
        UUID planId = UUID.randomUUID();
        PlanDTO plan = planDto(planId);
        when(planService.syncPlan(any(PlanSyncRequest.class))).thenReturn(plan);

        mockMvc.perform(post("/api/v1/plans/sync")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSyncJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Plan synchronized"))
                .andExpect(jsonPath("$.data.id").value(planId.toString()))
                .andExpect(jsonPath("$.data.clientPlanId").value("local-plan"))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.accessRole").value("OWNER"))
                .andExpect(jsonPath("$.data.owner.email").doesNotExist());

        verify(planService).syncPlan(any(PlanSyncRequest.class));
    }

    @Test
    void syncValidationAndConcurrencyErrorsKeepTheirStableErrorCodes() throws Exception {
        mockMvc.perform(post("/api/v1/plans/sync")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PLAN_DATA"));
        verify(planService, never()).syncPlan(any(PlanSyncRequest.class));

        when(planService.syncPlan(any(PlanSyncRequest.class)))
                .thenThrow(new AppException(ErrorCode.CONCURRENCY_CONFLICT));
        mockMvc.perform(post("/api/v1/plans/sync")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSyncJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENCY_CONFLICT"));
    }

    @Test
    void ownerCanSendAnInvitationWithoutExposingPrivateUserFields() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        FriendUserDTO owner = friend(UUID.randomUUID(), "Owner", "RML-100001");
        FriendUserDTO invitee = friend(inviteeId, "Invitee", "RML-100002");
        PlanInvitationDTO invitation = PlanInvitationDTO.builder()
                .id(invitationId)
                .plan(PlanSummaryDTO.builder()
                        .id(planId)
                        .name("Weekend plan")
                        .date(LocalDate.of(2026, 8, 8))
                        .itemCount(2)
                        .owner(owner)
                        .build())
                .inviter(owner)
                .invitee(invitee)
                .status(PlanInvitationStatus.PENDING)
                .sentAt(NOW)
                .build();
        when(planInvitationService.sendInvitation(planId, inviteeId)).thenReturn(invitation);

        mockMvc.perform(post("/api/v1/plans/{planId}/invitations", planId)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteBody(inviteeId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Plan invitation sent"))
                .andExpect(jsonPath("$.data.id").value(invitationId.toString()))
                .andExpect(jsonPath("$.data.plan.itemCount").value(2))
                .andExpect(jsonPath("$.data.invitee.id").value(inviteeId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.invitee.email").doesNotExist());

        verify(planInvitationService).sendInvitation(planId, inviteeId);
    }

    @Test
    void invitationBodyRequiresAnInviteeId() throws Exception {
        UUID planId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/plans/{planId}/invitations", planId)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PLAN_INVITEE_ID"));

        verify(planInvitationService, never()).sendInvitation(eq(planId), any(UUID.class));
    }

    @Test
    void invitationAndMembershipLifecycleRoutesCallTheExpectedOperations() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        PlanDTO accepted = planDto(planId);
        PlanInvitationDTO declined = PlanInvitationDTO.builder()
                .id(invitationId)
                .status(PlanInvitationStatus.DECLINED)
                .respondedAt(NOW)
                .build();
        when(planInvitationService.acceptInvitation(invitationId)).thenReturn(accepted);
        when(planInvitationService.declineInvitation(invitationId)).thenReturn(declined);

        mockMvc.perform(post("/api/v1/plan-invitations/{id}/accept", invitationId)
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId.toString()))
                .andExpect(jsonPath("$.message").value("Plan invitation accepted"));

        mockMvc.perform(post("/api/v1/plan-invitations/{id}/decline", invitationId)
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));

        mockMvc.perform(delete("/api/v1/plan-invitations/{id}", invitationId)
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Plan invitation cancelled"));

        mockMvc.perform(delete("/api/v1/plans/{planId}/members/{userId}", planId, memberId)
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Plan member removed"));

        mockMvc.perform(delete("/api/v1/plans/{planId}/membership", planId)
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Plan membership removed"));

        verify(planInvitationService).acceptInvitation(invitationId);
        verify(planInvitationService).declineInvitation(invitationId);
        verify(planInvitationService).cancelInvitation(invitationId);
        verify(planService).removeMember(planId, memberId);
        verify(planService).leavePlan(planId);
    }

    @Test
    void malformedResourceIdsAreRejectedBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/plan-invitations/not-a-uuid/accept")
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));

        verify(planInvitationService, never()).acceptInvitation(any(UUID.class));
    }

    private String validSyncJson() throws Exception {
        return objectMapper.writeValueAsString(new ValidSyncBody(
                "local-plan",
                "Weekend plan",
                LocalDate.of(2026, 8, 8),
                NOW,
                null,
                List.of()
        ));
    }

    private PlanDTO planDto(UUID planId) {
        return PlanDTO.builder()
                .id(planId)
                .clientPlanId("local-plan")
                .name("Weekend plan")
                .date(LocalDate.of(2026, 8, 8))
                .clientUpdatedAt(NOW)
                .version(3)
                .accessRole(PlanAccessRole.OWNER)
                .owner(friend(UUID.randomUUID(), "Owner", "RML-100001"))
                .members(List.of())
                .items(List.of())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private FriendUserDTO friend(UUID id, String name, String pinCode) {
        return FriendUserDTO.builder()
                .id(id)
                .fullName(name)
                .pinCode(pinCode)
                .build();
    }

    private record InviteBody(UUID inviteeId) {
    }

    private record ValidSyncBody(
            String clientPlanId,
            String name,
            LocalDate date,
            Instant clientUpdatedAt,
            Long expectedVersion,
            List<Object> items
    ) {
    }
}
