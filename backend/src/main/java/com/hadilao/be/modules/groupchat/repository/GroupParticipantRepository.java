package com.hadilao.be.modules.groupchat.repository;

import com.hadilao.be.modules.groupchat.entity.GroupParticipant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface GroupParticipantRepository extends JpaRepository<GroupParticipant, UUID> {
    Optional<GroupParticipant> findByGroupIdAndUserId(UUID groupId, UUID userId);
    @Query("select p from GroupParticipant p join fetch p.group g join fetch g.owner join fetch p.invitedBy where p.user.id = :userId order by p.createdAt desc")
    List<GroupParticipant> findOverview(@Param("userId") UUID userId);
    @Query("select p from GroupParticipant p join fetch p.user join fetch p.invitedBy where p.group.id = :groupId order by p.createdAt, p.id")
    List<GroupParticipant> findParticipants(@Param("groupId") UUID groupId);
    @Modifying
    @Query("delete from GroupParticipant p where p.group.id = :groupId")
    void deleteForGroup(@Param("groupId") UUID groupId);
}
