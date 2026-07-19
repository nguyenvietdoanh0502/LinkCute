package com.hadilao.be.modules.place.repository;

import com.hadilao.be.modules.place.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    List<Photo> findByPlaceIdOrderBySortOrderAsc(UUID placeId);

    List<Photo> findByPlaceIdInOrderByPlaceIdAscSortOrderAsc(List<UUID> placeIds);
}
