package com.hadilao.be.modules.friendship.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendRequestRequest {

    @NotNull(message = "MISSING_ADDRESSEE_ID")
    private UUID addresseeId;
}
