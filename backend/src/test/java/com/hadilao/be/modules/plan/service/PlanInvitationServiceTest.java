package com.hadilao.be.modules.plan.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanInvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T08:15:30Z");

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanInvitationRepository planInvitationRepository;

    @Mock
    private PlanMemberRepository planMemberRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private PlanMapper planMapper;

    @InjectMocks
    private PlanInvitationService invitationService;

    @Test
    void ownerCanInviteAnAcceptedFriend() {
        User owner = activeUser("owner@example.com");
        User invitee = activeUser("invitee@example.com");
        Plan plan = plan(owner);
        PlanInvitationDTO expected = PlanInvitationDTO.builder().id(UUID.randomUUID()).build();
        stubOwnedPlanAndAcceptedFriend(plan, owner, invitee);
        when(planInvitationRepository.findForUpdateByPlanAndInvitee(plan.getId(), invitee.getId()))
                .thenReturn(Optional.empty());
        when(planMemberRepository.existsByPlanIdAndUserId(plan.getId(), invitee.getId()))
                .thenReturn(false);
        when(planInvitationRepository.saveAndFlush(any(PlanInvitation.class))).thenAnswer(invocation -> {
            PlanInvitation saved = invocation.getArgument(0);
            saved.setId(expected.getId());
            saved.setSentAt(NOW);
            saved.setCreatedAt(NOW);
            saved.setUpdatedAt(NOW);
            return saved;
        });
        when(planMapper.toInvitationDTO(any(PlanInvitation.class))).thenReturn(expected);

        PlanInvitationDTO result = invitationService.sendInvitation(plan.getId(), invitee.getId());

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<PlanInvitation> captor = ArgumentCaptor.forClass(PlanInvitation.class);
        verify(planInvitationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).satisfies(invitation -> {
            assertThat(invitation.getPlan()).isSameAs(plan);
            assertThat(invitation.getInviter()).isSameAs(owner);
            assertThat(invitation.getInvitee()).isSameAs(invitee);
            assertThat(invitation.getStatus()).isEqualTo(PlanInvitationStatus.PENDING);
            assertThat(invitation.getRespondedAt()).isNull();
        });
    }

    @Test
    void nonOwnerGetsNotFoundEvenWhenTheyKnowThePlanId() {
        User owner = activeUser("owner@example.com");
        User outsider = activeUser("outsider@example.com");
        Plan plan = plan(owner);
        when(currentUserProvider.getCurrentUser()).thenReturn(outsider);
        when(planRepository.findByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));

        assertError(
                () -> invitationService.sendInvitation(plan.getId(), UUID.randomUUID()),
                ErrorCode.PLAN_NOT_FOUND
        );

        verifyNoInteractions(userRepository, friendshipRepository, planInvitationRepository, planMapper);
    }

    @Test
    void invitationRequiresAnAcceptedFriendship() {
        User owner = activeUser("owner@example.com");
        User invitee = activeUser("invitee@example.com");
        Plan plan = plan(owner);
        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(planRepository.findByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(invitee.getId(), AccountStatus.ACTIVE))
                .thenReturn(Optional.of(invitee));
        when(friendshipRepository.findAcceptedPairForUpdate(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());

        assertError(
                () -> invitationService.sendInvitation(plan.getId(), invitee.getId()),
                ErrorCode.PLAN_INVITEE_NOT_FRIEND
        );

        verify(planInvitationRepository, never()).saveAndFlush(any(PlanInvitation.class));
        verifyNoInteractions(planMapper);
    }

    @Test
    void aTerminalInvitationIsReactivatedInsteadOfCreatingADuplicateRow() {
        User owner = activeUser("owner@example.com");
        User invitee = activeUser("invitee@example.com");
        Plan plan = plan(owner);
        PlanInvitation existing = invitation(plan, owner, invitee, PlanInvitationStatus.DECLINED);
        existing.setRespondedAt(NOW);
        stubOwnedPlanAndAcceptedFriend(plan, owner, invitee);
        when(planMemberRepository.existsByPlanIdAndUserId(plan.getId(), invitee.getId()))
                .thenReturn(false);
        when(planInvitationRepository.findForUpdateByPlanAndInvitee(plan.getId(), invitee.getId()))
                .thenReturn(Optional.of(existing));
        when(planInvitationRepository.saveAndFlush(existing)).thenReturn(existing);
        when(planMapper.toInvitationDTO(existing)).thenReturn(PlanInvitationDTO.builder().build());

        invitationService.sendInvitation(plan.getId(), invitee.getId());

        assertThat(existing.getStatus()).isEqualTo(PlanInvitationStatus.PENDING);
        assertThat(existing.getRespondedAt()).isNull();
        assertThat(existing.getInviter()).isSameAs(owner);
        verify(planInvitationRepository).saveAndFlush(existing);
    }

    @Test
    void inviteeCanAcceptAndMembershipIsLinkedToTheInvitation() {
        User owner = activeUser("owner@example.com");
        User invitee = activeUser("invitee@example.com");
        Plan plan = plan(owner);
        PlanInvitation invitation = invitation(plan, owner, invitee, PlanInvitationStatus.PENDING);
        PlanDTO expected = PlanDTO.builder().id(plan.getId()).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(invitee);
        when(currentUserProvider.isAvailable(owner)).thenReturn(true);
        stubLockedInvitation(plan, invitation);
        stubAcceptedFriendship(owner, invitee);
        when(planMemberRepository.existsByPlanIdAndUserId(plan.getId(), invitee.getId()))
                .thenReturn(false);
        when(planInvitationRepository.saveAndFlush(invitation)).thenReturn(invitation);
        when(planMemberRepository.saveAndFlush(any(PlanMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(planMapper.toPlanDTO(plan, invitee.getId())).thenReturn(expected);

        PlanDTO result = invitationService.acceptInvitation(invitation.getId());

        assertThat(result).isSameAs(expected);
        assertThat(invitation.getStatus()).isEqualTo(PlanInvitationStatus.ACCEPTED);
        assertThat(invitation.getRespondedAt()).isNotNull();
        ArgumentCaptor<PlanMember> memberCaptor = ArgumentCaptor.forClass(PlanMember.class);
        verify(planMemberRepository).saveAndFlush(memberCaptor.capture());
        assertThat(memberCaptor.getValue()).satisfies(member -> {
            assertThat(member.getPlan()).isSameAs(plan);
            assertThat(member.getUser()).isSameAs(invitee);
            assertThat(member.getInvitation()).isSameAs(invitation);
        });
    }

    @Test
    void acceptRechecksFriendshipAndAnOutsiderCannotAccept() {
        User owner = activeUser("owner@example.com");
        User invitee = activeUser("invitee@example.com");
        User outsider = activeUser("outsider@example.com");
        Plan plan = plan(owner);
        PlanInvitation invitation = invitation(plan, owner, invitee, PlanInvitationStatus.PENDING);
        stubLockedInvitation(plan, invitation);

        when(currentUserProvider.getCurrentUser()).thenReturn(outsider);
        assertError(
                () -> invitationService.acceptInvitation(invitation.getId()),
                ErrorCode.PLAN_INVITATION_NOT_FOUND
        );
        verifyNoInteractions(friendshipRepository, planMemberRepository, planMapper);

        when(currentUserProvider.getCurrentUser()).thenReturn(invitee);
        when(currentUserProvider.isAvailable(owner)).thenReturn(true);
        when(friendshipRepository.findAcceptedPairForUpdate(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());
        assertError(
                () -> invitationService.acceptInvitation(invitation.getId()),
                ErrorCode.PLAN_INVITEE_NOT_FRIEND
        );
        verify(planInvitationRepository, never()).saveAndFlush(any(PlanInvitation.class));
        verify(planMemberRepository, never()).saveAndFlush(any(PlanMember.class));
    }

    @Test
    void inviteeCanDeclineButOnlyOwnerCanCancel() {
        User owner = activeUser("owner@example.com");
        User invitee = activeUser("invitee@example.com");
        Plan plan = plan(owner);
        PlanInvitation invitation = invitation(plan, owner, invitee, PlanInvitationStatus.PENDING);
        stubLockedInvitation(plan, invitation);
        when(currentUserProvider.getCurrentUser()).thenReturn(invitee);
        when(planInvitationRepository.saveAndFlush(invitation)).thenReturn(invitation);
        when(planMapper.toInvitationDTO(invitation)).thenReturn(PlanInvitationDTO.builder().build());

        invitationService.declineInvitation(invitation.getId());

        assertThat(invitation.getStatus()).isEqualTo(PlanInvitationStatus.DECLINED);
        assertThat(invitation.getRespondedAt()).isNotNull();

        PlanInvitation secondInvitation = invitation(plan, owner, invitee, PlanInvitationStatus.PENDING);
        stubLockedInvitation(plan, secondInvitation);
        assertError(
                () -> invitationService.cancelInvitation(secondInvitation.getId()),
                ErrorCode.PLAN_INVITATION_NOT_FOUND
        );

        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        invitationService.cancelInvitation(secondInvitation.getId());
        assertThat(secondInvitation.getStatus()).isEqualTo(PlanInvitationStatus.CANCELLED);
        assertThat(secondInvitation.getRespondedAt()).isNotNull();
    }

    private void stubOwnedPlanAndAcceptedFriend(Plan plan, User owner, User invitee) {
        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(planRepository.findByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(invitee.getId(), AccountStatus.ACTIVE))
                .thenReturn(Optional.of(invitee));
        stubAcceptedFriendship(owner, invitee);
    }

    private void stubAcceptedFriendship(User first, User second) {
        Friendship accepted = Friendship.builder()
                .id(UUID.randomUUID())
                .userLow(first)
                .userHigh(second)
                .requester(first)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        when(friendshipRepository.findAcceptedPairForUpdate(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.of(accepted));
    }

    private void stubLockedInvitation(Plan plan, PlanInvitation invitation) {
        when(planInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(planRepository.findByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        when(planInvitationRepository.findByIdForUpdate(invitation.getId()))
                .thenReturn(Optional.of(invitation));
    }

    private PlanInvitation invitation(
            Plan plan,
            User inviter,
            User invitee,
            PlanInvitationStatus status
    ) {
        return PlanInvitation.builder()
                .id(UUID.randomUUID())
                .plan(plan)
                .inviter(inviter)
                .invitee(invitee)
                .status(status)
                .sentAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private Plan plan(User owner) {
        return Plan.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .clientPlanId("local-plan")
                .name("Weekend plan")
                .date(LocalDate.of(2026, 8, 8))
                .clientUpdatedAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private User activeUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .fullName(email.substring(0, email.indexOf('@')))
                .pinCode("RML-123456")
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            ErrorCode errorCode
    ) {
        assertThatThrownBy(operation)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }
}
