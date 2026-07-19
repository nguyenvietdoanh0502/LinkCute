package com.hadilao.be.modules.auth.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.core.security.JwtProvider;
import com.hadilao.be.core.security.SessionRevocationService;
import com.hadilao.be.modules.auth.dto.AuthResponse;
import com.hadilao.be.modules.auth.dto.ChangePasswordRequest;
import com.hadilao.be.modules.auth.dto.RegisterRequest;
import com.hadilao.be.modules.auth.dto.RefreshTokenRequest;
import com.hadilao.be.modules.auth.dto.ResetPasswordRequest;
import com.hadilao.be.modules.auth.dto.VerifyOtpRequest;
import com.hadilao.be.modules.auth.dto.VerifyOtpForgotPasswordResponse;
import com.hadilao.be.modules.user.dto.UserDTO;
import com.hadilao.be.modules.user.dto.UserRegistrationCommand;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import com.hadilao.be.modules.user.service.UserRegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for AuthService")
class AuthServiceTest {

    private static final String CLIENT_IP = "203.0.113.10";

    @Mock
    private UserRegistrationService userRegistrationService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private OtpService otpService;
    @Mock
    private MailService mailService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private SessionRevocationService sessionRevocationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpiration", 3600000L);
        ReflectionTestUtils.setField(authService, "resetPasswordExpiration", 300000L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should process registration and send OTP email")
        void testRegister_Success() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password123!");
            request.setFullName("Test User");

            when(passwordEncoder.encode(anyString())).thenReturn("encoded_pwd");
            when(rateLimiterService.isRegisterAllowed(request.getEmail(), CLIENT_IP)).thenReturn(true);
            when(otpService.generateAndSaveOtp(request.getEmail(), OtpService.OtpType.REGISTER)).thenReturn("123456");

            // Act
            authService.register(request, CLIENT_IP);

            // Assert
            verify(userRegistrationService).registerNewUser(argThat(command ->
                command.getEmail().equals(request.getEmail()) &&
                command.getHashedPassword().equals("encoded_pwd") &&
                command.getFullName().equals(request.getFullName())
            ));
            verify(otpService).generateAndSaveOtp(request.getEmail(), OtpService.OtpType.REGISTER);
            verify(mailService).sendOtpEmail(request.getEmail(), "123456");
        }

        @Test
        @DisplayName("Should reject registration when email or IP exceeds the limit")
        void testRegister_RateLimited() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password123!");
            request.setFullName("Test User");
            when(rateLimiterService.isRegisterAllowed(request.getEmail(), CLIENT_IP)).thenReturn(false);

