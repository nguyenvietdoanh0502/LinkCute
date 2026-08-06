package com.hadilao.be.modules.friendship.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestDTO {
    private UUID id;
    private FriendUserDTO user;
    private Instant createdAt;
}
