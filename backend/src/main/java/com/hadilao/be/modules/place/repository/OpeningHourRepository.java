package com.hadilao.be.modules.place.repository;

import com.hadilao.be.modules.place.entity.OpeningHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OpeningHourRepository extends JpaRepository<OpeningHour, UUID> {

    List<OpeningHour> findByPlaceIdOrderByDayOfWeekAsc(UUID placeId);

    // Find places that are open now (normal hours: open <= currentTime <= close)
    @Query("SELECT DISTINCT o.place.id FROM OpeningHour o " +
           "WHERE o.dayOfWeek = :dayOfWeek " +
           "AND o.crossesMidnight = false " +
           "AND o.openTime <= :currentTime " +
           "AND o.closeTime >= :currentTime")
    List<UUID> findOpenPlaceIdsNormal(@Param("dayOfWeek") int dayOfWeek,
                                      @Param("currentTime") LocalTime currentTime);

    // Find places that are open now (crosses-midnight hours: currentTime >= open OR currentTime <= close)
    @Query("SELECT DISTINCT o.place.id FROM OpeningHour o " +
           "WHERE o.crossesMidnight = true " +
           "AND ((o.dayOfWeek = :currentDay AND o.openTime <= :currentTime) " +
           "OR (o.dayOfWeek = :previousDay AND o.closeTime >= :currentTime))")
    List<UUID> findOpenPlaceIdsCrossMidnight(@Param("currentDay") int currentDay,
                                              @Param("previousDay") int previousDay,
                                              @Param("currentTime") LocalTime currentTime);
}
