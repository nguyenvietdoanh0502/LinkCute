package com.hadilao.be.modules.chat.repository;

import com.hadilao.be.modules.chat.entity.ChatConversation;
import com.hadilao.be.modules.chat.entity.ChatMessage;
import com.hadilao.be.modules.chat.enums.ChatMessageType;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_repository;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ChatRepositoryTest {

    private static final Instant SHARED_TIMESTAMP =
            Instant.parse("2026-08-10T08:15:30Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Test
    void compoundCursorDoesNotSkipMessagesThatShareTheSameTimestamp() {
        User alice = saveUser("alice@example.com", "RML-410001");
        User bob = saveUser("bob@example.com", "RML-410002");
        User low = lower(alice, bob);
        User high = low == alice ? bob : alice;
        ChatConversation conversation = conversationRepository.saveAndFlush(
                ChatConversation.builder()
                        .userLow(low)
                        .userHigh(high)
                        .build()
        );

        List<ChatMessage> saved = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            saved.add(messageRepository.saveAndFlush(ChatMessage.builder()
                    .conversation(conversation)
                    .sender(index % 2 == 0 ? alice : bob)
                    .userLowId(low.getId())
                    .userHighId(high.getId())
                    .clientMessageId(UUID.randomUUID())
                    .content("message-" + index)
                    .createdAt(SHARED_TIMESTAMP)
                    .build()));
        }

        List<ChatMessage> firstPage = messageRepository.findLatest(
                conversation.getId(),
                PageRequest.of(0, 2)
        );
        ChatMessage cursor = firstPage.get(firstPage.size() - 1);
        List<ChatMessage> secondPage = messageRepository.findBeforeCursor(
                conversation.getId(),
                cursor.getCreatedAt(),
                cursor.getId(),
                PageRequest.of(0, 2)
        );

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(2);
        assertThat(firstPage)
                .extracting(ChatMessage::getId)
                .doesNotContainAnyElementsOf(
                        secondPage.stream().map(ChatMessage::getId).toList()
                );
        HashSet<UUID> pagedIds = new HashSet<>(
                firstPage.stream().map(ChatMessage::getId).toList()
        );
        pagedIds.addAll(secondPage.stream().map(ChatMessage::getId).toList());
        assertThat(pagedIds)
                .withFailMessage("Both cursor pages should contain all messages exactly once")
                .containsExactlyInAnyOrderElementsOf(
                        saved.stream().map(ChatMessage::getId).toList()
                );
    }

    @Test
    void retryLookupIsScopedByConversationSenderAndClientMessageId() {
        User alice = saveUser("retry-alice@example.com", "RML-420001");
        User bob = saveUser("retry-bob@example.com", "RML-420002");
        User low = lower(alice, bob);
        User high = low == alice ? bob : alice;
        ChatConversation conversation = conversationRepository.saveAndFlush(
                ChatConversation.builder()
                        .userLow(low)
                        .userHigh(high)
                        .build()
        );
        UUID sharedClientId = UUID.randomUUID();
        ChatMessage fromAlice = saveMessage(conversation, alice, sharedClientId, "from Alice");
        ChatMessage fromBob = saveMessage(conversation, bob, sharedClientId, "from Bob");

        assertThat(messageRepository.findByClientMessageId(
                conversation.getId(),
                alice.getId(),
                sharedClientId
        )).contains(fromAlice);
        assertThat(messageRepository.findByClientMessageId(
                conversation.getId(),
                bob.getId(),
                sharedClientId
        )).contains(fromBob);
    }

    @Test
    void locationPayloadRoundTripsThroughHistoryQuery() {
        User alice = saveUser("location-alice@example.com", "RML-430001");
        User bob = saveUser("location-bob@example.com", "RML-430002");
        User low = lower(alice, bob);
        User high = low == alice ? bob : alice;
        ChatConversation conversation = conversationRepository.saveAndFlush(
                ChatConversation.builder()
                        .userLow(low)
                        .userHigh(high)
                        .build()
        );
        ChatMessage saved = messageRepository.saveAndFlush(ChatMessage.builder()
                .conversation(conversation)
                .sender(alice)
                .userLowId(low.getId())
                .userHighId(high.getId())
                .clientMessageId(UUID.randomUUID())
                .messageType(ChatMessageType.LOCATION)
                .latitude(21.0278)
                .longitude(105.8342)
                .accuracyMeters(9.75)
                .createdAt(SHARED_TIMESTAMP)
                .build());

        List<ChatMessage> history = messageRepository.findLatest(
                conversation.getId(),
                PageRequest.of(0, 10)
        );

        assertThat(history).singleElement().satisfies(message -> {
            assertThat(message.getId()).isEqualTo(saved.getId());
            assertThat(message.getMessageType()).isEqualTo(ChatMessageType.LOCATION);
            assertThat(message.getContent()).isNull();
            assertThat(message.getLatitude()).isEqualTo(21.0278);
            assertThat(message.getLongitude()).isEqualTo(105.8342);
            assertThat(message.getAccuracyMeters()).isEqualTo(9.75);
        });
    }

    private ChatMessage saveMessage(
            ChatConversation conversation,
            User sender,
            UUID clientMessageId,
            String content
    ) {
        return messageRepository.saveAndFlush(ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .userLowId(conversation.getUserLow().getId())
                .userHighId(conversation.getUserHigh().getId())
                .clientMessageId(clientMessageId)
                .content(content)
                .createdAt(SHARED_TIMESTAMP)
                .build());
    }

    private User saveUser(String email, String pinCode) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .fullName(email.substring(0, email.indexOf('@')))
                .pinCode(pinCode)
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private User lower(User first, User second) {
        return first.getId().toString().compareTo(second.getId().toString()) < 0
                ? first
                : second;
    }
}
