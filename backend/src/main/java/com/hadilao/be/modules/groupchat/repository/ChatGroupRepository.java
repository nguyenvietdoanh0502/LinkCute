package com.hadilao.be.modules.groupchat.repository;

import com.hadilao.be.modules.groupchat.entity.ChatGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ChatGroup g where g.id = :id")
    Optional<ChatGroup> findForUpdate(@Param("id") UUID id);
}
