package com.hadilao.be.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.hadilao.be.modules.user.enums.AccountStatus;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SessionRevocationService sessionRevocationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNotAuthenticateNonAccessToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer refresh-token");
        when(jwtProvider.isAccessToken("refresh-token")).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtProvider, never()).extractUsername("refresh-token");
        verify(userDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatesValidAccessToken() throws Exception {
        com.hadilao.be.modules.user.entity.User userDetails =
                com.hadilao.be.modules.user.entity.User.builder()
                        .email("user@example.com")
                        .status(AccountStatus.ACTIVE)
                        .sessionVersion(2L)
                        .build();
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");
        when(jwtProvider.isAccessToken("access-token")).thenReturn(true);
        when(jwtProvider.extractJwtId("access-token")).thenReturn("token-id");
        when(redisTemplate.hasKey("blacklist:jti:token-id")).thenReturn(false);
        when(jwtProvider.extractUsername("access-token")).thenReturn("user@example.com");
        when(jwtProvider.extractSessionVersion("access-token")).thenReturn(2L);
        when(sessionRevocationService.isSessionActive(userDetails, 2L)).thenReturn(true);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtProvider.isTokenValid("access-token", userDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull()
                .extracting(authentication -> authentication.getName())
                .isEqualTo("user@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateRevokedSession() throws Exception {
        com.hadilao.be.modules.user.entity.User userDetails =
                com.hadilao.be.modules.user.entity.User.builder()
                        .email("user@example.com")
                        .status(AccountStatus.ACTIVE)
                        .sessionVersion(2L)
                        .build();
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");
        when(jwtProvider.isAccessToken("access-token")).thenReturn(true);
        when(jwtProvider.extractJwtId("access-token")).thenReturn("token-id");
        when(redisTemplate.hasKey("blacklist:jti:token-id")).thenReturn(false);
        when(jwtProvider.extractUsername("access-token")).thenReturn("user@example.com");
        when(jwtProvider.extractSessionVersion("access-token")).thenReturn(1L);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(sessionRevocationService.isSessionActive(userDetails, 1L)).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtProvider, never()).isTokenValid("access-token", userDetails);
        verify(filterChain).doFilter(request, response);
    }
}
