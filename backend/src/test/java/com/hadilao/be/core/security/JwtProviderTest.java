package com.hadilao.be.core.security;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.user.enums.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(mock(UserDetailsService.class));
        ReflectionTestUtils.setField(
                jwtProvider,
                "secretKey",
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
        );
        ReflectionTestUtils.setField(jwtProvider, "jwtExpiration", 60_000L);
        ReflectionTestUtils.setField(jwtProvider, "refreshExpiration", 60_000L);
        ReflectionTestUtils.setField(jwtProvider, "resetExpiration", 60_000L);
        userDetails = new User("user@example.com", "", Collections.emptyList());
    }

    @Test
    void recognizesOnlyAccessTokenAsAccessToken() {
        assertThat(jwtProvider.isAccessToken(jwtProvider.generateToken(userDetails))).isTrue();
        assertThat(jwtProvider.isAccessToken(jwtProvider.generateRefreshToken(userDetails))).isFalse();
        assertThat(jwtProvider.isAccessToken(jwtProvider.generateResetPasswordToken(userDetails))).isFalse();
    }

    @Test
    void preservesSessionVersionInAccessAndRefreshTokens() {
        String accessToken = jwtProvider.generateToken(userDetails, 7L);
        String refreshToken = jwtProvider.generateRefreshToken(userDetails, 7L);

        assertThat(jwtProvider.extractSessionVersion(accessToken)).isEqualTo(7L);
        assertThat(jwtProvider.extractSessionVersion(refreshToken)).isEqualTo(7L);
    }

    @Test
    void rejectsTokenForBannedUser() {
        com.hadilao.be.modules.user.entity.User bannedUser =
                com.hadilao.be.modules.user.entity.User.builder()
                        .email("banned@example.com")
                        .status(AccountStatus.BANNED)
                        .build();
        String accessToken = jwtProvider.generateToken(bannedUser, bannedUser.getSessionVersion());

        assertThat(jwtProvider.isTokenValid(accessToken, bannedUser)).isFalse();
    }

    @Test
    void validatesResetPasswordTokenTypeExpirationAndSubject() {
        String resetToken = jwtProvider.generateResetPasswordToken(userDetails);

        jwtProvider.validateResetPasswordToken(resetToken, "USER@example.com");

        assertThatThrownBy(() -> jwtProvider.validateResetPasswordToken(
                jwtProvider.generateToken(userDetails), userDetails.getUsername()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESET_TOKEN);

        assertThatThrownBy(() -> jwtProvider.validateResetPasswordToken(
                resetToken, "another@example.com"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESET_TOKEN);
    }

    @Test
    void rejectsExpiredOrMalformedResetPasswordToken() {
        ReflectionTestUtils.setField(jwtProvider, "resetExpiration", -1L);
        String expiredToken = jwtProvider.generateResetPasswordToken(userDetails);

        assertThatThrownBy(() -> jwtProvider.validateResetPasswordToken(
                expiredToken, userDetails.getUsername()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESET_TOKEN);

        assertThatThrownBy(() -> jwtProvider.validateResetPasswordToken(
                "not-a-jwt", userDetails.getUsername()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESET_TOKEN);
    }
}
