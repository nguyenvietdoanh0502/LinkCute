package com.hadilao.be.modules.user.repository;

import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPinCode(String pinCode);
    Optional<User> findByPinCodeAndIsDeletedFalseAndStatus(String pinCode, AccountStatus status);
    Optional<User> findByIdAndIsDeletedFalseAndStatus(UUID id, AccountStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select candidate from User candidate where candidate.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByPinCode(String pinCode);
}
