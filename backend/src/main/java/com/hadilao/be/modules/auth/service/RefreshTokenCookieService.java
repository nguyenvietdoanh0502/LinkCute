package com.hadilao.be.modules.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Component
public class RefreshTokenCookieService {

    public static final String COOKIE_NAME = "__Host-linkcute_refresh";

    private static final Set<String> ALLOWED_SAME_SITE_VALUES = Set.of("lax", "strict", "none");

    private final Duration maxAge;
    private final String sameSite;

    public RefreshTokenCookieService(
            @Value("${roamly.jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${roamly.auth.refresh-cookie.same-site:None}") String sameSite) {
        if (refreshTokenExpiration <= 0) {
            throw new IllegalArgumentException("Refresh token expiration must be positive");
        }
        this.maxAge = Duration.ofMillis(refreshTokenExpiration);
        this.sameSite = normalizeSameSite(sameSite);
    }

    public void write(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = baseCookie(refreshToken)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite(sameSite);
    }

    private static String normalizeSameSite(String configuredValue) {
        String normalized = configuredValue == null
                ? ""
                : configuredValue.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SAME_SITE_VALUES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Refresh cookie SameSite must be one of: Lax, Strict, None");
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