            assertThatThrownBy(() -> authService.register(request, CLIENT_IP))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);

            verify(userRegistrationService, never()).registerNewUser(any());
            verify(otpService, never()).generateAndSaveOtp(anyString(), any());
            verify(mailService, never()).sendOtpEmail(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("verifyOtp Tests")
    class VerifyOtpTests {

        @Test
        @DisplayName("Should verify OTP and return AuthResponse when valid")
        void testVerifyOtp_Success() {
            // Arrange
            VerifyOtpRequest request = new VerifyOtpRequest();
            request.setEmail("test@example.com");
            request.setOtpCode("123456");

            UserDTO userDTO = UserDTO.builder()
                    .email(request.getEmail())
                    .fullName("Test User")
                    .build();
            User user = User.builder()
                    .email(request.getEmail())
                    .status(AccountStatus.ACTIVE)
                    .sessionVersion(4L)
                    .build();

            when(otpService.verifyOtp(request.getEmail(), request.getOtpCode(), OtpService.OtpType.REGISTER)).thenReturn(true);
            when(rateLimiterService.isOtpVerificationAllowed(
                    request.getEmail(), CLIENT_IP, OtpService.OtpType.REGISTER)).thenReturn(true);
            when(userRegistrationService.verifyUser(request.getEmail())).thenReturn(userDTO);
            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(jwtProvider.generateToken(any(UserDetails.class), eq(4L))).thenReturn("access_token");
            when(jwtProvider.generateRefreshToken(any(UserDetails.class), eq(4L))).thenReturn("refresh_token");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Act
            AuthResponse response = authService.verifyOtp(request, CLIENT_IP);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access_token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
            assertThat(response.getUser().getEmail()).isEqualTo(request.getEmail());
            verify(otpService).verifyOtp(request.getEmail(), request.getOtpCode(), OtpService.OtpType.REGISTER);
            verify(userRegistrationService).verifyUser(request.getEmail());
            verify(valueOperations).set(
                    "refresh:test@example.com",
                    "refresh_token",
                    Duration.ofDays(30)
            );
        }

        @Test
        @DisplayName("Should throw OTP_INVALID when OTP is invalid")
        void testVerifyOtp_InvalidOtp() {
            // Arrange
            VerifyOtpRequest request = new VerifyOtpRequest();
            request.setEmail("test@example.com");
            request.setOtpCode("wrong");

            when(rateLimiterService.isOtpVerificationAllowed(
                    request.getEmail(), CLIENT_IP, OtpService.OtpType.REGISTER)).thenReturn(true);
            when(otpService.verifyOtp(request.getEmail(), request.getOtpCode(), OtpService.OtpType.REGISTER)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.verifyOtp(request, CLIENT_IP))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_INVALID);
            
            verify(userRegistrationService, never()).verifyUser(anyString());
        }

        @Test
        @DisplayName("Should reject OTP verification when email or IP exceeds the limit")
        void testVerifyOtp_RateLimited() {
            VerifyOtpRequest request = new VerifyOtpRequest();
            request.setEmail("test@example.com");
            request.setOtpCode("123456");
            when(rateLimiterService.isOtpVerificationAllowed(
                    request.getEmail(), CLIENT_IP, OtpService.OtpType.REGISTER)).thenReturn(false);

            assertThatThrownBy(() -> authService.verifyOtp(request, CLIENT_IP))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);

            verify(otpService, never()).verifyOtp(anyString(), anyString(), any());
            verify(userRegistrationService, never()).verifyUser(anyString());
        }
    }

    @Nested
    @DisplayName("reset password Tests")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should store reset token with reset-token TTL")
        void testVerifyForgotPasswordOtp_UsesResetTokenTtl() {
            VerifyOtpRequest request = new VerifyOtpRequest();
            request.setEmail("test@example.com");
            request.setOtpCode("123456");

            when(rateLimiterService.isOtpVerificationAllowed(
                    request.getEmail(), CLIENT_IP, OtpService.OtpType.FORGOT_PASSWORD)).thenReturn(true);
            when(otpService.verifyOtp(
                    request.getEmail(), request.getOtpCode(), OtpService.OtpType.FORGOT_PASSWORD)).thenReturn(true);
            when(jwtProvider.generateResetPasswordToken(any(UserDetails.class))).thenReturn("reset.token.signature");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            VerifyOtpForgotPasswordResponse response =
                    authService.verifyOtpForgotPassword(request, CLIENT_IP);

            assertThat(response.getResetToken()).isEqualTo("reset.token.signature");
            verify(valueOperations).set(
                    "reset_token:test@example.com",
                    "reset.token.signature",
                    Duration.ofMinutes(5)
            );
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("Should validate and atomically consume reset token")
        void testResetPassword_ValidatesAndConsumesToken() {
            ResetPasswordRequest request = resetPasswordRequest();
            User user = User.builder()
                    .email(request.getEmail())
                    .password("old_password")
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(redisTemplate.execute(
                    any(DefaultRedisScript.class),
                    eq(List.of("reset_token:test@example.com")),
                    eq(request.getResetToken())
            )).thenReturn(1L);
            when(passwordEncoder.encode(request.getNewPassword())).thenReturn("new_password_hash");

            authService.resetPassword(request);

            verify(jwtProvider).validateResetPasswordToken(request.getResetToken(), request.getEmail());
            assertThat(user.getPassword()).isEqualTo("new_password_hash");
            verify(sessionRevocationService).revokeAllSessions(user);
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("Should reject an already consumed or replaced reset token")
        void testResetPassword_RejectsTokenNotStoredInRedis() {
            ResetPasswordRequest request = resetPasswordRequest();
            User user = User.builder()
                    .email(request.getEmail())
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(redisTemplate.execute(
                    any(DefaultRedisScript.class),
                    eq(List.of("reset_token:test@example.com")),
                    eq(request.getResetToken())
            )).thenReturn(0L);

            assertThatThrownBy(() -> authService.resetPassword(request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESET_TOKEN);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject token when JWT validation fails")
        void testResetPassword_RejectsInvalidJwt() {
            ResetPasswordRequest request = resetPasswordRequest();
            doThrow(new AppException(ErrorCode.INVALID_RESET_TOKEN))
                    .when(jwtProvider)
                    .validateResetPasswordToken(request.getResetToken(), request.getEmail());

            assertThatThrownBy(() -> authService.resetPassword(request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESET_TOKEN);

            verify(userRepository, never()).findByEmail(anyString());
            verify(redisTemplate, never()).execute(any(), anyList(), any());
        }

        private ResetPasswordRequest resetPasswordRequest() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setEmail("test@example.com");
            request.setResetToken("reset.token.signature");
            request.setNewPassword("NewPassword123!");
            request.setConfirmNewPassword("NewPassword123!");
            return request;
        }
    }

    @Nested
    @DisplayName("change password Tests")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should revoke every session after changing password")
        void testChangePassword_RevokesAllSessions() {
            String email = "test@example.com";
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("OldPassword123!");
            request.setNewPassword("NewPassword123!");
            request.setConfirmNewPassword("NewPassword123!");
            User user = User.builder()
                    .email(email)
                    .password("old_hash")
                    .status(AccountStatus.ACTIVE)
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(email, null)
            );

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.getOldPassword(), "old_hash")).thenReturn(true);
            when(passwordEncoder.encode(request.getNewPassword())).thenReturn("new_hash");

            authService.changePassword(request);

            assertThat(user.getPassword()).isEqualTo("new_hash");
            verify(sessionRevocationService).revokeAllSessions(user);
        }
    }

    @Nested
    @DisplayName("refresh token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should reject refresh token from a revoked session version")
        void testRefreshToken_RejectsRevokedSession() {
            String refreshToken = "refresh_token";
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken(refreshToken);
            User user = User.builder()
                    .email("test@example.com")
                    .status(AccountStatus.ACTIVE)
                    .sessionVersion(2L)
                    .build();

            when(jwtProvider.validateRefreshToken(refreshToken)).thenReturn(user);
            when(jwtProvider.extractSessionVersion(refreshToken)).thenReturn(1L);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refresh:test@example.com")).thenReturn(refreshToken);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(sessionRevocationService.isSessionActive(user, 1L)).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);

            verify(jwtProvider, never()).generateToken(any(UserDetails.class), anyLong());
            verify(jwtProvider, never()).generateRefreshToken(any(UserDetails.class), anyLong());
        }
    }
}
