package com.hadilao.be.modules.chat.entity;

import com.hadilao.be.modules.chat.enums.ChatMessageType;
import com.hadilao.be.modules.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_chat_messages_client_id",
                columnNames = {"conversation_id", "sender_id", "client_message_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "user_low_id", nullable = false, updatable = false)
    private UUID userLowId;

    @Column(name = "user_high_id", nullable = false, updatable = false)
    private UUID userHighId;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType = ChatMessageType.TEXT;

    @Column(length = 2000)
    private String content;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (messageType == null) {
            messageType = ChatMessageType.TEXT;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
