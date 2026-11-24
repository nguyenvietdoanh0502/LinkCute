package com.hadilao.be.modules.groupchat.entity;

import com.hadilao.be.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_groups")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatGroup {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;
    @Column(name = "avatar_public_id")
    private String avatarPublicId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
