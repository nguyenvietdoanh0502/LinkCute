package com.hadilao.be.modules.plan.repository;

import com.hadilao.be.modules.plan.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanItemRepository extends JpaRepository<PlanItem, UUID> {

    List<PlanItem> findAllByPlanIdOrderByPositionAscIdAsc(UUID planId);

    long countByPlanId(UUID planId);

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("delete from PlanItem item where item.plan.id = :planId")
    int deleteAllByPlanId(@Param("planId") UUID planId);
}
