package com.hadilao.be.modules.plan.repository;

import com.hadilao.be.modules.plan.entity.PlanInvitation;
import com.hadilao.be.modules.plan.enums.PlanInvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanInvitationRepository extends JpaRepository<PlanInvitation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from PlanInvitation invitation
            join fetch invitation.plan plan
            join fetch plan.owner
            join fetch invitation.inviter
            join fetch invitation.invitee
            where invitation.id = :invitationId
            """)
    Optional<PlanInvitation> findByIdForUpdate(@Param("invitationId") UUID invitationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from PlanInvitation invitation
            join fetch invitation.inviter
            join fetch invitation.invitee
            where invitation.plan.id = :planId
              and invitation.invitee.id = :inviteeId
            """)
    Optional<PlanInvitation> findForUpdateByPlanAndInvitee(
            @Param("planId") UUID planId,
            @Param("inviteeId") UUID inviteeId
    );

    @Query("""
            select invitation
            from PlanInvitation invitation
            join fetch invitation.plan plan
            join fetch plan.owner
            join fetch invitation.inviter
            join fetch invitation.invitee
            where invitation.invitee.id = :userId
              and invitation.status = :status
            order by invitation.sentAt desc, invitation.id asc
            """)
    List<PlanInvitation> findIncomingByStatus(
            @Param("userId") UUID userId,
            @Param("status") PlanInvitationStatus status
    );

    @Query("""
            select invitation
            from PlanInvitation invitation
            join fetch invitation.plan plan
            join fetch plan.owner
            join fetch invitation.inviter
            join fetch invitation.invitee
            where invitation.inviter.id = :userId
              and invitation.status = :status
            order by invitation.sentAt desc, invitation.id asc
            """)
    List<PlanInvitation> findOutgoingByStatus(
            @Param("userId") UUID userId,
            @Param("status") PlanInvitationStatus status
    );
}
