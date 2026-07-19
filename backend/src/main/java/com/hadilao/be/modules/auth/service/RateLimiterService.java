package com.hadilao.be.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(String key, int maxRequests, Duration timeWindow) {
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount != null && currentCount == 1) {
            // Lần đầu tiên tạo key, set thời gian sống (TTL)
            redisTemplate.expire(key, timeWindow);
        }

        return currentCount != null && currentCount <= maxRequests;
    }

    public boolean isRegisterAllowed(String email, String ipAddress) {
        boolean emailAllowed = isAllowed(
                "rate_limit:register:email:" + normalizeEmail(email),
                3,
                Duration.ofHours(1)
        );
        boolean ipAllowed = isAllowed(
                "rate_limit:register:ip:" + ipAddress,
                5,
                Duration.ofHours(1)
        );
        return emailAllowed && ipAllowed;
    }

    public boolean isLoginIpAllowed(String ipAddress) {
        String key = "rate_limit:login_ip:" + ipAddress;
        return isAllowed(key, 10, Duration.ofMinutes(15));
    }


    public boolean isLoginEmailAllowed(String email) {
        String key = "failed:login:" + email;
        return isAllowed(key, 5, Duration.ofMinutes(15));
    }

    public void resetFailedLoginCount(String email) {
        String key = "failed:login:" + email;
        redisTemplate.delete(key);
    }

    public boolean isEmailLocked(String email) {
        String key = "failed:login:" + email;
        String countStr = redisTemplate.opsForValue().get(key);
        if (countStr != null) {
            int count = Integer.parseInt(countStr);
            return count > 5;
        }
        return false;
    }

    public boolean isForgotPasswordAllowed(String email, String ipAddress) {
        boolean emailAllowed = isAllowed(
                "rate_limit:forgot:email:" + normalizeEmail(email),
                3,
                Duration.ofHours(1)
        );
        boolean ipAllowed = isAllowed(
                "rate_limit:forgot:ip:" + ipAddress,
                10,
                Duration.ofHours(1)
        );
        return emailAllowed && ipAllowed;
    }

    public boolean isOtpVerificationAllowed(String email, String ipAddress, OtpService.OtpType type) {
        String otpType = type.name().toLowerCase(Locale.ROOT);
        boolean emailAllowed = isAllowed(
                "rate_limit:otp:" + otpType + ":email:" + normalizeEmail(email),
                5,
                Duration.ofMinutes(5)
        );
        boolean ipAllowed = isAllowed(
                "rate_limit:otp:" + otpType + ":ip:" + ipAddress,
                20,
                Duration.ofMinutes(5)
        );
        return emailAllowed && ipAllowed;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
