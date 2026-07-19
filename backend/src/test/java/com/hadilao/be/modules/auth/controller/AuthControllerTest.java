package com.hadilao.be.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadilao.be.modules.auth.service.MailService;
import com.hadilao.be.modules.auth.service.OtpService;
import com.hadilao.be.modules.auth.service.RateLimiterService;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.auth.dto.RegisterRequest;
import com.hadilao.be.modules.auth.dto.ResetPasswordRequest;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests for AuthController")
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MailService mailService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void allowRateLimitedRequests() {
        Mockito.when(rateLimiterService.isRegisterAllowed(anyString(), anyString())).thenReturn(true);
        Mockito.when(rateLimiterService.isForgotPasswordAllowed(anyString(), anyString())).thenReturn(true);
        Mockito.when(rateLimiterService.isOtpVerificationAllowed(anyString(), anyString(), any())).thenReturn(true);
    }

    @Nested
    @DisplayName("Success Patterns")
    class SuccessPatterns {

        @Test
        @DisplayName("Should register a new user successfully and return message")
        void testRegister_Success() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password123!");
            request.setConfirmPassword("Password123!");
            request.setFullName("Test User");

            Mockito.when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
            
            User savedUser = User.builder()
                    .id(UUID.randomUUID())
                    .email(request.getEmail())
                    .fullName(request.getFullName())
                    .pinCode("RML-123456")
                    .status(AccountStatus.PENDING)
                    .build();

            Mockito.when(userRepository.save(any(User.class))).thenReturn(savedUser);
            Mockito.when(otpService.generateAndSaveOtp(any(), any())).thenReturn("123456");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                    .with(httpRequest -> {
                        httpRequest.setRemoteAddr("203.0.113.10");
                        httpRequest.addHeader("X-Forwarded-For", "198.51.100.99");
                        return httpRequest;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("OTP has been sent to your email"))
                    .andExpect(jsonPath("$.data").doesNotExist());
            Mockito.verify(rateLimiterService).isRegisterAllowed("test@example.com", "203.0.113.10");
        }
    }

    @Nested
    @DisplayName("Failure Patterns")
    class FailurePatterns {

        @Test
        @DisplayName("Should return 400 Bad Request when the email is already registered")
        void testRegister_UserExisted() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password123!");
            request.setConfirmPassword("Password123!");
            request.setFullName("Test User");

            User existingUser = User.builder()
                    .id(UUID.randomUUID())
                    .email(request.getEmail())
                    .status(AccountStatus.ACTIVE)
                    .build();

            Mockito.when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("USER_EXISTED"))
                    .andExpect(jsonPath("$.message").value("User existed"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when the password is shorter than 8 characters")
        void testRegister_InvalidPassword() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("short"); // Less than 8 chars
            request.setConfirmPassword("short");
            request.setFullName("Test User");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("WEAK_PASSWORD"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when the email format is invalid")
        void testRegister_InvalidEmail() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("invalid-email");
            request.setPassword("Password123!");
            request.setConfirmPassword("Password123!");
            request.setFullName("Test User");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_EMAIL"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when password and confirm password do not match")
        void testRegister_PasswordMismatch() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password123!");
            request.setConfirmPassword("differentPassword123!");
            request.setFullName("Test User");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("PASSWORD_MISMATCH"));
        }

        @Test
        @DisplayName("Should reject malformed reset token before processing it")
        void testResetPassword_InvalidTokenFormat() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setEmail("test@example.com");
            request.setResetToken("not-a-jwt");
            request.setNewPassword("NewPassword123!");
            request.setConfirmNewPassword("NewPassword123!");

            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_RESET_TOKEN"));
        }

        @Test
        @DisplayName("Should return a domain error when refresh token is missing")
        void testRefreshToken_MissingToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("Should return 400 when JSON is malformed")
        void testLogin_MalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{bad"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("Should require a six-digit OTP")
        void testVerifyOtp_RejectsLetters() throws Exception {
            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"test@example.com\",\"otpCode\":\"abcdef\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_OTP_FORMAT"));
        }

        @Test
        @DisplayName("Should return a structured 401 for protected endpoints")
        void testChangePassword_Anonymous() throws Exception {
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }
    }
}

