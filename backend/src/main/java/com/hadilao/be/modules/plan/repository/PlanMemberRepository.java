package com.hadilao.be.modules.plan.repository;

import com.hadilao.be.modules.plan.entity.PlanMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanMemberRepository extends JpaRepository<PlanMember, UUID> {

    boolean existsByPlanIdAndUserId(UUID planId, UUID userId);

    Optional<PlanMember> findByPlanIdAndUserId(UUID planId, UUID userId);

    @Query("""
            select member
            from PlanMember member
            join fetch member.user
            where member.plan.id = :planId
            order by member.joinedAt asc, member.id asc
            """)
    List<PlanMember> findAllForPlan(@Param("planId") UUID planId);
}
