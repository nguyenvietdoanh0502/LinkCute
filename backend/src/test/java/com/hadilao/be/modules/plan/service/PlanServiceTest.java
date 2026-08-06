package com.hadilao.be.modules.plan.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.repository.PlaceRepository;
import com.hadilao.be.modules.plan.dto.PlanDTO;
import com.hadilao.be.modules.plan.dto.PlanSyncItemRequest;
import com.hadilao.be.modules.plan.dto.PlanSyncPlaceRequest;
import com.hadilao.be.modules.plan.dto.PlanSyncRequest;
import com.hadilao.be.modules.plan.entity.Plan;
import com.hadilao.be.modules.plan.entity.PlanItem;
import com.hadilao.be.modules.plan.entity.PlanMember;
import com.hadilao.be.modules.plan.repository.PlanItemRepository;
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
import java.time.LocalTime;
import java.util.List;
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
class PlanServiceTest {

    private static final Instant CLIENT_TIME = Instant.parse("2026-08-05T08:15:30Z");

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanItemRepository planItemRepository;

    @Mock
    private PlanMemberRepository planMemberRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlanMapper planMapper;

    @InjectMocks
    private PlanService planService;

    @Test
    void syncCreatesAServerPlanAndNormalizesTheFullItemSnapshot() {
        User owner = activeUser("owner@example.com");
        UUID firstPlaceId = UUID.randomUUID();
        UUID secondPlaceId = UUID.randomUUID();
        UUID persistedPlanId = UUID.randomUUID();
        PlanDTO expected = PlanDTO.builder().id(persistedPlanId).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(planRepository.findOwnedByClientPlanIdForUpdate(owner.getId(), "local-plan"))
                .thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(placeRepository.findAllById(any())).thenReturn(List.of(
                Place.builder().id(firstPlaceId).build(),
                Place.builder().id(secondPlaceId).build()
        ));
        when(planRepository.saveAndFlush(any(Plan.class))).thenAnswer(invocation -> {
            Plan saved = invocation.getArgument(0);
            saved.setId(persistedPlanId);
            return saved;
        });
        when(planMapper.toPlanDTO(any(Plan.class), any(UUID.class))).thenReturn(expected);

        PlanSyncRequest request = request(
                "  local-plan  ",
                CLIENT_TIME,
                null,
                List.of(
                        item("second-item", 1, secondPlaceId, " Second place ", 11, 0, 12, 0),
                        item("first-item", 0, firstPlaceId, " First place ", 9, 0, 10, 0)
                )
        );
        request.getItems().get(1).getPlace().setLat(null);
        request.getItems().get(1).getPlace().setLng(null);

        PlanDTO result = planService.syncPlan(request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).saveAndFlush(planCaptor.capture());
        assertThat(planCaptor.getValue()).satisfies(plan -> {
            assertThat(plan.getOwner()).isSameAs(owner);
            assertThat(plan.getClientPlanId()).isEqualTo("local-plan");
            assertThat(plan.getName()).isEqualTo("Weekend plan");
            assertThat(plan.getDate()).isEqualTo(LocalDate.of(2026, 8, 8));
            assertThat(plan.getClientUpdatedAt()).isEqualTo(CLIENT_TIME);
        });
        verify(planItemRepository).deleteAllByPlanId(persistedPlanId);
        verify(planItemRepository).flush();

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<PlanItem>> itemsCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
        verify(planItemRepository).saveAllAndFlush(itemsCaptor.capture());
        List<PlanItem> savedItems = ((List<PlanItem>) itemsCaptor.getValue());
        assertThat(savedItems).extracting(PlanItem::getClientItemId)
                .containsExactly("first-item", "second-item");
        assertThat(savedItems).extracting(PlanItem::getPosition).containsExactly(0, 1);
        assertThat(savedItems.get(0)).satisfies(item -> {
            assertThat(item.getPlan().getId()).isEqualTo(persistedPlanId);
            assertThat(item.getPlaceId()).isEqualTo(firstPlaceId);
            assertThat(item.getPlaceName()).isEqualTo("First place");
            assertThat(item.getPlaceLat()).isNull();
            assertThat(item.getPlaceLng()).isNull();
            assertThat(item.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(item.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        });
    }

    @Test
    void simultaneousFirstPublishConvergesOnThePlanCreatedWhileTheOwnerWasLocked() {
        User owner = activeUser("owner@example.com");
        Plan winner = plan(owner);
        PlanDTO expected = PlanDTO.builder().id(winner.getId()).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(placeRepository.findAllById(any())).thenReturn(List.of());
        when(planRepository.findOwnedByClientPlanIdForUpdate(owner.getId(), winner.getClientPlanId()))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(planMapper.toPlanDTO(winner, owner.getId())).thenReturn(expected);
        PlanSyncRequest request = request(
                winner.getClientPlanId(),
                winner.getClientUpdatedAt(),
                null,
                List.of()
        );

        PlanDTO result = planService.syncPlan(request);

        assertThat(result).isSameAs(expected);
        verify(planRepository, never()).saveAndFlush(any(Plan.class));
        verifyNoInteractions(planItemRepository);
    }

    @Test
    void syncRejectsAStaleExpectedVersionBeforeReplacingItems() {
        User owner = activeUser("owner@example.com");
        Plan stored = plan(owner);
        stored.setVersion(5);
        stored.setClientUpdatedAt(CLIENT_TIME);
        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(placeRepository.findAllById(any())).thenReturn(List.of());
        when(planRepository.findOwnedByClientPlanIdForUpdate(owner.getId(), stored.getClientPlanId()))
                .thenReturn(Optional.of(stored));
        PlanSyncRequest request = request(
                stored.getClientPlanId(),
                CLIENT_TIME.plusSeconds(1),
                4L,
                List.of()
        );

        assertError(() -> planService.syncPlan(request), ErrorCode.CONCURRENCY_CONFLICT);

        verify(planRepository, never()).saveAndFlush(any(Plan.class));
        verifyNoInteractions(planItemRepository);
        verifyNoInteractions(planMapper);
    }

    @Test
    void syncRejectsDuplicatePlacesAndInvalidTimeRangesBeforeWriting() {
        User owner = activeUser("owner@example.com");
        UUID placeId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        PlanSyncRequest duplicatePlaceRequest = request(
                "local-plan",
                CLIENT_TIME,
                null,
                List.of(
                        item("first", 0, placeId, "First", 10, 0, 11, 0),
                        item("second", 1, placeId, "Second", 11, 0, 12, 0)
                )
        );

        assertError(() -> planService.syncPlan(duplicatePlaceRequest), ErrorCode.INVALID_PLAN_DATA);

        PlanSyncRequest invalidTimeRequest = request(
                "local-plan",
                CLIENT_TIME,
                null,
                List.of(item("first", 0, UUID.randomUUID(), "First", 12, 0, 11, 0))
        );
        assertError(() -> planService.syncPlan(invalidTimeRequest), ErrorCode.INVALID_PLAN_DATA);

        verifyNoInteractions(planRepository, planItemRepository, planMapper);
    }

    @Test
    void aNonOwnerCannotDeleteAPlanEvenWhenTheyKnowItsId() {
        User owner = activeUser("owner@example.com");
        User member = activeUser("member@example.com");
        Plan stored = plan(owner);
        when(currentUserProvider.getCurrentUser()).thenReturn(member);
        when(planRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));

        assertError(() -> planService.deletePlan(stored.getId()), ErrorCode.PLAN_NOT_FOUND);

        verify(planRepository, never()).delete(any(Plan.class));
    }

    @Test
    void aMemberCanLeaveButTheOwnerCannot() {
        User owner = activeUser("owner@example.com");
        User member = activeUser("member@example.com");
        Plan stored = plan(owner);
        PlanMember membership = PlanMember.builder()
                .id(UUID.randomUUID())
                .plan(stored)
                .user(member)
                .build();
        when(currentUserProvider.getCurrentUser()).thenReturn(member);
        when(planRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));
        when(planMemberRepository.findByPlanIdAndUserId(stored.getId(), member.getId()))
                .thenReturn(Optional.of(membership));

        planService.leavePlan(stored.getId());

        verify(planMemberRepository).delete(membership);
        verify(planMemberRepository).flush();

        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        assertError(() -> planService.leavePlan(stored.getId()), ErrorCode.PLAN_OWNER_CANNOT_LEAVE);
    }

