package com.hadilao.be.modules.chat.dto;

public record ChatDelivery(
        ChatMessageDTO message,
        String recipientUsername
) {
}
