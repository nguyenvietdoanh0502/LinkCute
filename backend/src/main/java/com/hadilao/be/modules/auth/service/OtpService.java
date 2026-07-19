package com.hadilao.be.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final long OTP_TTL_MINUTES = 5;

    public enum OtpType {
        REGISTER("otp:register:"),
        FORGOT_PASSWORD("otp:forgot:");

        private final String prefix;

        OtpType(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    public String generateAndSaveOtp(String email, OtpType type) {
        int otpNumber = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(otpNumber);

        redisTemplate.opsForValue().set(type.getPrefix() + email, otpCode, Duration.ofMinutes(OTP_TTL_MINUTES));

        return otpCode;
    }

    public boolean verifyOtp(String email, String otpCode, OtpType type) {
        String savedOtp = redisTemplate.opsForValue().get(type.getPrefix() + email);
        if (savedOtp != null && savedOtp.equals(otpCode)) {
            redisTemplate.delete(type.getPrefix() + email);
            return true;
        }
        return false;
    }
}
