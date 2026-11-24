package com.hadilao.be.modules.groupchat.entity;

import com.hadilao.be.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_group_participants", uniqueConstraints = @UniqueConstraint(
        name = "uq_group_participant", columnNames = {"group_id", "user_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class GroupParticipant {
    public enum Status { INVITED, MEMBER }
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_id", nullable = false)
    private User invitedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "joined_at")
    private Instant joinedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
