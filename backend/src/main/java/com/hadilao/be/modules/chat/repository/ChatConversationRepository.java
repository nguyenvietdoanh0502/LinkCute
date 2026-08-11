package com.hadilao.be.modules.chat.repository;

import com.hadilao.be.modules.chat.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    @Query("""
            select conversation
            from ChatConversation conversation
            join fetch conversation.userLow
            join fetch conversation.userHigh
            where conversation.userLow.id = :userLowId
              and conversation.userHigh.id = :userHighId
            """)
    Optional<ChatConversation> findByUserPair(
            @Param("userLowId") UUID userLowId,
            @Param("userHighId") UUID userHighId
    );
}
