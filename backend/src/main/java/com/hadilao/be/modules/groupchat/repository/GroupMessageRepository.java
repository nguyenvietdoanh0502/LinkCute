package com.hadilao.be.modules.groupchat.repository;

import com.hadilao.be.modules.groupchat.entity.GroupMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {
    Optional<GroupMessage> findByGroupIdAndSenderIdAndClientMessageId(UUID groupId, UUID senderId, UUID clientMessageId);
    @Query("select m from GroupMessage m join fetch m.sender where m.group.id = :groupId and (:before is null or m.id < :before) order by m.id desc")
    List<GroupMessage> findHistory(@Param("groupId") UUID groupId, @Param("before") Long before, Pageable page);
    @Query("select m from GroupMessage m join fetch m.sender where m.group.id = :groupId and m.id > :after order by m.id")
    List<GroupMessage> findUpdates(@Param("groupId") UUID groupId, @Param("after") Long after, Pageable page);
    @Modifying
    @Query("delete from GroupMessage m where m.group.id = :groupId")
    void deleteForGroup(@Param("groupId") UUID groupId);
}
