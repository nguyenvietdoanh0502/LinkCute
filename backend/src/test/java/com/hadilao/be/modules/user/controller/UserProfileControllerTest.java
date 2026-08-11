package com.hadilao.be.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.service.MailService;
import com.hadilao.be.modules.auth.service.OtpService;
import com.hadilao.be.modules.user.dto.UpdateUserProfileRequest;
import com.hadilao.be.modules.user.dto.UserDTO;
import com.hadilao.be.modules.user.enums.Gender;
import com.hadilao.be.modules.user.repository.UserRepository;
import com.hadilao.be.modules.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserProfileControllerTest {

    private static final String AUTHENTICATED_EMAIL = "profile@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MailService mailService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Test
    void anonymousProfileRequestsReturnStructuredAuthenticationError() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Unauthorized User\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        mockMvc.perform(multipart("/api/v1/users/me/avatar").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        verify(userProfileService, never()).getCurrentProfile();
        verify(userProfileService, never()).updateCurrentProfile(any(UpdateUserProfileRequest.class));
        verify(userProfileService, never()).updateAvatar(any());
    }

    @Test
    void getCurrentProfileReturnsPrivateProfileWithoutInternalAvatarPublicId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.getCurrentProfile()).thenReturn(profile(userId));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value(AUTHENTICATED_EMAIL))
                .andExpect(jsonPath("$.data.fullName").value("Profile User"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.birthYear").value(1995))
                .andExpect(jsonPath("$.data.age").value(java.time.Year.now().getValue() - 1995))
                .andExpect(jsonPath("$.data.address").value("Ha Noi"))
                .andExpect(jsonPath("$.data.avatarPublicId").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.sessionVersion").doesNotExist())
                .andExpect(jsonPath("$.data.rowVersion").doesNotExist());
    }

    @Test
    void patchProfileValidatesAndReturnsTheUpdatedProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "Updated User", Gender.OTHER, 1990, "Da Nang");
        UserDTO response = profile(userId);
        response.setFullName("Updated User");
        response.setGender(Gender.OTHER);
        response.setBirthYear(1990);
        response.setAddress("Da Nang");
        when(userProfileService.updateCurrentProfile(any(UpdateUserProfileRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated"))
                .andExpect(jsonPath("$.data.fullName").value("Updated User"))
                .andExpect(jsonPath("$.data.gender").value("OTHER"))
                .andExpect(jsonPath("$.data.birthYear").value(1990))
                .andExpect(jsonPath("$.data.address").value("Da Nang"));

        verify(userProfileService).updateCurrentProfile(any(UpdateUserProfileRequest.class));
    }

    @Test
    void patchProfileRejectsMissingNameAndInvalidBirthYearBeforeCallingService() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":1990}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_FULL_NAME"));

        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Valid User\",\"birthYear\":1899}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_BIRTH_YEAR"));

        verify(userProfileService, never()).updateCurrentProfile(any(UpdateUserProfileRequest.class));
    }

    @Test
    void patchProfileRejectsMalformedGenderAndOversizedAddress() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Valid User\",\"gender\":\"SECRET\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));

        UpdateUserProfileRequest longAddress = new UpdateUserProfileRequest(
                "Valid User", Gender.MALE, 1990, "x".repeat(256));
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longAddress)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADDRESS"));
    }

    @Test
    void concurrentProfileUpdateReturnsAStableConflict() throws Exception {
        when(userProfileService.updateCurrentProfile(any(UpdateUserProfileRequest.class)))
                .thenThrow(new OptimisticLockingFailureException("stale profile"));

        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Valid User\",\"gender\":\"OTHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.errorCode").value("CONCURRENCY_CONFLICT"));
    }

    @Test
    void multipartAvatarUploadUsesFilePartAndNeverExposesPublicId() throws Exception {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        UserDTO updated = profile(userId);
        updated.setAvatarUrl("https://res.cloudinary.com/demo/avatar.png");
        when(userProfileService.updateAvatar(any())).thenReturn(updated);

        mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(file)
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Avatar updated"))
                .andExpect(jsonPath("$.data.avatarUrl")
                        .value("https://res.cloudinary.com/demo/avatar.png"))
                .andExpect(jsonPath("$.data.avatarPublicId").doesNotExist());

        verify(userProfileService).updateAvatar(any());
    }

    @Test
    void missingMultipartFileReturnsStructuredInvalidAvatarError() throws Exception {
        mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_AVATAR"));

        verify(userProfileService, never()).updateAvatar(any());
    }

    @Test
    void avatarStorageFailureKeepsStableGatewayError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        when(userProfileService.updateAvatar(any()))
                .thenThrow(new AppException(ErrorCode.AVATAR_UPLOAD_FAILED));

        mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(file)
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.errorCode").value("AVATAR_UPLOAD_FAILED"));
    }

    @Test
    void deleteAvatarReturnsSanitizedUpdatedProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDTO updated = profile(userId);
        updated.setAvatarUrl(null);
        when(userProfileService.removeAvatar()).thenReturn(updated);

        mockMvc.perform(delete("/api/v1/users/me/avatar")
                        .with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Avatar removed"))
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.avatarPublicId").doesNotExist());

        verify(userProfileService).removeAvatar();
    }

    private UserDTO profile(UUID id) {
        return UserDTO.builder()
                .id(id)
                .email(AUTHENTICATED_EMAIL)
                .fullName("Profile User")
                .pinCode("RML-123456")
                .avatarUrl("https://cdn.example.com/avatar.png")
                .gender(Gender.FEMALE)
                .birthYear(1995)
                .age(java.time.Year.now().getValue() - 1995)
                .address("Ha Noi")
                .build();
    }
}
