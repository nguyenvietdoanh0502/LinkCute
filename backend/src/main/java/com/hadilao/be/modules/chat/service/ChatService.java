package com.hadilao.be.modules.chat.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.chat.dto.ChatDelivery;
import com.hadilao.be.modules.chat.dto.ChatHistoryDTO;
import com.hadilao.be.modules.chat.dto.ChatMessageDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_HISTORY_SIZE = 50;
    private static final int MAX_HISTORY_SIZE = 100;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final double MAX_LOCATION_ACCURACY_METERS = 1_000_000D;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    @Transactional
    public ChatDelivery sendMessage(String senderUsername, SendChatMessageRequest request) {
        User sender = requireActiveSender(senderUsername);
        NormalizedMessage normalized = normalizeRequest(request);
        User recipient = userRepository
                .findByIdAndIsDeletedFalseAndStatus(request.getRecipientId(), AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_RECIPIENT_NOT_FOUND));
        if (sender.getId().equals(recipient.getId())) {
            throw new AppException(ErrorCode.CHAT_SELF_NOT_ALLOWED);
        }

        UserPair pair = canonicalPair(sender, recipient);
        friendshipRepository.findAcceptedPairForUpdate(pair.low().getId(), pair.high().getId())
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_REQUIRES_FRIENDSHIP));

        ChatConversation conversation = conversationRepository
                .findByUserPair(pair.low().getId(), pair.high().getId())
                .orElseGet(() -> conversationRepository.saveAndFlush(ChatConversation.builder()
                        .userLow(pair.low())
                        .userHigh(pair.high())
                        .build()));

        ChatMessage existing = messageRepository.findByClientMessageId(
                conversation.getId(),
                sender.getId(),
                request.getClientMessageId()
        ).orElse(null);
        if (existing != null) {
            if (!matches(existing, normalized)) {
                throw new AppException(ErrorCode.INVALID_CHAT_MESSAGE);
            }
            return delivery(existing, recipient.getEmail());
        }

        conversation.touch();
        ChatMessage saved = messageRepository.saveAndFlush(ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .userLowId(conversation.getUserLow().getId())
                .userHighId(conversation.getUserHigh().getId())
                .clientMessageId(request.getClientMessageId())
                .messageType(normalized.messageType())
                .content(normalized.content())
                .latitude(normalized.latitude())
                .longitude(normalized.longitude())
                .accuracyMeters(normalized.accuracyMeters())
                .build());
        return delivery(saved, recipient.getEmail());
    }

    @Transactional(readOnly = true)
    public ChatHistoryDTO getHistory(
            String currentUsername,
            UUID friendId,
            Instant before,
            Integer requestedSize) {
        return getHistory(currentUsername, friendId, before, null, requestedSize);
    }

    @Transactional(readOnly = true)
    public ChatHistoryDTO getHistory(
            String currentUsername,
            UUID friendId,
            Instant before,
            UUID beforeId,
            Integer requestedSize) {
        if ((before == null) != (beforeId == null)) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        User currentUser = requireActiveSender(currentUsername);
        User friend = userRepository
                .findByIdAndIsDeletedFalseAndStatus(friendId, AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_RECIPIENT_NOT_FOUND));
        if (currentUser.getId().equals(friend.getId())) {
            throw new AppException(ErrorCode.CHAT_SELF_NOT_ALLOWED);
        }

        UserPair pair = canonicalPair(currentUser, friend);
        Friendship friendship = friendshipRepository
                .findByUserPair(pair.low().getId(), pair.high().getId())
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_REQUIRES_FRIENDSHIP));
        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new AppException(ErrorCode.CHAT_REQUIRES_FRIENDSHIP);
        }

        ChatConversation conversation = conversationRepository
                .findByUserPair(pair.low().getId(), pair.high().getId())
                .orElse(null);
        if (conversation == null) {
            return ChatHistoryDTO.builder()
                    .friendId(friendId)
                    .messages(List.of())
                    .hasMore(false)
                    .build();
        }

        int size = normalizeHistorySize(requestedSize);
        PageRequest page = PageRequest.of(0, size + 1);
        List<ChatMessage> newestFirst;
        newestFirst = before == null
                ? messageRepository.findLatest(conversation.getId(), page)
                : messageRepository.findBeforeCursor(conversation.getId(), before, beforeId, page);
        boolean hasMore = newestFirst.size() > size;
        List<ChatMessage> selected = hasMore
                ? new ArrayList<>(newestFirst.subList(0, size))
                : new ArrayList<>(newestFirst);
        Instant nextBefore = hasMore && !selected.isEmpty()
                ? selected.get(selected.size() - 1).getCreatedAt()
                : null;
        UUID nextBeforeId = hasMore && !selected.isEmpty()
                ? selected.get(selected.size() - 1).getId()
                : null;
        Collections.reverse(selected);

        return ChatHistoryDTO.builder()
                .friendId(friendId)
                .messages(selected.stream().map(this::toDTO).toList())
                .hasMore(hasMore)
                .nextBefore(nextBefore)
                .nextBeforeId(nextBeforeId)
                .build();
    }

    private User requireActiveSender(String username) {
        if (username == null || username.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByEmail(username)
                .filter(this::isAvailable)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private NormalizedMessage normalizeRequest(SendChatMessageRequest request) {
        if (request == null
                || request.getRecipientId() == null
                || request.getClientMessageId() == null) {
            throw new AppException(ErrorCode.INVALID_CHAT_MESSAGE);
        }
        ChatMessageType messageType = request.getMessageType() == null
                ? ChatMessageType.TEXT
                : request.getMessageType();
        return switch (messageType) {
            case TEXT -> normalizeText(request);
            case LOCATION -> normalizeLocation(request);
        };
    }

    private NormalizedMessage normalizeText(SendChatMessageRequest request) {
        if (request.getLatitude() != null
                || request.getLongitude() != null
                || request.getAccuracyMeters() != null) {
            throw new AppException(ErrorCode.INVALID_CHAT_LOCATION);
        }
        return new NormalizedMessage(
                ChatMessageType.TEXT,
                normalizeContent(request.getContent()),
                null,
                null,
                null
        );
    }

    private NormalizedMessage normalizeLocation(SendChatMessageRequest request) {
        if (request.getContent() != null
                || !isFiniteInRange(request.getLatitude(), -90D, 90D)
                || !isFiniteInRange(request.getLongitude(), -180D, 180D)
                || !isFiniteInRange(
                        request.getAccuracyMeters(),
                        0D,
                        MAX_LOCATION_ACCURACY_METERS
                )) {
            throw new AppException(ErrorCode.INVALID_CHAT_LOCATION);
        }
        return new NormalizedMessage(
                ChatMessageType.LOCATION,
                null,
                canonicalZero(request.getLatitude()),
                canonicalZero(request.getLongitude()),
                canonicalZero(request.getAccuracyMeters())
        );
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_CONTENT_LENGTH) {
            throw new AppException(ErrorCode.INVALID_CHAT_MESSAGE);
        }
        return normalized;
    }

    private boolean isFiniteInRange(Double value, double minimum, double maximum) {
        return value != null
                && Double.isFinite(value)
                && value >= minimum
                && value <= maximum;
    }

    private double canonicalZero(double value) {
        return value == 0D ? 0D : value;
    }

    private boolean matches(ChatMessage existing, NormalizedMessage normalized) {
        return existing.getMessageType() == normalized.messageType()
                && Objects.equals(existing.getContent(), normalized.content())
                && Objects.equals(existing.getLatitude(), normalized.latitude())
                && Objects.equals(existing.getLongitude(), normalized.longitude())
                && Objects.equals(existing.getAccuracyMeters(), normalized.accuracyMeters());
    }

    private int normalizeHistorySize(Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_HISTORY_SIZE : requestedSize;
        return Math.max(1, Math.min(MAX_HISTORY_SIZE, size));
    }

    private boolean isAvailable(User user) {
        return user != null && !user.isDeleted() && user.getStatus() == AccountStatus.ACTIVE;
    }

    private UserPair canonicalPair(User first, User second) {
        if (first.getId().toString().compareTo(second.getId().toString()) < 0) {
            return new UserPair(first, second);
        }
        return new UserPair(second, first);
    }

    private ChatDelivery delivery(ChatMessage message, String recipientUsername) {
        return new ChatDelivery(toDTO(message), recipientUsername);
    }

    private ChatMessageDTO toDTO(ChatMessage message) {
        ChatConversation conversation = message.getConversation();
        UUID senderId = message.getSender().getId();
        UUID recipientId = conversation.getUserLow().getId().equals(senderId)
                ? conversation.getUserHigh().getId()
                : conversation.getUserLow().getId();
        return ChatMessageDTO.builder()
                .id(message.getId())
                .clientMessageId(message.getClientMessageId())
                .senderId(senderId)
                .recipientId(recipientId)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .latitude(message.getLatitude())
                .longitude(message.getLongitude())
                .accuracyMeters(message.getAccuracyMeters())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private record NormalizedMessage(
            ChatMessageType messageType,
            String content,
            Double latitude,
            Double longitude,
            Double accuracyMeters
    ) {
    }

    private record UserPair(User low, User high) {
    }
}
