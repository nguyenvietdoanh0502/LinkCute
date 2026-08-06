package com.hadilao.be.modules.plan.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.plan.dto.PlanDTO;
import com.hadilao.be.modules.plan.dto.PlanInvitationDTO;
import com.hadilao.be.modules.plan.entity.Plan;
import com.hadilao.be.modules.plan.entity.PlanInvitation;
import com.hadilao.be.modules.plan.entity.PlanMember;
import com.hadilao.be.modules.plan.enums.PlanInvitationStatus;
import com.hadilao.be.modules.plan.repository.PlanInvitationRepository;
import com.hadilao.be.modules.plan.repository.PlanMemberRepository;
import com.hadilao.be.modules.plan.repository.PlanRepository;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanInvitationService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PlanInvitationRepository planInvitationRepository;
    private final PlanMemberRepository planMemberRepository;
    private final FriendshipRepository friendshipRepository;
    private final PlanMapper planMapper;

    @Transactional(readOnly = true)
    public List<PlanInvitationDTO> getIncomingInvitations() {
        User currentUser = currentUserProvider.getCurrentUser();
        return planInvitationRepository.findIncomingByStatus(
                        currentUser.getId(),
                        PlanInvitationStatus.PENDING)
                .stream()
                .map(planMapper::toInvitationDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanInvitationDTO> getOutgoingInvitations() {
        User currentUser = currentUserProvider.getCurrentUser();
        return planInvitationRepository.findOutgoingByStatus(
                        currentUser.getId(),
                        PlanInvitationStatus.PENDING)
                .stream()
                .map(planMapper::toInvitationDTO)
                .toList();
    }

    @Transactional
    public PlanInvitationDTO sendInvitation(UUID planId, UUID inviteeId) {
        User currentUser = currentUserProvider.getCurrentUser();
        Plan plan = getOwnedPlanForUpdate(planId, currentUser.getId());

        if (currentUser.getId().equals(inviteeId)) {
            throw new AppException(ErrorCode.CANNOT_INVITE_SELF_TO_PLAN);
        }

        User invitee = userRepository
                .findByIdAndIsDeletedFalseAndStatus(inviteeId, AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        requireAcceptedFriendship(currentUser.getId(), inviteeId);

        if (planMemberRepository.existsByPlanIdAndUserId(planId, inviteeId)) {
            throw new AppException(ErrorCode.PLAN_MEMBER_ALREADY_EXISTS);
        }

        PlanInvitation invitation = planInvitationRepository
                .findForUpdateByPlanAndInvitee(planId, inviteeId)
                .orElse(null);
        if (invitation != null && invitation.getStatus() == PlanInvitationStatus.PENDING) {
            throw new AppException(ErrorCode.PLAN_INVITATION_ALREADY_PENDING);
        }

        if (invitation == null) {
            invitation = PlanInvitation.builder()
                    .plan(plan)
                    .inviter(currentUser)
                    .invitee(invitee)
                    .status(PlanInvitationStatus.PENDING)
                    .build();
        } else {
            invitation.resetPending(currentUser);
        }

        try {
            return planMapper.toInvitationDTO(planInvitationRepository.saveAndFlush(invitation));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.PLAN_INVITATION_ALREADY_PENDING);
        }
    }

    @Transactional
    public PlanDTO acceptInvitation(UUID invitationId) {
        User currentUser = currentUserProvider.getCurrentUser();
        LockedInvitation locked = lockInvitationAndPlan(invitationId);
        PlanInvitation invitation = locked.invitation();

        requirePendingInvitee(invitation, currentUser.getId());
        if (!currentUserProvider.isAvailable(locked.plan().getOwner())) {
            throw new AppException(ErrorCode.PLAN_INVITATION_NOT_FOUND);
        }
        requireAcceptedFriendship(locked.plan().getOwner().getId(), currentUser.getId());

        if (planMemberRepository.existsByPlanIdAndUserId(locked.plan().getId(), currentUser.getId())) {
            throw new AppException(ErrorCode.PLAN_MEMBER_ALREADY_EXISTS);
        }

        invitation.accept();
        planInvitationRepository.saveAndFlush(invitation);

        PlanMember member = PlanMember.builder()
                .plan(locked.plan())
                .user(currentUser)
                .invitation(invitation)
                .build();
        try {
            planMemberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.PLAN_MEMBER_ALREADY_EXISTS);
        }

        return planMapper.toPlanDTO(locked.plan(), currentUser.getId());
    }

    @Transactional
    public PlanInvitationDTO declineInvitation(UUID invitationId) {
        User currentUser = currentUserProvider.getCurrentUser();
        LockedInvitation locked = lockInvitationAndPlan(invitationId);
        requirePendingInvitee(locked.invitation(), currentUser.getId());
        locked.invitation().decline();
        return planMapper.toInvitationDTO(
                planInvitationRepository.saveAndFlush(locked.invitation()));
    }

    @Transactional
    public void cancelInvitation(UUID invitationId) {
        User currentUser = currentUserProvider.getCurrentUser();
        LockedInvitation locked = lockInvitationAndPlan(invitationId);
        PlanInvitation invitation = locked.invitation();
        if (!locked.plan().getOwner().getId().equals(currentUser.getId())
                || invitation.getStatus() != PlanInvitationStatus.PENDING) {
            throw new AppException(ErrorCode.PLAN_INVITATION_NOT_FOUND);
        }
        invitation.cancel();
        planInvitationRepository.saveAndFlush(invitation);
    }

    private Plan getOwnedPlanForUpdate(UUID planId, UUID currentUserId) {
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
        if (!plan.getOwner().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }

    private LockedInvitation lockInvitationAndPlan(UUID invitationId) {
        PlanInvitation snapshot = planInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_INVITATION_NOT_FOUND));
        UUID planId = snapshot.getPlan().getId();
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_INVITATION_NOT_FOUND));
        PlanInvitation invitation = planInvitationRepository.findByIdForUpdate(invitationId)
                .filter(candidate -> candidate.getPlan().getId().equals(planId))
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_INVITATION_NOT_FOUND));
        return new LockedInvitation(plan, invitation);
    }

    private void requirePendingInvitee(PlanInvitation invitation, UUID currentUserId) {
        if (!invitation.getInvitee().getId().equals(currentUserId)
                || invitation.getStatus() != PlanInvitationStatus.PENDING) {
            throw new AppException(ErrorCode.PLAN_INVITATION_NOT_FOUND);
        }
    }

    private void requireAcceptedFriendship(UUID firstUserId, UUID secondUserId) {
        UUID lowId;
        UUID highId;
        if (firstUserId.toString().compareTo(secondUserId.toString()) < 0) {
            lowId = firstUserId;
            highId = secondUserId;
        } else {
            lowId = secondUserId;
            highId = firstUserId;
        }
        friendshipRepository.findAcceptedPairForUpdate(lowId, highId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_INVITEE_NOT_FRIEND));
    }

    private record LockedInvitation(Plan plan, PlanInvitation invitation) {
    }
}
