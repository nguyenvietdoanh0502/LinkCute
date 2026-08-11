package com.hadilao.be.modules.friendship.dto;

import com.hadilao.be.modules.friendship.enums.RelationshipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendSearchDTO {
    private UUID id;
    private String fullName;
    private String avatarUrl;
    private String pinCode;
    private RelationshipStatus relationshipStatus;
    private UUID friendshipId;
}
