package com.hadilao.be.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiterService = new RateLimiterService(redisTemplate);
    }

    @Test
    void limitsRegistrationByNormalizedEmailAndIp() {
        when(valueOperations.increment("rate_limit:register:email:user@example.com")).thenReturn(1L);
        when(valueOperations.increment("rate_limit:register:ip:203.0.113.10")).thenReturn(1L);

        assertThat(rateLimiterService.isRegisterAllowed(" User@Example.COM ", "203.0.113.10")).isTrue();

        verify(redisTemplate).expire(
                "rate_limit:register:email:user@example.com", Duration.ofHours(1));
        verify(redisTemplate).expire(
                "rate_limit:register:ip:203.0.113.10", Duration.ofHours(1));
    }

    @Test
    void checksIpEvenWhenEmailLimitIsExceeded() {
        when(valueOperations.increment("rate_limit:register:email:user@example.com")).thenReturn(4L);
        when(valueOperations.increment("rate_limit:register:ip:203.0.113.10")).thenReturn(1L);

        assertThat(rateLimiterService.isRegisterAllowed("user@example.com", "203.0.113.10")).isFalse();

        verify(valueOperations).increment("rate_limit:register:ip:203.0.113.10");
    }

    @Test
    void limitsOtpVerificationByTypeEmailAndIp() {
        when(valueOperations.increment(
                "rate_limit:otp:forgot_password:email:user@example.com")).thenReturn(1L);
        when(valueOperations.increment(
                "rate_limit:otp:forgot_password:ip:203.0.113.10")).thenReturn(1L);

        assertThat(rateLimiterService.isOtpVerificationAllowed(
                "user@example.com", "203.0.113.10", OtpService.OtpType.FORGOT_PASSWORD)).isTrue();

        verify(redisTemplate).expire(
                "rate_limit:otp:forgot_password:email:user@example.com", Duration.ofMinutes(5));
        verify(redisTemplate).expire(
                "rate_limit:otp:forgot_password:ip:203.0.113.10", Duration.ofMinutes(5));
    }
}
