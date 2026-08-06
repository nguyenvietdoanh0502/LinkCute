package com.hadilao.be.modules.plan.service;

import com.hadilao.be.modules.friendship.dto.FriendUserDTO;
import com.hadilao.be.modules.plan.dto.PlanDTO;
import com.hadilao.be.modules.plan.dto.PlanInvitationDTO;
import com.hadilao.be.modules.plan.dto.PlanItemDTO;
import com.hadilao.be.modules.plan.dto.PlanMemberDTO;
import com.hadilao.be.modules.plan.dto.PlanPlaceDTO;
import com.hadilao.be.modules.plan.dto.PlanSummaryDTO;
import com.hadilao.be.modules.plan.entity.Plan;
import com.hadilao.be.modules.plan.entity.PlanInvitation;
import com.hadilao.be.modules.plan.entity.PlanItem;
import com.hadilao.be.modules.plan.enums.PlanAccessRole;
import com.hadilao.be.modules.plan.repository.PlanItemRepository;
import com.hadilao.be.modules.plan.repository.PlanMemberRepository;
import com.hadilao.be.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlanMapper {

    private final PlanItemRepository planItemRepository;
    private final PlanMemberRepository planMemberRepository;

    public PlanDTO toPlanDTO(Plan plan, UUID currentUserId) {
        PlanAccessRole accessRole = plan.getOwner().getId().equals(currentUserId)
                ? PlanAccessRole.OWNER
                : PlanAccessRole.MEMBER;

        return PlanDTO.builder()
                .id(plan.getId())
                .clientPlanId(plan.getClientPlanId())
                .name(plan.getName())
                .date(plan.getDate())
                .clientUpdatedAt(plan.getClientUpdatedAt())
                .version(plan.getVersion())
                .accessRole(accessRole)
                .owner(toFriendUserDTO(plan.getOwner()))
                .members(planMemberRepository.findAllForPlan(plan.getId()).stream()
                        .map(member -> PlanMemberDTO.builder()
                                .user(toFriendUserDTO(member.getUser()))
                                .joinedAt(member.getJoinedAt())
                                .build())
                        .toList())
                .items(planItemRepository.findAllByPlanIdOrderByPositionAscIdAsc(plan.getId()).stream()
                        .map(this::toPlanItemDTO)
                        .toList())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    public PlanInvitationDTO toInvitationDTO(PlanInvitation invitation) {
        Plan plan = invitation.getPlan();
        return PlanInvitationDTO.builder()
                .id(invitation.getId())
                .plan(PlanSummaryDTO.builder()
                        .id(plan.getId())
                        .name(plan.getName())
                        .date(plan.getDate())
                        .itemCount(planItemRepository.countByPlanId(plan.getId()))
                        .owner(toFriendUserDTO(plan.getOwner()))
                        .build())
                .inviter(toFriendUserDTO(invitation.getInviter()))
                .invitee(toFriendUserDTO(invitation.getInvitee()))
                .status(invitation.getStatus())
                .sentAt(invitation.getSentAt())
                .respondedAt(invitation.getRespondedAt())
                .build();
    }

    public FriendUserDTO toFriendUserDTO(User user) {
        return FriendUserDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .pinCode(user.getPinCode())
                .build();
    }

    private PlanItemDTO toPlanItemDTO(PlanItem item) {
        return PlanItemDTO.builder()
                .id(item.getId())
                .clientItemId(item.getClientItemId())
                .position(item.getPosition())
                .place(PlanPlaceDTO.builder()
                        .id(item.getPlaceId())
                        .name(item.getPlaceName())
                        .address(item.getPlaceAddress())
                        .district(item.getPlaceDistrict())
                        .category(item.getPlaceCategory())
                        .photoUrl(item.getPlacePhotoUrl())
                        .lat(item.getPlaceLat())
                        .lng(item.getPlaceLng())
                        .priceLevel(item.getPlacePriceLevel())
                        .priceMin(item.getPlacePriceMin())
                        .priceMax(item.getPlacePriceMax())
                        .build())
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .build();
    }
}
