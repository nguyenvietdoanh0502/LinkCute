package com.hadilao.be.modules.plan.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.service.MailService;
import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.repository.PlaceRepository;
import com.hadilao.be.modules.plan.dto.PlanDTO;
import com.hadilao.be.modules.plan.dto.PlanInvitationDTO;
import com.hadilao.be.modules.plan.dto.PlanSyncItemRequest;
import com.hadilao.be.modules.plan.dto.PlanSyncPlaceRequest;
import com.hadilao.be.modules.plan.dto.PlanSyncRequest;
import com.hadilao.be.modules.plan.enums.PlanAccessRole;
import com.hadilao.be.modules.plan.enums.PlanInvitationStatus;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan_sharing_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlanSharingIntegrationTest {

    @MockBean
    private MailService mailService;

    @Autowired
    private PlanService planService;

    @Autowired
    private PlanInvitationService planInvitationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerInvitesFriendWhoAcceptsReadsUpdatesAndLeavesSharedPlan() {
        User owner = saveUser("owner@plan.test", "Owner", "111111");
        User friend = saveUser("friend@plan.test", "Friend", "222222");
        User outsider = saveUser("outsider@plan.test", "Outsider", "333333");
        saveAcceptedFriendship(owner, friend);

        Place place = placeRepository.saveAndFlush(Place.builder()
                .name("Plan integration cafe")
                .address("1 Test Street")
                .district("District 1")
                .lat(10.7769)
                .lng(106.7009)
                .category(PlaceCategory.CAFE)
                .build());

        Instant firstClientUpdate = Instant.parse("2026-08-05T03:00:00Z");
        authenticate(owner);
        PlanDTO published = planService.syncPlan(syncRequest(
                "client-plan-integration",
                "Morning plan",
                firstClientUpdate,
                null,
                place,
                LocalTime.of(9, 0)
        ));

        assertThat(published.getAccessRole()).isEqualTo(PlanAccessRole.OWNER);
        assertThat(published.getItems()).hasSize(1);
        assertThat(published.getItems().get(0).getPlace().getId()).isEqualTo(place.getId());

        PlanInvitationDTO invitation = planInvitationService.sendInvitation(
                published.getId(), friend.getId());
        assertThat(invitation.getStatus()).isEqualTo(PlanInvitationStatus.PENDING);
        assertThat(invitation.getPlan().getItemCount()).isEqualTo(1);

        authenticate(friend);
        assertThat(planInvitationService.getIncomingInvitations())
                .extracting(PlanInvitationDTO::getId)
                .containsExactly(invitation.getId());

        PlanDTO accepted = planInvitationService.acceptInvitation(invitation.getId());
        assertThat(accepted.getAccessRole()).isEqualTo(PlanAccessRole.MEMBER);
        assertThat(accepted.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(accepted.getItems()).hasSize(1);
        assertThat(planInvitationService.getIncomingInvitations()).isEmpty();

        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> planService.deletePlan(published.getId()))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

        authenticate(owner);
        Instant secondClientUpdate = firstClientUpdate.plusSeconds(60);
        PlanDTO updated = planService.syncPlan(syncRequest(
                published.getClientPlanId(),
                "Updated morning plan",
                secondClientUpdate,
                published.getVersion(),
                place,
                LocalTime.of(10, 30)
        ));
        assertThat(updated.getVersion()).isGreaterThan(published.getVersion());
        assertThat(updated.getMembers())
                .extracting(member -> member.getUser().getId())
                .containsExactly(friend.getId());

        authenticate(friend);
        PlanDTO sharedPlan = planService.getPlan(published.getId());
        assertThat(sharedPlan.getAccessRole()).isEqualTo(PlanAccessRole.MEMBER);
        assertThat(sharedPlan.getName()).isEqualTo("Updated morning plan");
        assertThat(sharedPlan.getClientUpdatedAt()).isEqualTo(secondClientUpdate);
        assertThat(sharedPlan.getItems().get(0).getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(planService.getPlans())
                .extracting(PlanDTO::getId)
                .contains(published.getId());

        authenticate(outsider);
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> planService.getPlan(published.getId()))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

        authenticate(friend);
        planService.leavePlan(published.getId());
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> planService.getPlan(published.getId()))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

        authenticate(owner);
        assertThat(planService.getPlan(published.getId()).getMembers()).isEmpty();
    }

    private User saveUser(String email, String fullName, String pinCode) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("test-password")
                .fullName(fullName)
                .pinCode(pinCode)
                .status(AccountStatus.ACTIVE)
                .isDeleted(false)
                .build());
    }

    private void saveAcceptedFriendship(User first, User second) {
        List<User> ordered = List.of(first, second).stream()
                .sorted(Comparator.comparing(user -> user.getId().toString()))
                .toList();
        friendshipRepository.saveAndFlush(Friendship.builder()
                .userLow(ordered.get(0))
                .userHigh(ordered.get(1))
                .requester(first)
                .status(FriendshipStatus.ACCEPTED)
                .acceptedAt(Instant.now())
                .build());
    }

    private PlanSyncRequest syncRequest(
            String clientPlanId,
            String name,
            Instant clientUpdatedAt,
            Long expectedVersion,
            Place place,
            LocalTime startTime
    ) {
        return PlanSyncRequest.builder()
                .clientPlanId(clientPlanId)
                .name(name)
                .date(LocalDate.of(2026, 8, 10))
                .clientUpdatedAt(clientUpdatedAt)
                .expectedVersion(expectedVersion)
                .items(List.of(PlanSyncItemRequest.builder()
                        .clientItemId("client-item-1")
                        .position(0)
                        .place(PlanSyncPlaceRequest.builder()
                                .id(place.getId())
                                .name(place.getName())
                                .address(place.getAddress())
                                .district(place.getDistrict())
                                .category(place.getCategory().name())
                                .lat(place.getLat())
                                .lng(place.getLng())
                                .build())
                        .startTime(startTime)
                        .endTime(startTime.plusHours(1))
                        .build()))
                .build();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "test", List.of()));
    }
}
