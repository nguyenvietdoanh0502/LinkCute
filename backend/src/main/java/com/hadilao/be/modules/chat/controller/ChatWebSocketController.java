package com.hadilao.be.modules.chat.controller;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.chat.dto.ChatDelivery;
import com.hadilao.be.modules.chat.dto.ChatErrorDTO;
import com.hadilao.be.modules.chat.dto.SendChatMessageRequest;
import com.hadilao.be.modules.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private static final String MESSAGE_QUEUE = "/queue/messages";
    private static final String ERROR_QUEUE = "/queue/chat-errors";

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(SendChatMessageRequest request, Principal principal) {
        try {
            ChatDelivery delivery = chatService.sendMessage(principal.getName(), request);
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    MESSAGE_QUEUE,
                    delivery.message()
            );
            messagingTemplate.convertAndSendToUser(
                    delivery.recipientUsername(),
                    MESSAGE_QUEUE,
                    delivery.message()
            );
        } catch (AppException exception) {
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    ERROR_QUEUE,
                    ChatErrorDTO.builder()
                            .errorCode(exception.getErrorCode().getCode())
                            .message(exception.getErrorCode().getMessage())
                            .clientMessageId(request == null ? null : request.getClientMessageId())
                            .build()
            );
        }
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(ERROR_QUEUE)
    public ChatErrorDTO handleUnexpectedMessage(Exception exception) {
        log.warn("Could not handle a chat WebSocket frame", exception);
        ErrorCode error = ErrorCode.INVALID_CHAT_MESSAGE;
        return ChatErrorDTO.builder()
                .errorCode(error.getCode())
                .message(error.getMessage())
                .build();
    }
}
