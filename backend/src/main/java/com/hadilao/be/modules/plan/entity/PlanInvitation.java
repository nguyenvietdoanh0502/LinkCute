package com.hadilao.be.modules.plan.entity;

import com.hadilao.be.modules.plan.enums.PlanInvitationStatus;
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
import jakarta.persistence.PreUpdate;
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
        name = "plan_invitations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_plan_invitations_plan_invitee",
                columnNames = {"plan_id", "invitee_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlanInvitationStatus status = PlanInvitationStatus.PENDING;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (sentAt == null) {
            sentAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = PlanInvitationStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void resetPending(User nextInviter) {
        Instant now = Instant.now();
        inviter = nextInviter;
        status = PlanInvitationStatus.PENDING;
        sentAt = now;
        respondedAt = null;
        updatedAt = now;
    }

    public void accept() {
        transitionTo(PlanInvitationStatus.ACCEPTED);
    }

    public void decline() {
        transitionTo(PlanInvitationStatus.DECLINED);
    }

    public void cancel() {
        transitionTo(PlanInvitationStatus.CANCELLED);
    }

    private void transitionTo(PlanInvitationStatus nextStatus) {
        Instant now = Instant.now();
        status = nextStatus;
        respondedAt = now;
        updatedAt = now;
    }
}
