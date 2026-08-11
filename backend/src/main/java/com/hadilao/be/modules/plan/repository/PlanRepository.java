package com.hadilao.be.modules.plan.repository;

import com.hadilao.be.modules.plan.entity.Plan;
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
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select plan
            from Plan plan
            join fetch plan.owner
            where plan.owner.id = :ownerId
              and plan.clientPlanId = :clientPlanId
            """)
    Optional<Plan> findOwnedByClientPlanIdForUpdate(
            @Param("ownerId") UUID ownerId,
            @Param("clientPlanId") String clientPlanId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from Plan plan join fetch plan.owner where plan.id = :planId")
    Optional<Plan> findByIdForUpdate(@Param("planId") UUID planId);

    @Query("""
            select plan
            from Plan plan
            join fetch plan.owner
            where plan.owner.id = :userId
               or exists (
                    select member.id
                    from PlanMember member
                    where member.plan.id = plan.id
                      and member.user.id = :userId
               )
            order by plan.updatedAt desc, plan.id asc
            """)
    List<Plan> findAllAccessibleByUserId(@Param("userId") UUID userId);
}