    private PlanSyncRequest request(
            String clientPlanId,
            Instant clientUpdatedAt,
            Long expectedVersion,
            List<PlanSyncItemRequest> items
    ) {
        return PlanSyncRequest.builder()
                .clientPlanId(clientPlanId)
                .name("  Weekend plan  ")
                .date(LocalDate.of(2026, 8, 8))
                .clientUpdatedAt(clientUpdatedAt)
                .expectedVersion(expectedVersion)
                .items(items)
                .build();
    }

    private PlanSyncItemRequest item(
            String clientItemId,
            int position,
            UUID placeId,
            String placeName,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        return PlanSyncItemRequest.builder()
                .clientItemId(clientItemId)
                .position(position)
                .place(PlanSyncPlaceRequest.builder()
                        .id(placeId)
                        .name(placeName)
                        .category("CAFE")
                        .lat(21.03)
                        .lng(105.85)
                        .priceMin(50_000.0)
                        .priceMax(100_000.0)
                        .build())
                .startTime(LocalTime.of(startHour, startMinute))
                .endTime(LocalTime.of(endHour, endMinute))
                .build();
    }

    private Plan plan(User owner) {
        return Plan.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .clientPlanId("local-plan")
                .name("Stored plan")
                .date(LocalDate.of(2026, 8, 8))
                .clientUpdatedAt(CLIENT_TIME)
                .createdAt(CLIENT_TIME)
                .updatedAt(CLIENT_TIME)
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
