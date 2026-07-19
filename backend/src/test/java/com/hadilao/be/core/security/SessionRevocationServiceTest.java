package com.hadilao.be.core.security;

import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class SessionRevocationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserRepository userRepository;

    private SessionRevocationService sessionRevocationService;

    @BeforeEach
    void setUp() {
        sessionRevocationService = new SessionRevocationService(redisTemplate, userRepository);
    }

    @Test
    void validatesVersionStoredOnUser() {
        User user = User.builder()
                .email("user@example.com")
                .status(AccountStatus.ACTIVE)
                .sessionVersion(3L)
                .build();

        assertThat(sessionRevocationService.isSessionActive(user, 3L)).isTrue();
        assertThat(sessionRevocationService.isSessionActive(user, 2L)).isFalse();
        assertThat(sessionRevocationService.isSessionActive(user, null)).isFalse();
    }

    @Test
    void revokesAllSessionsByIncrementingPersistentVersionAndDeletingRefreshToken() {
        User user = User.builder()
                .email("user@example.com")
                .status(AccountStatus.ACTIVE)
                .sessionVersion(3L)
                .build();

        sessionRevocationService.revokeAllSessions(user);

        assertThat(user.getSessionVersion()).isEqualTo(4L);
        verify(userRepository).save(user);
        verify(redisTemplate).delete("refresh:user@example.com");
    }

    @Test
    void keepsPersistentRevocationWhenRedisCleanupFails() {
        User user = User.builder()
                .email("user@example.com")
                .status(AccountStatus.ACTIVE)
                .sessionVersion(3L)
                .build();
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(redisTemplate).delete("refresh:user@example.com");

        sessionRevocationService.revokeAllSessions(user);

        assertThat(user.getSessionVersion()).isEqualTo(4L);
        verify(userRepository).save(user);
    }
}
