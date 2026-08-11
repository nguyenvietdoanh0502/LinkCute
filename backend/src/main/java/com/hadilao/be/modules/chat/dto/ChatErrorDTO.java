package com.hadilao.be.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatErrorDTO {
    private String errorCode;
    private String message;
    private UUID clientMessageId;
}
