package com.hadilao.be.modules.plan.entity;

import com.hadilao.be.modules.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "plans",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_plans_owner_client_plan",
                columnNames = {"owner_id", "client_plan_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "client_plan_id", nullable = false, length = 128)
    private String clientPlanId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "planned_date", nullable = false)
    private LocalDate date;

    @Column(name = "client_updated_at", nullable = false)
    private Instant clientUpdatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void applySync(String name, LocalDate date, Instant clientUpdatedAt) {
        this.name = name;
        this.date = date;
        this.clientUpdatedAt = clientUpdatedAt;
        // Mark the aggregate dirty even when only its items changed so @Version
        // protects full-snapshot replacement from stale clients.
        this.updatedAt = Instant.now();
    }
}
