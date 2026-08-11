package com.hadilao.be.modules.plan.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
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
import com.hadilao.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {

    private static final int MAX_PLAN_ITEMS = 100;

    private final CurrentUserProvider currentUserProvider;
    private final PlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final PlanMemberRepository planMemberRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final PlanMapper planMapper;

    @Transactional(readOnly = true)
    public List<PlanDTO> getPlans() {
        User currentUser = currentUserProvider.getCurrentUser();
        return planRepository.findAllAccessibleByUserId(currentUser.getId()).stream()
                .map(plan -> planMapper.toPlanDTO(plan, currentUser.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanDTO getPlan(UUID planId) {
        User currentUser = currentUserProvider.getCurrentUser();
        Plan plan = getAccessiblePlan(planId, currentUser.getId());
        return planMapper.toPlanDTO(plan, currentUser.getId());
    }

    @Transactional
    public PlanDTO syncPlan(PlanSyncRequest request) {
        User currentUser = currentUserProvider.getCurrentUser();
        NormalizedPlan normalized = normalize(request);
        Plan plan = planRepository.findOwnedByClientPlanIdForUpdate(
                        currentUser.getId(),
                        normalized.clientPlanId())
                .orElse(null);

        if (plan == null) {
            // An absent row cannot itself be locked. Serialize first publish on
            // the owner row, then re-check so simultaneous tabs converge on the
            // same server plan instead of making the loser handle a unique-key
            // error without a server id.
            User lockedOwner = userRepository.findByIdForUpdate(currentUser.getId())
                    .filter(User::isEnabled)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
            plan = planRepository.findOwnedByClientPlanIdForUpdate(
                            currentUser.getId(),
                            normalized.clientPlanId())
                    .orElse(null);
            if (plan != null) {
                return syncExistingPlan(plan, request, normalized, currentUser.getId());
            }
            if (request.getExpectedVersion() != null) {
                throw new AppException(ErrorCode.CONCURRENCY_CONFLICT);
            }
            plan = Plan.builder()
                    .owner(lockedOwner)
                    .clientPlanId(normalized.clientPlanId())
                    .name(normalized.name())
                    .date(normalized.date())
                    .clientUpdatedAt(normalized.clientUpdatedAt())
                    .build();
            try {
                plan = planRepository.saveAndFlush(plan);
            } catch (DataIntegrityViolationException exception) {
                throw new AppException(ErrorCode.CONCURRENCY_CONFLICT);
            }
        } else {
            return syncExistingPlan(plan, request, normalized, currentUser.getId());
        }

        replaceItems(plan, normalized.items());
        return planMapper.toPlanDTO(plan, currentUser.getId());
    }

    private PlanDTO syncExistingPlan(
            Plan plan,
            PlanSyncRequest request,
            NormalizedPlan normalized,
            UUID currentUserId
    ) {
        int timeComparison = normalized.clientUpdatedAt().compareTo(plan.getClientUpdatedAt());
        if (timeComparison == 0) {
            return planMapper.toPlanDTO(plan, currentUserId);
        }
        if (timeComparison < 0
                || request.getExpectedVersion() == null
                || request.getExpectedVersion() != plan.getVersion()) {
            throw new AppException(ErrorCode.CONCURRENCY_CONFLICT);
        }

        plan.applySync(normalized.name(), normalized.date(), normalized.clientUpdatedAt());
        try {
            // Flush the aggregate first so the returned DTO contains the
            // incremented optimistic-lock version.
            Plan saved = planRepository.saveAndFlush(plan);
            replaceItems(saved, normalized.items());
            return planMapper.toPlanDTO(saved, currentUserId);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.CONCURRENCY_CONFLICT);
        }
    }

    @Transactional
    public void deletePlan(UUID planId) {
        User currentUser = currentUserProvider.getCurrentUser();
        Plan plan = getOwnedPlanForUpdate(planId, currentUser.getId());
        planRepository.delete(plan);
        planRepository.flush();
    }

    @Transactional
    public void removeMember(UUID planId, UUID userId) {
        User currentUser = currentUserProvider.getCurrentUser();
        getOwnedPlanForUpdate(planId, currentUser.getId());
        PlanMember member = planMemberRepository.findByPlanIdAndUserId(planId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_MEMBER_NOT_FOUND));
        planMemberRepository.delete(member);
        planMemberRepository.flush();
    }

    @Transactional
    public void leavePlan(UUID planId) {
        User currentUser = currentUserProvider.getCurrentUser();
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
        if (plan.getOwner().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.PLAN_OWNER_CANNOT_LEAVE);
        }
        PlanMember member = planMemberRepository.findByPlanIdAndUserId(planId, currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
        planMemberRepository.delete(member);
        planMemberRepository.flush();
    }

    private Plan getAccessiblePlan(UUID planId, UUID currentUserId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
        if (!plan.getOwner().getId().equals(currentUserId)
                && !planMemberRepository.existsByPlanIdAndUserId(planId, currentUserId)) {
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }

    private Plan getOwnedPlanForUpdate(UUID planId, UUID currentUserId) {
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
        if (!plan.getOwner().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }

    private void replaceItems(Plan plan, List<NormalizedItem> normalizedItems) {
        // Hibernate may insert replacements before deleting old rows, which can
        // violate the per-plan unique keys. A bulk delete and explicit flush
        // gives full-snapshot replacement deterministic ordering.
        planItemRepository.deleteAllByPlanId(plan.getId());
        planItemRepository.flush();

        List<PlanItem> items = normalizedItems.stream()
                .map(item -> PlanItem.builder()
                        .plan(plan)
                        .clientItemId(item.clientItemId())
                        .position(item.position())
                        .placeId(item.place().getId())
                        .placeName(item.place().getName())
                        .placeAddress(item.place().getAddress())
                        .placeDistrict(item.place().getDistrict())
                        .placeCategory(item.place().getCategory())
                        .placePhotoUrl(item.place().getPhotoUrl())
                        .placeLat(item.place().getLat())
                        .placeLng(item.place().getLng())
                        .placePriceLevel(item.place().getPriceLevel())
                        .placePriceMin(item.place().getPriceMin())
                        .placePriceMax(item.place().getPriceMax())
                        .startTime(item.startTime())
                        .endTime(item.endTime())
                        .build())
                .toList();
        try {
            planItemRepository.saveAllAndFlush(items);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.INVALID_PLAN_DATA);
        }
    }

    private NormalizedPlan normalize(PlanSyncRequest request) {
        if (request == null
                || isBlank(request.getClientPlanId())
                || request.getClientPlanId().trim().length() > 128
                || isBlank(request.getName())
                || request.getName().trim().length() > 200
                || request.getDate() == null
                || request.getClientUpdatedAt() == null
                || request.getItems() == null
                || request.getItems().size() > MAX_PLAN_ITEMS) {
            throw new AppException(ErrorCode.INVALID_PLAN_DATA);
        }

        List<PlanSyncItemRequest> sorted = new ArrayList<>(request.getItems());
        if (sorted.stream().anyMatch(item -> item == null || item.getPosition() == null)) {
            throw new AppException(ErrorCode.INVALID_PLAN_DATA);
        }
        sorted.sort(Comparator.comparingInt(PlanSyncItemRequest::getPosition));

        Set<String> clientItemIds = new HashSet<>();
        Set<UUID> placeIds = new HashSet<>();
        List<NormalizedItem> items = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            PlanSyncItemRequest item = sorted.get(index);
            PlanSyncPlaceRequest place = item.getPlace();
            String clientItemId = trimmed(item.getClientItemId());
            if (item.getPosition() != index
                    || clientItemId == null
                    || clientItemId.length() > 128
                    || place == null
                    || place.getId() == null
                    || isBlank(place.getName())
                    || place.getName().trim().length() > 255
                    || isBlank(place.getCategory())
                    || place.getCategory().trim().length() > 50
                    || !clientItemIds.add(clientItemId)
                    || !placeIds.add(place.getId())
                    || !validPlaceNumbers(place)
                    || !validTimeRange(item)) {
                throw new AppException(ErrorCode.INVALID_PLAN_DATA);
            }

            PlanSyncPlaceRequest normalizedPlace = PlanSyncPlaceRequest.builder()
                    .id(place.getId())
                    .name(place.getName().trim())
                    .address(trimmed(place.getAddress()))
                    .district(trimmed(place.getDistrict()))
                    .category(place.getCategory().trim())
                    .photoUrl(trimmed(place.getPhotoUrl()))
                    .lat(place.getLat())
                    .lng(place.getLng())
                    .priceLevel(place.getPriceLevel())
                    .priceMin(place.getPriceMin())
                    .priceMax(place.getPriceMax())
                    .build();
            items.add(new NormalizedItem(
                    clientItemId,
                    index,
                    normalizedPlace,
                    item.getStartTime(),
                    item.getEndTime()
            ));
        }

        if (placeRepository.findAllById(placeIds).size() != placeIds.size()) {
            throw new AppException(ErrorCode.INVALID_PLAN_DATA);
        }

        return new NormalizedPlan(
                request.getClientPlanId().trim(),
                request.getName().trim(),
                request.getDate(),
                request.getClientUpdatedAt(),
                items
        );
    }

    private boolean validPlaceNumbers(PlanSyncPlaceRequest place) {
        return within(place.getLat(), -90, 90)
                && within(place.getLng(), -180, 180)
                && (place.getPriceLevel() == null || place.getPriceLevel() >= 0)
                && nonNegativeFinite(place.getPriceMin())
                && nonNegativeFinite(place.getPriceMax())
                && (place.getPriceMin() == null
                    || place.getPriceMax() == null
                    || place.getPriceMax() >= place.getPriceMin());
    }

    private boolean within(Double value, double minimum, double maximum) {
        return value == null || (Double.isFinite(value) && value >= minimum && value <= maximum);
    }

    private boolean nonNegativeFinite(Double value) {
        return value == null || (Double.isFinite(value) && value >= 0);
    }

    private boolean validTimeRange(PlanSyncItemRequest item) {
        return item.getStartTime() == null
                || item.getEndTime() == null
                || item.getEndTime().isAfter(item.getStartTime());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimmed(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private record NormalizedPlan(
            String clientPlanId,
            String name,
            java.time.LocalDate date,
            Instant clientUpdatedAt,
            List<NormalizedItem> items
    ) {
    }

    private record NormalizedItem(
            String clientItemId,
            int position,
            PlanSyncPlaceRequest place,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime
    ) {
    }
}
