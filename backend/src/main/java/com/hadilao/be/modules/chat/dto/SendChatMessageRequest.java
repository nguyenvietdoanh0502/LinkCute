package com.hadilao.be.modules.chat.dto;

import com.hadilao.be.modules.chat.enums.ChatMessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendChatMessageRequest {
    private UUID recipientId;
    private UUID clientMessageId;
    @Builder.Default
    private ChatMessageType messageType = ChatMessageType.TEXT;
    private String content;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
}
