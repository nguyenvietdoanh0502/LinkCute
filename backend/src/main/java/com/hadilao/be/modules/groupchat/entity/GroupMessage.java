package com.hadilao.be.modules.groupchat.entity;

import com.hadilao.be.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_group_messages", uniqueConstraints = @UniqueConstraint(
        name = "uq_group_message_client", columnNames = {"group_id", "sender_id", "client_message_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class GroupMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;
    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;
    @Column(nullable = false, length = 2000)
    private String content;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
