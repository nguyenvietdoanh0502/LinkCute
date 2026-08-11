package com.hadilao.be.modules.chat.repository;

import com.hadilao.be.modules.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
            select message
            from ChatMessage message
            join fetch message.sender
            join fetch message.conversation conversation
            join fetch conversation.userLow
            join fetch conversation.userHigh
            where conversation.id = :conversationId
              and message.sender.id = :senderId
              and message.clientMessageId = :clientMessageId
            """)
    Optional<ChatMessage> findByClientMessageId(
            @Param("conversationId") UUID conversationId,
            @Param("senderId") UUID senderId,
            @Param("clientMessageId") UUID clientMessageId
    );

    @Query("""
            select message
            from ChatMessage message
            join fetch message.sender
            join fetch message.conversation conversation
            join fetch conversation.userLow
            join fetch conversation.userHigh
            where conversation.id = :conversationId
            order by message.createdAt desc, message.id desc
            """)
    List<ChatMessage> findLatest(
            @Param("conversationId") UUID conversationId,
            Pageable pageable
    );

    @Query("""
            select message
            from ChatMessage message
            join fetch message.sender
            join fetch message.conversation conversation
            join fetch conversation.userLow
            join fetch conversation.userHigh
            where conversation.id = :conversationId
              and (
                    message.createdAt < :before
                    or (message.createdAt = :before and message.id < :beforeId)
              )
            order by message.createdAt desc, message.id desc
            """)
    List<ChatMessage> findBeforeCursor(
            @Param("conversationId") UUID conversationId,
            @Param("before") Instant before,
            @Param("beforeId") UUID beforeId,
            Pageable pageable
    );
}
