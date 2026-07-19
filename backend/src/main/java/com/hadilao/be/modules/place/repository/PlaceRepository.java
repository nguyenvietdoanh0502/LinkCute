package com.hadilao.be.modules.place.repository;

import com.hadilao.be.modules.place.entity.Place;
import com.hadilao.be.modules.place.enums.PlaceCategory;
import com.hadilao.be.modules.place.enums.PlaceSourceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaceRepository extends JpaRepository<Place, UUID>, JpaSpecificationExecutor<Place> {

    Optional<Place> findByOsmId(String osmId);

    Optional<Place> findByOvertureId(String overtureId);

    Optional<Place> findByGooglePlaceId(String googlePlaceId);

    List<Place> findAllBySourceIdIsNotNull();

    List<Place> findAllByOvertureIdIsNotNull();

    List<Place> findAllByOsmIdIsNotNull();

    List<Place> findAllByIsDeletedFalse();

    List<Place> findAllBySourceIdIsNotNullAndOvertureIdIsNullAndOsmIdIsNullAndIsDeletedFalse();

    List<Place> findAllBySourceIdIsNullAndIsDeletedFalse();

    @Query("SELECT p.category, COUNT(p) FROM Place p WHERE p.isDeleted = false GROUP BY p.category")
    List<Object[]> countByCategory();

    @Query("SELECT p.district, COUNT(p) FROM Place p WHERE p.isDeleted = false AND p.district IS NOT NULL GROUP BY p.district ORDER BY COUNT(p) DESC")
    List<Object[]> countByDistrict();
}
