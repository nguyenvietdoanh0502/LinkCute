package com.hadilao.be.modules.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for OtpService")
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpService otpService;

    @Test
    @DisplayName("Should generate and save OTP to Redis for REGISTER type")
    void testGenerateAndSaveOtp_Register() {
        // Arrange
        String email = "test@example.com";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        String otpCode = otpService.generateAndSaveOtp(email, OtpService.OtpType.REGISTER);

        // Assert
        assertThat(otpCode).hasSize(6).containsOnlyDigits();
        verify(valueOperations).set(eq("otp:register:" + email), eq(otpCode), any(Duration.class));
    }

    @Test
    @DisplayName("Should generate and save OTP to Redis for FORGOT_PASSWORD type")
    void testGenerateAndSaveOtp_ForgotPassword() {
        // Arrange
        String email = "test@example.com";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        String otpCode = otpService.generateAndSaveOtp(email, OtpService.OtpType.FORGOT_PASSWORD);

        // Assert
        assertThat(otpCode).hasSize(6).containsOnlyDigits();
        verify(valueOperations).set(eq("otp:forgot:" + email), eq(otpCode), any(Duration.class));
    }

    @Test
    @DisplayName("Should return true when OTP is valid and delete it")
    void testVerifyOtp_Valid() {
        // Arrange
        String email = "test@example.com";
        String otpCode = "123456";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:register:" + email)).thenReturn(otpCode);

        // Act
        boolean result = otpService.verifyOtp(email, otpCode, OtpService.OtpType.REGISTER);

        // Assert
        assertThat(result).isTrue();
        verify(redisTemplate).delete("otp:register:" + email);
    }

    @Test
    @DisplayName("Should return false when OTP is invalid")
    void testVerifyOtp_Invalid() {
        // Arrange
        String email = "test@example.com";
        String otpCode = "123456";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:register:" + email)).thenReturn("different");

        // Act
        boolean result = otpService.verifyOtp(email, otpCode, OtpService.OtpType.REGISTER);

        // Assert
        assertThat(result).isFalse();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Should return false when OTP is missing in Redis")
    void testVerifyOtp_Missing() {
        // Arrange
        String email = "test@example.com";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:register:" + email)).thenReturn(null);

        // Act
        boolean result = otpService.verifyOtp(email, "123456", OtpService.OtpType.REGISTER);

        // Assert
        assertThat(result).isFalse();
    }
}
