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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final String EMAIL = "profile@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AvatarStorageService avatarStorageService;

    @InjectMocks
    private UserProfileService userProfileService;

    @BeforeEach
    void authenticateUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
    }

    @AfterEach
    void clearThreadState() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getCurrentProfileReturnsOnlyMappedProfileData() {
        User user = activeUser();
        user.setGender(Gender.FEMALE);
        user.setBirthYear(1995);
        user.setAddress("Ha Noi");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        UserDTO result = userProfileService.getCurrentProfile();

        assertThat(result.getId()).isEqualTo(user.getId());
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getFullName()).isEqualTo("Profile User");
        assertThat(result.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(result.getBirthYear()).isEqualTo(1995);
        assertThat(result.getAge()).isEqualTo(Year.now().getValue() - 1995);
        assertThat(result.getAddress()).isEqualTo("Ha Noi");
    }

    @Test
    void updateCurrentProfileNormalizesInputAndDefaultsMissingGender() {
        User user = activeUser();
        user.setGender(Gender.MALE);
        user.setAddress("Old address");
        user.setAvatarUrl("https://ui-avatars.com/api/?name=Profile+User");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserDTO result = userProfileService.updateCurrentProfile(
                new UpdateUserProfileRequest("  New Profile Name  ", null, 1990, "   "));

        assertThat(user.getFullName()).isEqualTo("New Profile Name");
        assertThat(user.getGender()).isEqualTo(Gender.UNSPECIFIED);
        assertThat(user.getBirthYear()).isEqualTo(1990);
        assertThat(user.getAddress()).isNull();
        assertThat(user.getAvatarUrl()).isNull();
        assertThat(result.getFullName()).isEqualTo("New Profile Name");
        verify(userRepository).save(user);
    }

    @Test
    void updateCurrentProfileRejectsInvalidServiceLevelValuesBeforeSaving() {
        User user = activeUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userProfileService.updateCurrentProfile(
                new UpdateUserProfileRequest(" ", Gender.OTHER, 1990, "Ha Noi")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FULL_NAME);

        assertThatThrownBy(() -> userProfileService.updateCurrentProfile(
                new UpdateUserProfileRequest("Valid Name", Gender.OTHER,
                        Year.now().getValue() + 1, "Ha Noi")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_BIRTH_YEAR);

        assertThatThrownBy(() -> userProfileService.updateCurrentProfile(
                new UpdateUserProfileRequest("Valid Name", Gender.OTHER, 1990, "x".repeat(256))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ADDRESS);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unavailableOrAnonymousUsersCannotReadTheirProfile() {
        User bannedUser = activeUser();
        bannedUser.setStatus(AccountStatus.BANNED);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(bannedUser));

        assertThatThrownBy(userProfileService::getCurrentProfile)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(userProfileService::getCurrentProfile)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void updateAvatarPersistsNewAssetAndDeletesPreviousAssetWithoutTransactionSynchronization() {
        User user = activeUser();
        user.setAvatarUrl("https://cdn.example.com/old.png");
        user.setAvatarPublicId("avatars/old");
        MockMultipartFile file = pngFile();
        StoredAvatar uploaded = new StoredAvatar(
                "https://res.cloudinary.com/demo/new.png", "avatars/new");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(avatarStorageService.upload(user.getId(), file)).thenReturn(uploaded);
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        UserDTO result = userProfileService.updateAvatar(file);

        assertThat(user.getAvatarUrl()).isEqualTo(uploaded.url());
        assertThat(user.getAvatarPublicId()).isEqualTo(uploaded.publicId());
        assertThat(result.getAvatarUrl()).isEqualTo(uploaded.url());
        verify(avatarStorageService).delete("avatars/old");
    }

    @Test
    void updateAvatarRestoresOldStateAndDeletesNewAssetWhenPersistenceFails() {
        User user = activeUser();
        user.setAvatarUrl("https://cdn.example.com/old.png");
        user.setAvatarPublicId("avatars/old");
        MockMultipartFile file = pngFile();
        StoredAvatar uploaded = new StoredAvatar(
                "https://res.cloudinary.com/demo/new.png", "avatars/new");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(avatarStorageService.upload(user.getId(), file)).thenReturn(uploaded);
        when(userRepository.saveAndFlush(user))
                .thenThrow(new DataIntegrityViolationException("save failed"));

        assertThatThrownBy(() -> userProfileService.updateAvatar(file))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(user.getAvatarUrl()).isEqualTo("https://cdn.example.com/old.png");
        assertThat(user.getAvatarPublicId()).isEqualTo("avatars/old");
        verify(avatarStorageService).delete("avatars/new");
        verify(avatarStorageService, never()).delete("avatars/old");
    }

    @Test
    void updateAvatarDefersCleanupAndDeletesNewAssetOnTransactionRollback() {
        User user = activeUser();
        user.setAvatarPublicId("avatars/old");
        MockMultipartFile file = pngFile();
        StoredAvatar uploaded = new StoredAvatar(
                "https://res.cloudinary.com/demo/new.png", "avatars/new");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(avatarStorageService.upload(user.getId(), file)).thenReturn(uploaded);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        TransactionSynchronizationManager.initSynchronization();

        userProfileService.updateAvatar(file);

        verify(avatarStorageService, never()).delete(anyString());
        List<TransactionSynchronization> callbacks =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(callbacks).hasSize(1);
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(avatarStorageService).delete("avatars/new");
        verify(avatarStorageService, never()).delete("avatars/old");
    }

    @Test
    void updateAvatarDeletesPreviousAssetOnlyAfterTransactionCommit() {
        User user = activeUser();
        user.setAvatarPublicId("avatars/old");
        MockMultipartFile file = pngFile();
        StoredAvatar uploaded = new StoredAvatar(
                "https://res.cloudinary.com/demo/new.png", "avatars/new");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(avatarStorageService.upload(user.getId(), file)).thenReturn(uploaded);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        TransactionSynchronizationManager.initSynchronization();

        userProfileService.updateAvatar(file);

        verify(avatarStorageService, never()).delete(anyString());
        List<TransactionSynchronization> callbacks =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(callbacks).hasSize(1);
        callbacks.forEach(TransactionSynchronization::afterCommit);
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(avatarStorageService).delete("avatars/old");
        verify(avatarStorageService, never()).delete("avatars/new");
    }

    @Test
    void removeAvatarClearsStoredReferencesAndDeletesTheRemoteAsset() {
        User user = activeUser();
        user.setAvatarUrl("https://cdn.example.com/current.png");
        user.setAvatarPublicId("avatars/current");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        UserDTO result = userProfileService.removeAvatar();

        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getAvatarPublicId()).isNull();
        assertThat(result.getAvatarUrl()).isNull();
        verify(avatarStorageService).delete("avatars/current");
    }

    @Test
    void avatarStorageIsNotCalledWhenCurrentUserCannotBeResolved() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.updateAvatar(pngFile()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);

        verifyNoInteractions(avatarStorageService);
    }

    private User activeUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .password("secret-hash")
                .fullName("Profile User")
                .pinCode("RML-123456")
                .avatarUrl("https://cdn.example.com/avatar.png")
                .gender(Gender.UNSPECIFIED)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    }
}
