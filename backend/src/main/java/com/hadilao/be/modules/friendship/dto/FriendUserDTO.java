package com.hadilao.be.modules.friendship.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendUserDTO {
    private UUID id;
    private String fullName;
    private String avatarUrl;
    private String pinCode;
}
