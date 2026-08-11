package com.hadilao.be.modules.friendship.repository;

import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
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
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("""
            select friendship
            from Friendship friendship
            join fetch friendship.userLow
            join fetch friendship.userHigh
            join fetch friendship.requester
            where friendship.userLow.id = :userLowId
              and friendship.userHigh.id = :userHighId
            """)
    Optional<Friendship> findByUserPair(
            @Param("userLowId") UUID userLowId,
            @Param("userHighId") UUID userHighId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select friendship
            from Friendship friendship
            where friendship.userLow.id = :userLowId
              and friendship.userHigh.id = :userHighId
              and friendship.status = com.hadilao.be.modules.friendship.enums.FriendshipStatus.ACCEPTED
            """)
    Optional<Friendship> findAcceptedPairForUpdate(
            @Param("userLowId") UUID userLowId,
            @Param("userHighId") UUID userHighId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select friendship from Friendship friendship where friendship.id = :id")
    Optional<Friendship> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select friendship
            from Friendship friendship
            join fetch friendship.userLow
            join fetch friendship.userHigh
            join fetch friendship.requester
            where friendship.status = :status
              and (friendship.userLow.id = :userId or friendship.userHigh.id = :userId)
            order by friendship.acceptedAt desc, friendship.createdAt desc
            """)
    List<Friendship> findAllForUserByStatus(
            @Param("userId") UUID userId,
            @Param("status") FriendshipStatus status
    );

    @Query("""
            select friendship
            from Friendship friendship
            join fetch friendship.userLow
            join fetch friendship.userHigh
            join fetch friendship.requester
            where friendship.status = com.hadilao.be.modules.friendship.enums.FriendshipStatus.PENDING
              and (friendship.userLow.id = :userId or friendship.userHigh.id = :userId)
              and friendship.requester.id <> :userId
            order by friendship.createdAt desc
            """)
    List<Friendship> findIncomingRequests(@Param("userId") UUID userId);

    @Query("""
            select friendship
            from Friendship friendship
            join fetch friendship.userLow
            join fetch friendship.userHigh
            join fetch friendship.requester
            where friendship.status = com.hadilao.be.modules.friendship.enums.FriendshipStatus.PENDING
              and friendship.requester.id = :userId
            order by friendship.createdAt desc
            """)
    List<Friendship> findOutgoingRequests(@Param("userId") UUID userId);
}
