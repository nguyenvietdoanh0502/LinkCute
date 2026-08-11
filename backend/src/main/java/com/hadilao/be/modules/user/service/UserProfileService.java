package com.hadilao.be.modules.user.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.user.dto.StoredAvatar;
import com.hadilao.be.modules.user.dto.UpdateUserProfileRequest;
import com.hadilao.be.modules.user.dto.UserDTO;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.enums.Gender;
import com.hadilao.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Year;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserRepository userRepository;
    private final AvatarStorageService avatarStorageService;

    @Transactional(readOnly = true)
    public UserDTO getCurrentProfile() {
        return UserMapper.toDTO(getCurrentUser());
    }

    @Transactional
    public UserDTO updateCurrentProfile(UpdateUserProfileRequest request) {
        User user = getCurrentUser();
        String fullName = normalizeRequiredName(request.getFullName());
        String address = normalizeAddress(request.getAddress());
        validateBirthYear(request.getBirthYear());

        if (isLegacyGeneratedAvatar(user)) {
            user.setAvatarUrl(null);
        }
        user.setFullName(fullName);
        user.setGender(request.getGender() == null ? Gender.UNSPECIFIED : request.getGender());
        user.setBirthYear(request.getBirthYear());
        user.setAddress(address);

        return UserMapper.toDTO(userRepository.save(user));
    }

    @Transactional
    public UserDTO updateAvatar(MultipartFile file) {
        User user = getCurrentUser();
        String oldUrl = user.getAvatarUrl();
        String oldPublicId = user.getAvatarPublicId();
        StoredAvatar uploaded = avatarStorageService.upload(user.getId(), file);
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();

        if (synchronizedTransaction) {
            registerReplacementCleanup(oldPublicId, uploaded.publicId());
        }

        try {
            user.setAvatarUrl(uploaded.url());
            user.setAvatarPublicId(uploaded.publicId());
            User saved = userRepository.saveAndFlush(user);
            if (!synchronizedTransaction) {
                safeDelete(oldPublicId);
            }
            return UserMapper.toDTO(saved);
        } catch (RuntimeException exception) {
            user.setAvatarUrl(oldUrl);
            user.setAvatarPublicId(oldPublicId);
            if (!synchronizedTransaction) {
                safeDelete(uploaded.publicId());
            }
            throw exception;
        }
    }

    @Transactional
    public UserDTO removeAvatar() {
        User user = getCurrentUser();
        String oldPublicId = user.getAvatarPublicId();

        user.setAvatarUrl(null);
        user.setAvatarPublicId(null);
        User saved = userRepository.saveAndFlush(user);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            registerDeleteAfterCommit(oldPublicId);
        } else {
            safeDelete(oldPublicId);
        }
        return UserMapper.toDTO(saved);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userRepository.findByEmail(authentication.getName())
                .filter(user -> !user.isDeleted() && user.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private boolean isLegacyGeneratedAvatar(User user) {
        return user.getAvatarPublicId() == null
                && user.getAvatarUrl() != null
                && user.getAvatarUrl().startsWith("https://ui-avatars.com/api/");
    }

    private String normalizeRequiredName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new AppException(ErrorCode.INVALID_FULL_NAME);
        }
        return normalized;
    }

    private String normalizeAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw new AppException(ErrorCode.INVALID_ADDRESS);
        }
        return normalized;
    }

    private void validateBirthYear(Integer birthYear) {
        if (birthYear != null && (birthYear < 1900 || birthYear > Year.now().getValue())) {
            throw new AppException(ErrorCode.INVALID_BIRTH_YEAR);
        }
    }

    private void registerReplacementCleanup(String oldPublicId, String newPublicId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeDelete(oldPublicId);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    safeDelete(newPublicId);
                }
            }
        });
    }

    private void registerDeleteAfterCommit(String publicId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeDelete(publicId);
            }
        });
    }

    private void safeDelete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            avatarStorageService.delete(publicId);
        } catch (RuntimeException exception) {
            log.warn("Could not clean up previous avatar {}: {}", publicId, exception.getMessage());
        }
    }
}
