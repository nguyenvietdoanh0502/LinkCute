package com.hadilao.be.modules.place.repository;

import com.hadilao.be.modules.place.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByPlaceIdOrderByPublishedAtDesc(UUID placeId);
}
