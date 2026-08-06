package com.hadilao.be.modules.friendship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.service.MailService;
import com.hadilao.be.modules.auth.service.OtpService;
import com.hadilao.be.modules.friendship.dto.FriendDTO;
import com.hadilao.be.modules.friendship.dto.FriendRequestDTO;
import com.hadilao.be.modules.friendship.dto.FriendSearchDTO;
import com.hadilao.be.modules.friendship.dto.FriendUserDTO;
import com.hadilao.be.modules.friendship.dto.SendFriendRequestRequest;
import com.hadilao.be.modules.friendship.enums.RelationshipStatus;
import com.hadilao.be.modules.friendship.service.FriendshipService;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests for FriendshipController")
class FriendshipControllerTest {

    private static final String AUTHENTICATED_EMAIL = "friend@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FriendshipService friendshipService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MailService mailService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Nested
    @DisplayName("Endpoint security")
    class EndpointSecurity {

        @Test
        @DisplayName("Should return a structured 401 for an anonymous request")
        void shouldRejectAnonymousRequestsWithStructuredError() throws Exception {
            mockMvc.perform(get("/api/v1/friends")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Unauthenticated"))
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

            verify(friendshipService, never()).getFriends();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/search")
    class SearchByPinCode {

        @Test
        @DisplayName("Should return the public friend search shape without exposing email")
        void shouldReturnSearchResultWithoutEmail() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID friendshipId = UUID.randomUUID();
            FriendSearchDTO result = FriendSearchDTO.builder()
                    .id(userId)
                    .fullName("Nguyen Van An")
                    .avatarUrl("https://cdn.example.com/an.png")
                    .pinCode("RML-123456")
                    .relationshipStatus(RelationshipStatus.OUTGOING_PENDING)
                    .friendshipId(friendshipId)
                    .build();
            when(friendshipService.searchByPinCode("RML-123456")).thenReturn(result);

            mockMvc.perform(get("/api/v1/friends/search")
                            .with(user(AUTHENTICATED_EMAIL))
                            .param("pinCode", "RML-123456")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.id").value(userId.toString()))
                    .andExpect(jsonPath("$.data.fullName").value("Nguyen Van An"))
                    .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example.com/an.png"))
                    .andExpect(jsonPath("$.data.pinCode").value("RML-123456"))
                    .andExpect(jsonPath("$.data.relationshipStatus").value("OUTGOING_PENDING"))
                    .andExpect(jsonPath("$.data.friendshipId").value(friendshipId.toString()))
                    .andExpect(jsonPath("$.data.email").doesNotExist());

            verify(friendshipService).searchByPinCode("RML-123456");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/friends/requests")
    class SendRequest {

        @Test
        @DisplayName("Should create a friend request and return its body")
        void shouldSendFriendRequest() throws Exception {
            UUID addresseeId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-08-05T08:15:30Z");
            FriendRequestDTO result = FriendRequestDTO.builder()
                    .id(requestId)
                    .user(friendUser(addresseeId))
                    .createdAt(createdAt)
                    .build();

            when(friendshipService.sendRequest(addresseeId)).thenReturn(result);

            mockMvc.perform(post("/api/v1/friends/requests")
                            .with(user(AUTHENTICATED_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SendFriendRequestRequest(addresseeId))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Friend request sent"))
                    .andExpect(jsonPath("$.data.id").value(requestId.toString()))
                    .andExpect(jsonPath("$.data.user.id").value(addresseeId.toString()))
                    .andExpect(jsonPath("$.data.user.fullName").value("Nguyen Van An"))
                    .andExpect(jsonPath("$.data.user.pinCode").value("RML-123456"))
                    .andExpect(jsonPath("$.data.user.email").doesNotExist())
                    .andExpect(jsonPath("$.data.createdAt").value(createdAt.toString()));

            verify(friendshipService).sendRequest(addresseeId);
        }

        @Test
        @DisplayName("Should reject a body without addresseeId")
        void shouldRejectMissingAddresseeId() throws Exception {
            mockMvc.perform(post("/api/v1/friends/requests")
                            .with(user(AUTHENTICATED_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("MISSING_ADDRESSEE_ID"));

            verify(friendshipService, never()).sendRequest(any());
        }

        @Test
        @DisplayName("Should map a service AppException to its API error")
        void shouldMapServiceAppException() throws Exception {
            UUID addresseeId = UUID.randomUUID();
            when(friendshipService.sendRequest(addresseeId))
                    .thenThrow(new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS));

            mockMvc.perform(post("/api/v1/friends/requests")
                            .with(user(AUTHENTICATED_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SendFriendRequestRequest(addresseeId))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("A friend request already exists"))
                    .andExpect(jsonPath("$.errorCode").value("FRIEND_REQUEST_ALREADY_EXISTS"));

            verify(friendshipService).sendRequest(addresseeId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/friends/requests/{id}/accept")
    class AcceptRequest {

        @Test
        @DisplayName("Should accept a friend request")
        void shouldAcceptFriendRequest() throws Exception {
            UUID requestId = UUID.randomUUID();
            UUID friendshipId = UUID.randomUUID();
            UUID friendId = UUID.randomUUID();
            Instant friendsSince = Instant.parse("2026-08-05T09:30:00Z");
            FriendDTO result = FriendDTO.builder()
                    .friendshipId(friendshipId)
                    .user(friendUser(friendId))
                    .friendsSince(friendsSince)
                    .build();
            when(friendshipService.acceptRequest(requestId)).thenReturn(result);

            mockMvc.perform(post("/api/v1/friends/requests/{id}/accept", requestId)
                            .with(user(AUTHENTICATED_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Friend request accepted"))
                    .andExpect(jsonPath("$.data.friendshipId").value(friendshipId.toString()))
                    .andExpect(jsonPath("$.data.user.id").value(friendId.toString()))
                    .andExpect(jsonPath("$.data.user.email").doesNotExist())
                    .andExpect(jsonPath("$.data.friendsSince").value(friendsSince.toString()));

            verify(friendshipService).acceptRequest(requestId);
        }

        @Test
        @DisplayName("Should return INVALID_INPUT when request id is not a UUID")
        void shouldRejectInvalidRequestId() throws Exception {
            mockMvc.perform(post("/api/v1/friends/requests/not-a-uuid/accept")
                            .with(user(AUTHENTICATED_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));

            verify(friendshipService, never()).acceptRequest(any());
        }
    }

    @Nested
    @DisplayName("DELETE friendship endpoints")
    class DeleteEndpoints {

        @Test
        @DisplayName("Should delete a pending friend request")
        void shouldDeletePendingRequest() throws Exception {
            UUID requestId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/friends/requests/{id}", requestId)
                            .with(user(AUTHENTICATED_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Friend request removed"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(friendshipService).deleteRequest(requestId);
        }

        @Test
        @DisplayName("Should remove an accepted friendship")
        void shouldRemoveFriend() throws Exception {
            UUID friendshipId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/friends/{friendshipId}", friendshipId)
                            .with(user(AUTHENTICATED_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Friendship removed"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(friendshipService).removeFriend(friendshipId);
        }
    }

    private FriendUserDTO friendUser(UUID userId) {
        return FriendUserDTO.builder()
                .id(userId)
                .fullName("Nguyen Van An")
                .avatarUrl("https://cdn.example.com/an.png")
                .pinCode("RML-123456")
                .build();
    }
}
