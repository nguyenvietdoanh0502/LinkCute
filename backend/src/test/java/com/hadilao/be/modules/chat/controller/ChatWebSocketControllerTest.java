package com.hadilao.be.modules.chat.controller;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.chat.dto.ChatDelivery;
import com.hadilao.be.modules.chat.dto.ChatErrorDTO;
import com.hadilao.be.modules.chat.dto.ChatMessageDTO;
import com.hadilao.be.modules.chat.dto.SendChatMessageRequest;
import com.hadilao.be.modules.chat.enums.ChatMessageType;
import com.hadilao.be.modules.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    private static final String SENDER = "sender@example.com";
    private static final String RECIPIENT = "recipient@example.com";

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Principal principal;

    @InjectMocks
    private ChatWebSocketController controller;

    @Test
    void successfulMessageIsDeliveredToBothParticipantsPrivateQueues() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        SendChatMessageRequest request = request(recipientId);
        ChatMessageDTO message = ChatMessageDTO.builder()
                .id(UUID.randomUUID())
                .clientMessageId(request.getClientMessageId())
                .senderId(senderId)
                .recipientId(recipientId)
                .messageType(ChatMessageType.TEXT)
                .content("hello")
                .createdAt(Instant.parse("2026-08-10T08:15:30Z"))
                .build();
        when(principal.getName()).thenReturn(SENDER);
        when(chatService.sendMessage(SENDER, request))
                .thenReturn(new ChatDelivery(message, RECIPIENT));

        controller.sendMessage(request, principal);

        verify(messagingTemplate).convertAndSendToUser(SENDER, "/queue/messages", message);
        verify(messagingTemplate).convertAndSendToUser(RECIPIENT, "/queue/messages", message);
        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("/queue/chat-errors"),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void locationSnapshotUsesTheExistingPrivateMessageQueues() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        SendChatMessageRequest request = SendChatMessageRequest.builder()
                .recipientId(recipientId)
                .clientMessageId(UUID.randomUUID())
                .messageType(ChatMessageType.LOCATION)
                .latitude(21.0278)
                .longitude(105.8342)
                .accuracyMeters(6.5)
                .build();
        ChatMessageDTO message = ChatMessageDTO.builder()
                .id(UUID.randomUUID())
                .clientMessageId(request.getClientMessageId())
                .senderId(senderId)
                .recipientId(recipientId)
                .messageType(ChatMessageType.LOCATION)
                .latitude(21.0278)
                .longitude(105.8342)
                .accuracyMeters(6.5)
                .createdAt(Instant.parse("2026-08-10T08:15:30Z"))
                .build();
        when(principal.getName()).thenReturn(SENDER);
        when(chatService.sendMessage(SENDER, request))
                .thenReturn(new ChatDelivery(message, RECIPIENT));

        controller.sendMessage(request, principal);

        verify(messagingTemplate).convertAndSendToUser(SENDER, "/queue/messages", message);
        verify(messagingTemplate).convertAndSendToUser(RECIPIENT, "/queue/messages", message);
    }

    @Test
    void businessFailureIsReturnedOnlyToTheSenderErrorQueueWithTheRetryId() {
        UUID recipientId = UUID.randomUUID();
        SendChatMessageRequest request = request(recipientId);
        when(principal.getName()).thenReturn(SENDER);
        when(chatService.sendMessage(SENDER, request))
                .thenThrow(new AppException(ErrorCode.CHAT_REQUIRES_FRIENDSHIP));

        controller.sendMessage(request, principal);

        ArgumentCaptor<ChatErrorDTO> errorCaptor = ArgumentCaptor.forClass(ChatErrorDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq(SENDER),
                org.mockito.ArgumentMatchers.eq("/queue/chat-errors"),
                errorCaptor.capture()
        );
        assertThat(errorCaptor.getValue()).satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo("CHAT_REQUIRES_FRIENDSHIP");
            assertThat(error.getMessage())
                    .isEqualTo("Only accepted friends can exchange messages");
            assertThat(error.getClientMessageId()).isEqualTo(request.getClientMessageId());
        });
        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("/queue/messages"),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void nullRequestFailureStillReturnsAWellFormedError() {
        when(principal.getName()).thenReturn(SENDER);
        when(chatService.sendMessage(SENDER, null))
                .thenThrow(new AppException(ErrorCode.INVALID_CHAT_MESSAGE));

        controller.sendMessage(null, principal);

        ArgumentCaptor<ChatErrorDTO> errorCaptor = ArgumentCaptor.forClass(ChatErrorDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq(SENDER),
                org.mockito.ArgumentMatchers.eq("/queue/chat-errors"),
                errorCaptor.capture()
        );
        assertThat(errorCaptor.getValue().getErrorCode()).isEqualTo("INVALID_CHAT_MESSAGE");
        assertThat(errorCaptor.getValue().getClientMessageId()).isNull();
    }

    @Test
    void unexpectedFrameFailureUsesTheGenericChatValidationError() {
        ChatErrorDTO error = controller.handleUnexpectedMessage(
                new IllegalStateException("malformed frame")
        );

        assertThat(error.getErrorCode()).isEqualTo("INVALID_CHAT_MESSAGE");
        assertThat(error.getMessage())
                .isEqualTo("Message content must contain between 1 and 2000 characters");
        assertThat(error.getClientMessageId()).isNull();
    }

    private SendChatMessageRequest request(UUID recipientId) {
        return SendChatMessageRequest.builder()
                .recipientId(recipientId)
                .clientMessageId(UUID.randomUUID())
                .content("hello")
                .build();
    }
}
