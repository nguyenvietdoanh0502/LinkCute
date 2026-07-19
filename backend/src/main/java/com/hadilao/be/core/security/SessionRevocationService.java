package com.hadilao.be.core.security;

import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionRevocationService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public boolean isSessionActive(User user, Long tokenSessionVersion) {
        return tokenSessionVersion != null
                && tokenSessionVersion == user.getSessionVersion();
    }

    public void revokeAllSessions(User user) {
        user.setSessionVersion(user.getSessionVersion() + 1);
        userRepository.save(user);
        try {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + user.getEmail());
        } catch (DataAccessException exception) {
            log.warn("Could not remove refresh token while revoking sessions for user {}", user.getId());
        }
    }
}
