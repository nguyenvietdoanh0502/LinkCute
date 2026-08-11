package com.hadilao.be.modules.chat.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.chat.dto.ChatDelivery;
import com.hadilao.be.modules.chat.dto.ChatHistoryDTO;
import com.hadilao.be.modules.chat.dto.SendChatMessageRequest;
import com.hadilao.be.modules.chat.entity.ChatConversation;
import com.hadilao.be.modules.chat.entity.ChatMessage;
import com.hadilao.be.modules.chat.enums.ChatMessageType;
import com.hadilao.be.modules.chat.repository.ChatConversationRepository;
import com.hadilao.be.modules.chat.repository.ChatMessageRepository;
import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final UUID RECIPIENT_ID = id(1);
    private static final UUID SENDER_ID = id(2);
    private static final UUID CONVERSATION_ID = id(10);
    private static final UUID CLIENT_MESSAGE_ID = id(20);
    private static final UUID MESSAGE_ID = id(30);
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T08:15:30Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private ChatConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @InjectMocks
    private ChatService chatService;

    @Test
    void acceptedFriendsCanSendATrimmedMessage() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        User recipient = activeUser(RECIPIENT_ID, "recipient@example.com");
        ChatConversation conversation = conversation(recipient, sender);
        conversation.setUpdatedAt(Instant.EPOCH);
        stubAcceptedPair(sender, recipient);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByClientMessageId(
                CONVERSATION_ID,
                SENDER_ID,
                CLIENT_MESSAGE_ID
        )).thenReturn(Optional.empty());
        when(messageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(MESSAGE_ID);
            saved.setCreatedAt(CREATED_AT);
            return saved;
        });

        ChatDelivery delivery = chatService.sendMessage(
                sender.getEmail(),
                request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "  hello there  ")
        );

        assertThat(delivery.recipientUsername()).isEqualTo(recipient.getEmail());
        assertThat(delivery.message()).satisfies(message -> {
            assertThat(message.getId()).isEqualTo(MESSAGE_ID);
            assertThat(message.getClientMessageId()).isEqualTo(CLIENT_MESSAGE_ID);
            assertThat(message.getSenderId()).isEqualTo(SENDER_ID);
            assertThat(message.getRecipientId()).isEqualTo(RECIPIENT_ID);
            assertThat(message.getMessageType()).isEqualTo(ChatMessageType.TEXT);
            assertThat(message.getContent()).isEqualTo("hello there");
            assertThat(message.getLatitude()).isNull();
            assertThat(message.getLongitude()).isNull();
            assertThat(message.getAccuracyMeters()).isNull();
            assertThat(message.getCreatedAt()).isEqualTo(CREATED_AT);
        });
        assertThat(conversation.getUpdatedAt()).isAfter(Instant.EPOCH);

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).saveAndFlush(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getConversation()).isSameAs(conversation);
            assertThat(message.getSender()).isSameAs(sender);
            assertThat(message.getUserLowId()).isEqualTo(RECIPIENT_ID);
            assertThat(message.getUserHighId()).isEqualTo(SENDER_ID);
            assertThat(message.getClientMessageId()).isEqualTo(CLIENT_MESSAGE_ID);
            assertThat(message.getMessageType()).isEqualTo(ChatMessageType.TEXT);
            assertThat(message.getContent()).isEqualTo("hello there");
            assertThat(message.getLatitude()).isNull();
            assertThat(message.getLongitude()).isNull();
            assertThat(message.getAccuracyMeters()).isNull();
        });
        verify(friendshipRepository).findAcceptedPairForUpdate(RECIPIENT_ID, SENDER_ID);
    }

    @Test
    void acceptedFriendsCanSendALocationSnapshot() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        User recipient = activeUser(RECIPIENT_ID, "recipient@example.com");
        ChatConversation conversation = conversation(recipient, sender);
        stubAcceptedPair(sender, recipient);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByClientMessageId(
                CONVERSATION_ID,
                SENDER_ID,
                CLIENT_MESSAGE_ID
        )).thenReturn(Optional.empty());
        when(messageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(MESSAGE_ID);
            saved.setCreatedAt(CREATED_AT);
            return saved;
        });

        ChatDelivery delivery = chatService.sendMessage(
                sender.getEmail(),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, 21.0278, 105.8342, 12.5)
        );

        assertThat(delivery.message()).satisfies(message -> {
            assertThat(message.getMessageType()).isEqualTo(ChatMessageType.LOCATION);
            assertThat(message.getContent()).isNull();
            assertThat(message.getLatitude()).isEqualTo(21.0278);
            assertThat(message.getLongitude()).isEqualTo(105.8342);
            assertThat(message.getAccuracyMeters()).isEqualTo(12.5);
        });

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).saveAndFlush(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getMessageType()).isEqualTo(ChatMessageType.LOCATION);
            assertThat(message.getContent()).isNull();
            assertThat(message.getLatitude()).isEqualTo(21.0278);
            assertThat(message.getLongitude()).isEqualTo(105.8342);
            assertThat(message.getAccuracyMeters()).isEqualTo(12.5);
        });
    }

    @Test
    void createsOneCanonicalConversationWhenTheAcceptedPairHasNotChatted() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        User recipient = activeUser(RECIPIENT_ID, "recipient@example.com");
        stubAcceptedPair(sender, recipient);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(ChatConversation.class))).thenAnswer(invocation -> {
            ChatConversation saved = invocation.getArgument(0);
            saved.setId(CONVERSATION_ID);
            return saved;
        });
        when(messageRepository.findByClientMessageId(
                CONVERSATION_ID,
                SENDER_ID,
                CLIENT_MESSAGE_ID
        )).thenReturn(Optional.empty());
        when(messageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(MESSAGE_ID);
            saved.setCreatedAt(CREATED_AT);
            return saved;
        });

        chatService.sendMessage(
                sender.getEmail(),
                request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "hello")
        );

        ArgumentCaptor<ChatConversation> conversationCaptor =
                ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversationRepository).saveAndFlush(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue()).satisfies(conversation -> {
            assertThat(conversation.getUserLow()).isSameAs(recipient);
            assertThat(conversation.getUserHigh()).isSameAs(sender);
        });
    }

    @Test
    void duplicateClientMessageIdReturnsTheOriginalMessageWithoutSavingAgain() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        User recipient = activeUser(RECIPIENT_ID, "recipient@example.com");
        ChatConversation conversation = conversation(recipient, sender);
        ChatMessage existing = message(conversation, sender, MESSAGE_ID, CLIENT_MESSAGE_ID,
                "hello", CREATED_AT);
        stubAcceptedPair(sender, recipient);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByClientMessageId(
                CONVERSATION_ID,
                SENDER_ID,
                CLIENT_MESSAGE_ID
        )).thenReturn(Optional.of(existing));

        ChatDelivery delivery = chatService.sendMessage(
                sender.getEmail(),
                request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "  hello ")
        );

        assertThat(delivery.message().getId()).isEqualTo(MESSAGE_ID);
        assertThat(delivery.message().getContent()).isEqualTo("hello");
        verify(messageRepository, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    void duplicateClientMessageIdCannotBeReusedForDifferentContent() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        User recipient = activeUser(RECIPIENT_ID, "recipient@example.com");
        ChatConversation conversation = conversation(recipient, sender);
        ChatMessage existing = message(conversation, sender, MESSAGE_ID, CLIENT_MESSAGE_ID,
                "original", CREATED_AT);
        stubAcceptedPair(sender, recipient);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByClientMessageId(
                CONVERSATION_ID,
                SENDER_ID,
                CLIENT_MESSAGE_ID
        )).thenReturn(Optional.of(existing));

        assertError(
                () -> chatService.sendMessage(
                        sender.getEmail(),
                        request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "different")
                ),
                ErrorCode.INVALID_CHAT_MESSAGE
        );

        verify(messageRepository, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    void locationRetryMustMatchTheEntireOriginalPayload() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        User recipient = activeUser(RECIPIENT_ID, "recipient@example.com");
        ChatConversation conversation = conversation(recipient, sender);
        ChatMessage existing = locationMessage(
                conversation,
                sender,
                MESSAGE_ID,
                CLIENT_MESSAGE_ID,
                21.0278,
                105.8342,
                12.5,
                CREATED_AT
        );
        stubAcceptedPair(sender, recipient);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByClientMessageId(
                CONVERSATION_ID,
                SENDER_ID,
                CLIENT_MESSAGE_ID
        )).thenReturn(Optional.of(existing));

        ChatDelivery retry = chatService.sendMessage(
                sender.getEmail(),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, 21.0278, 105.8342, 12.5)
        );
        assertThat(retry.message().getId()).isEqualTo(MESSAGE_ID);

        assertError(
                () -> chatService.sendMessage(
                        sender.getEmail(),
                        locationRequest(
                                RECIPIENT_ID,
                                CLIENT_MESSAGE_ID,
                                21.0279,
                                105.8342,
                                12.5
                        )
                ),
                ErrorCode.INVALID_CHAT_MESSAGE
        );
        assertError(
                () -> chatService.sendMessage(
                        sender.getEmail(),
                        request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "different type")
                ),
                ErrorCode.INVALID_CHAT_MESSAGE
        );

        verify(messageRepository, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    void sendingRequiresAnAcceptedFriendship() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        User recipient = activeUser(RECIPIENT_ID, "recipient@example.com");
        stubUsers(sender, recipient);
        when(friendshipRepository.findAcceptedPairForUpdate(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.empty());

        assertError(
                () -> chatService.sendMessage(
                        sender.getEmail(),
                        request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "hello")
                ),
                ErrorCode.CHAT_REQUIRES_FRIENDSHIP
        );

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    void sendingToSelfIsRejectedBeforeCheckingFriendship() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        when(userRepository.findByEmail(sender.getEmail())).thenReturn(Optional.of(sender));
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(SENDER_ID, AccountStatus.ACTIVE))
                .thenReturn(Optional.of(sender));

        assertError(
                () -> chatService.sendMessage(
                        sender.getEmail(),
                        request(SENDER_ID, CLIENT_MESSAGE_ID, "hello")
                ),
                ErrorCode.CHAT_SELF_NOT_ALLOWED
        );

        verifyNoInteractions(friendshipRepository, conversationRepository, messageRepository);
    }

    @Test
    void malformedMessagesAreRejectedBeforeLookingUpTheRecipient() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        when(userRepository.findByEmail(sender.getEmail())).thenReturn(Optional.of(sender));

        List<SendChatMessageRequest> invalidRequests = List.of(
                request(null, CLIENT_MESSAGE_ID, "hello"),
                request(RECIPIENT_ID, null, "hello"),
                request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "   "),
                request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "x".repeat(2001))
        );
        assertError(() -> chatService.sendMessage(sender.getEmail(), null),
                ErrorCode.INVALID_CHAT_MESSAGE);
        invalidRequests.forEach(request -> assertError(
                () -> chatService.sendMessage(sender.getEmail(), request),
                ErrorCode.INVALID_CHAT_MESSAGE
        ));

        verify(userRepository, never())
                .findByIdAndIsDeletedFalseAndStatus(any(UUID.class), any(AccountStatus.class));
        verifyNoInteractions(friendshipRepository, conversationRepository, messageRepository);
    }

    @Test
    void malformedLocationPayloadsAreRejectedBeforeLookingUpTheRecipient() {
        User sender = activeUser(SENDER_ID, "sender@example.com");
        when(userRepository.findByEmail(sender.getEmail())).thenReturn(Optional.of(sender));
        SendChatMessageRequest textWithCoordinates = request(
                RECIPIENT_ID,
                CLIENT_MESSAGE_ID,
                "hello"
        );
        textWithCoordinates.setLatitude(21.0);
        SendChatMessageRequest locationWithContent = locationRequest(
                RECIPIENT_ID,
                CLIENT_MESSAGE_ID,
                21.0,
                105.0,
                10.0
        );
        locationWithContent.setContent("mixed payload");

        List<SendChatMessageRequest> invalidRequests = List.of(
                textWithCoordinates,
                locationWithContent,
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, null, 105.0, 10.0),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, 21.0, null, 10.0),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, 21.0, 105.0, null),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, Double.NaN, 105.0, 10.0),
                locationRequest(
                        RECIPIENT_ID,
                        CLIENT_MESSAGE_ID,
                        21.0,
                        Double.POSITIVE_INFINITY,
                        10.0
                ),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, -90.0001, 105.0, 10.0),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, 21.0, 180.0001, 10.0),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, 21.0, 105.0, -0.01),
                locationRequest(
                        RECIPIENT_ID,
                        CLIENT_MESSAGE_ID,
                        21.0,
                        105.0,
                        Double.NaN
                ),
                locationRequest(RECIPIENT_ID, CLIENT_MESSAGE_ID, 21.0, 105.0, 1_000_000.01)
        );
        invalidRequests.forEach(request -> assertError(
                () -> chatService.sendMessage(sender.getEmail(), request),
                ErrorCode.INVALID_CHAT_LOCATION
        ));

        verify(userRepository, never())
                .findByIdAndIsDeletedFalseAndStatus(any(UUID.class), any(AccountStatus.class));
        verifyNoInteractions(friendshipRepository, conversationRepository, messageRepository);
    }

    @Test
    void unavailableSenderIsTreatedAsUnauthenticated() {
        User banned = activeUser(SENDER_ID, "sender@example.com");
        banned.setStatus(AccountStatus.BANNED);
        when(userRepository.findByEmail(banned.getEmail())).thenReturn(Optional.of(banned));

        assertError(
                () -> chatService.sendMessage(
                        banned.getEmail(),
                        request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "hello")
                ),
                ErrorCode.UNAUTHENTICATED
        );
        assertError(
                () -> chatService.sendMessage("   ",
                        request(RECIPIENT_ID, CLIENT_MESSAGE_ID, "hello")),
                ErrorCode.UNAUTHENTICATED
        );

        verifyNoInteractions(friendshipRepository, conversationRepository, messageRepository);
    }

    @Test
    void historyRequiresTheFriendshipToStillBeAccepted() {
        User current = activeUser(SENDER_ID, "sender@example.com");
        User friend = activeUser(RECIPIENT_ID, "recipient@example.com");
        Friendship pending = Friendship.builder()
                .userLow(friend)
                .userHigh(current)
                .requester(current)
                .status(FriendshipStatus.PENDING)
                .build();
        stubUsers(current, friend);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(pending));

        assertError(
                () -> chatService.getHistory(current.getEmail(), RECIPIENT_ID, null, 50),
                ErrorCode.CHAT_REQUIRES_FRIENDSHIP
        );

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    void acceptedFriendsWithoutAConversationReceiveAnEmptyHistory() {
        User current = activeUser(SENDER_ID, "sender@example.com");
        User friend = activeUser(RECIPIENT_ID, "recipient@example.com");
        stubAcceptedHistoryPair(current, friend);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.empty());

        ChatHistoryDTO history = chatService.getHistory(
                current.getEmail(),
                RECIPIENT_ID,
                null,
                null
        );

        assertThat(history.getFriendId()).isEqualTo(RECIPIENT_ID);
        assertThat(history.getMessages()).isEmpty();
        assertThat(history.isHasMore()).isFalse();
        assertThat(history.getNextBefore()).isNull();
        verifyNoInteractions(messageRepository);
    }

    @Test
    void historyUsesLookaheadAndReturnsMessagesInChronologicalOrder() {
        User current = activeUser(SENDER_ID, "sender@example.com");
        User friend = activeUser(RECIPIENT_ID, "recipient@example.com");
        ChatConversation conversation = conversation(friend, current);
        Instant oldest = Instant.parse("2026-08-10T08:00:00Z");
        Instant middle = Instant.parse("2026-08-10T08:01:00Z");
        Instant newest = Instant.parse("2026-08-10T08:02:00Z");
        ChatMessage newestMessage = message(
                conversation, current, id(33), id(23), "newest", newest);
        ChatMessage middleMessage = message(
                conversation, friend, id(32), id(22), "middle", middle);
        ChatMessage oldestMessage = message(
                conversation, current, id(31), id(21), "oldest", oldest);
        stubAcceptedHistoryPair(current, friend);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findLatest(eq(CONVERSATION_ID), any(Pageable.class)))
                .thenReturn(List.of(newestMessage, middleMessage, oldestMessage));

        ChatHistoryDTO history = chatService.getHistory(
                current.getEmail(),
                RECIPIENT_ID,
                null,
                2
        );

        assertThat(history.getMessages())
                .extracting(message -> message.getContent())
                .containsExactly("middle", "newest");
        assertThat(history.getMessages().get(0).getSenderId()).isEqualTo(RECIPIENT_ID);
        assertThat(history.getMessages().get(0).getRecipientId()).isEqualTo(SENDER_ID);
        assertThat(history.isHasMore()).isTrue();
        assertThat(history.getNextBefore()).isEqualTo(middle);
        assertThat(history.getNextBeforeId()).isEqualTo(id(32));
        verify(messageRepository).findLatest(
                eq(CONVERSATION_ID),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 3)
        );
    }

    @Test
    void historyReturnsTheCompleteLocationPayload() {
        User current = activeUser(SENDER_ID, "sender@example.com");
        User friend = activeUser(RECIPIENT_ID, "recipient@example.com");
        ChatConversation conversation = conversation(friend, current);
        ChatMessage location = locationMessage(
                conversation,
                friend,
                MESSAGE_ID,
                CLIENT_MESSAGE_ID,
                21.0278,
                105.8342,
                8.25,
                CREATED_AT
        );
        stubAcceptedHistoryPair(current, friend);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findLatest(eq(CONVERSATION_ID), any(Pageable.class)))
                .thenReturn(List.of(location));

        ChatHistoryDTO history = chatService.getHistory(
                current.getEmail(),
                RECIPIENT_ID,
                null,
                50
        );

        assertThat(history.getMessages()).singleElement().satisfies(message -> {
            assertThat(message.getMessageType()).isEqualTo(ChatMessageType.LOCATION);
            assertThat(message.getContent()).isNull();
            assertThat(message.getLatitude()).isEqualTo(21.0278);
            assertThat(message.getLongitude()).isEqualTo(105.8342);
            assertThat(message.getAccuracyMeters()).isEqualTo(8.25);
        });
    }

    @Test
    void historyForwardsTheCompoundCursorAndClampsTheRequestedPageSize() {
        User current = activeUser(SENDER_ID, "sender@example.com");
        User friend = activeUser(RECIPIENT_ID, "recipient@example.com");
        ChatConversation conversation = conversation(friend, current);
        Instant before = Instant.parse("2026-08-10T08:00:00Z");
        UUID beforeId = id(40);
        stubAcceptedHistoryPair(current, friend);
        when(conversationRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findBeforeCursor(
                eq(CONVERSATION_ID),
                eq(before),
                eq(beforeId),
                any(Pageable.class)
        )).thenReturn(List.of());

        chatService.getHistory(current.getEmail(), RECIPIENT_ID, before, beforeId, 500);

        verify(messageRepository).findBeforeCursor(
                eq(CONVERSATION_ID),
                eq(before),
                eq(beforeId),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 101)
        );
    }

    private void stubAcceptedPair(User sender, User recipient) {
        stubUsers(sender, recipient);
        when(friendshipRepository.findAcceptedPairForUpdate(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(acceptedFriendship(recipient, sender)));
    }

    private void stubAcceptedHistoryPair(User current, User friend) {
        stubUsers(current, friend);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(acceptedFriendship(friend, current)));
    }

    private void stubUsers(User current, User other) {
        when(userRepository.findByEmail(current.getEmail())).thenReturn(Optional.of(current));
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(other.getId(), AccountStatus.ACTIVE))
                .thenReturn(Optional.of(other));
    }

    private Friendship acceptedFriendship(User low, User high) {
        return Friendship.builder()
                .id(id(5))
                .userLow(low)
                .userHigh(high)
                .requester(high)
                .status(FriendshipStatus.ACCEPTED)
                .build();
    }

    private ChatConversation conversation(User low, User high) {
        return ChatConversation.builder()
                .id(CONVERSATION_ID)
                .userLow(low)
                .userHigh(high)
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .build();
    }

    private ChatMessage message(
            ChatConversation conversation,
            User sender,
            UUID messageId,
            UUID clientMessageId,
            String content,
            Instant createdAt
    ) {
        return ChatMessage.builder()
                .id(messageId)
                .conversation(conversation)
                .sender(sender)
                .userLowId(conversation.getUserLow().getId())
                .userHighId(conversation.getUserHigh().getId())
                .clientMessageId(clientMessageId)
                .content(content)
                .createdAt(createdAt)
                .build();
    }

    private ChatMessage locationMessage(
            ChatConversation conversation,
            User sender,
            UUID messageId,
            UUID clientMessageId,
            Double latitude,
            Double longitude,
            Double accuracyMeters,
            Instant createdAt
    ) {
        return ChatMessage.builder()
                .id(messageId)
                .conversation(conversation)
                .sender(sender)
                .userLowId(conversation.getUserLow().getId())
                .userHighId(conversation.getUserHigh().getId())
                .clientMessageId(clientMessageId)
                .messageType(ChatMessageType.LOCATION)
                .latitude(latitude)
                .longitude(longitude)
                .accuracyMeters(accuracyMeters)
                .createdAt(createdAt)
                .build();
    }

    private User activeUser(UUID userId, String email) {
        return User.builder()
                .id(userId)
                .email(email)
                .fullName(email.substring(0, email.indexOf('@')))
                .pinCode("RML-123456")
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private SendChatMessageRequest request(
            UUID recipientId,
            UUID clientMessageId,
            String content
    ) {
        return SendChatMessageRequest.builder()
                .recipientId(recipientId)
                .clientMessageId(clientMessageId)
                .content(content)
                .build();
    }

    private SendChatMessageRequest locationRequest(
            UUID recipientId,
            UUID clientMessageId,
            Double latitude,
            Double longitude,
            Double accuracyMeters
    ) {
        return SendChatMessageRequest.builder()
                .recipientId(recipientId)
                .clientMessageId(clientMessageId)
                .messageType(ChatMessageType.LOCATION)
                .latitude(latitude)
                .longitude(longitude)
                .accuracyMeters(accuracyMeters)
                .build();
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            ErrorCode errorCode
    ) {
        assertThatThrownBy(operation)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }

    private static UUID id(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
