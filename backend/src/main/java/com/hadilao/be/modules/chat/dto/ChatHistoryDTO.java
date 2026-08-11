package com.hadilao.be.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryDTO {
    private UUID friendId;
    private List<ChatMessageDTO> messages;
    private boolean hasMore;
    private Instant nextBefore;
    private UUID nextBeforeId;
}
