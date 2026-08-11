package com.hadilao.be.modules.plan.dto;

import com.hadilao.be.modules.friendship.dto.FriendUserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanMemberDTO {
    private FriendUserDTO user;
    private Instant joinedAt;
}
