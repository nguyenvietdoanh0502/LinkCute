package com.hadilao.be.modules.chat.dto;

import com.hadilao.be.modules.chat.enums.ChatMessageType;
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
public class ChatMessageDTO {
    private UUID id;
    private UUID clientMessageId;
    private UUID senderId;
    private UUID recipientId;
    private ChatMessageType messageType;
    private String content;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    private Instant createdAt;
}
