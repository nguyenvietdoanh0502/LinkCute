package com.hadilao.be.modules.user.service;

import com.hadilao.be.core.security.SessionRevocationService;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRevocationService sessionRevocationService;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    void bansUserAndRevokesEverySession() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .status(AccountStatus.ACTIVE)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userAccountService.banUser(userId);

        assertThat(user.getStatus()).isEqualTo(AccountStatus.BANNED);
        verify(sessionRevocationService).revokeAllSessions(user);
    }
}
