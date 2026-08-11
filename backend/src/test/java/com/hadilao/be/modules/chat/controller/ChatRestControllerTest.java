package com.hadilao.be.modules.chat.controller;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.service.MailService;
import com.hadilao.be.modules.auth.service.OtpService;
import com.hadilao.be.modules.chat.dto.ChatHistoryDTO;
import com.hadilao.be.modules.chat.dto.ChatMessageDTO;
import com.hadilao.be.modules.chat.enums.ChatMessageType;
import com.hadilao.be.modules.chat.service.ChatService;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatRestControllerTest {

    private static final String AUTHENTICATED_EMAIL = "chat@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void anonymousHistoryRequestReturnsTheStructuredAuthenticationError() throws Exception {
        UUID friendId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/chat/friends/{friendId}/messages", friendId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthenticated"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        verifyNoInteractions(chatService);
    }

    @Test
    void historyEndpointForwardsTheCompoundCursorAndReturnsItsResponseShape() throws Exception {
        UUID friendId = UUID.randomUUID();
        UUID beforeId = UUID.randomUUID();
        UUID nextBeforeId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        Instant before = Instant.parse("2026-08-10T08:15:30Z");
        Instant nextBefore = Instant.parse("2026-08-10T08:00:00Z");
        Instant createdAt = Instant.parse("2026-08-10T08:10:00Z");
        ChatHistoryDTO history = ChatHistoryDTO.builder()
                .friendId(friendId)
                .messages(List.of(ChatMessageDTO.builder()
                        .id(messageId)
                        .clientMessageId(clientMessageId)
                        .senderId(friendId)
                        .recipientId(UUID.randomUUID())
                        .messageType(ChatMessageType.TEXT)
                        .content("hello")
                        .createdAt(createdAt)
                        .build()))
                .hasMore(true)
                .nextBefore(nextBefore)
                .nextBeforeId(nextBeforeId)
                .build();
        when(chatService.getHistory(
                AUTHENTICATED_EMAIL,
                friendId,
                before,
                beforeId,
                25
        )).thenReturn(history);

        mockMvc.perform(get("/api/v1/chat/friends/{friendId}/messages", friendId)
                        .with(user(AUTHENTICATED_EMAIL))
                        .param("before", before.toString())
                        .param("beforeId", beforeId.toString())
                        .param("size", "25")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.friendId").value(friendId.toString()))
                .andExpect(jsonPath("$.data.messages[0].id").value(messageId.toString()))
                .andExpect(jsonPath("$.data.messages[0].clientMessageId")
                        .value(clientMessageId.toString()))
                .andExpect(jsonPath("$.data.messages[0].senderId").value(friendId.toString()))
                .andExpect(jsonPath("$.data.messages[0].messageType").value("TEXT"))
                .andExpect(jsonPath("$.data.messages[0].content").value("hello"))
                .andExpect(jsonPath("$.data.messages[0].createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextBefore").value(nextBefore.toString()))
                .andExpect(jsonPath("$.data.nextBeforeId").value(nextBeforeId.toString()));

        verify(chatService).getHistory(
                AUTHENTICATED_EMAIL,
                friendId,
                before,
                beforeId,
                25
        );
    }

    @Test
    void historySerializesAFlatLocationPayload() throws Exception {
        UUID friendId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        ChatHistoryDTO history = ChatHistoryDTO.builder()
                .friendId(friendId)
                .messages(List.of(ChatMessageDTO.builder()
                        .id(UUID.randomUUID())
                        .clientMessageId(UUID.randomUUID())
                        .senderId(friendId)
                        .recipientId(currentUserId)
                        .messageType(ChatMessageType.LOCATION)
                        .latitude(21.0278)
                        .longitude(105.8342)
                        .accuracyMeters(7.5)
                        .createdAt(Instant.parse("2026-08-10T08:10:00Z"))
                        .build()))
                .build();
        when(chatService.getHistory(
                AUTHENTICATED_EMAIL,
                friendId,
                null,
                null,
                50
        )).thenReturn(history);

        mockMvc.perform(get("/api/v1/chat/friends/{friendId}/messages", friendId)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].messageType").value("LOCATION"))
                .andExpect(jsonPath("$.data.messages[0].latitude").value(21.0278))
                .andExpect(jsonPath("$.data.messages[0].longitude").value(105.8342))
                .andExpect(jsonPath("$.data.messages[0].accuracyMeters").value(7.5));
    }

    @Test
    void historyEndpointUsesTheDefaultPageSize() throws Exception {
        UUID friendId = UUID.randomUUID();
        ChatHistoryDTO empty = ChatHistoryDTO.builder()
                .friendId(friendId)
                .messages(List.of())
                .build();
        when(chatService.getHistory(
                AUTHENTICATED_EMAIL,
                friendId,
                null,
                null,
                50
        )).thenReturn(empty);

        mockMvc.perform(get("/api/v1/chat/friends/{friendId}/messages", friendId)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isEmpty())
                .andExpect(jsonPath("$.data.hasMore").value(false));

        verify(chatService).getHistory(
                AUTHENTICATED_EMAIL,
                friendId,
                null,
                null,
                50
        );
    }

    @Test
    void invalidCursorInputIsRejectedBeforeCallingTheService() throws Exception {
        UUID friendId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/chat/friends/{friendId}/messages", friendId)
                        .with(user(AUTHENTICATED_EMAIL))
                        .param("before", "not-an-instant")
                        .param("beforeId", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));

        verifyNoInteractions(chatService);
    }

    @Test
    void serviceFriendshipFailureIsMappedToAForbiddenApiError() throws Exception {
        UUID friendId = UUID.randomUUID();
        when(chatService.getHistory(
                AUTHENTICATED_EMAIL,
                friendId,
                null,
                null,
                50
        )).thenThrow(new AppException(ErrorCode.CHAT_REQUIRES_FRIENDSHIP));

        mockMvc.perform(get("/api/v1/chat/friends/{friendId}/messages", friendId)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message")
                        .value("Only accepted friends can exchange messages"))
                .andExpect(jsonPath("$.errorCode").value("CHAT_REQUIRES_FRIENDSHIP"));
    }
}
