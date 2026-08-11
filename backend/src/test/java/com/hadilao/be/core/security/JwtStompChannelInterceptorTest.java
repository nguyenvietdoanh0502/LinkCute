package com.hadilao.be.core.security;

import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtStompChannelInterceptorTest {

    private static final String TOKEN = "access-token";
    private static final String EMAIL = "chat@example.com";

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SessionRevocationService sessionRevocationService;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private JwtStompChannelInterceptor interceptor;

    @Test
    void connectAuthenticatesAValidBearerTokenAndKeepsItForFrameRevalidation() {
        User user = activeUser();
        stubValidToken(user);
        Message<byte[]> connect = stomp(
                StompCommand.CONNECT,
                null,
                null,
                "authorization",
                "Bearer " + TOKEN
        );

        Message<?> result = interceptor.preSend(connect, channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                result,
                StompHeaderAccessor.class
        );
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
        Authentication authentication = (Authentication) accessor.getUser();
        assertThat(authentication.getName()).isEqualTo(EMAIL);
        assertThat(authentication.getCredentials()).isEqualTo(TOKEN);
    }

    @Test
    void stompAliasAuthenticatesExactlyLikeAConnectFrame() {
        User user = activeUser();
        stubValidToken(user);
        Message<byte[]> connect = stomp(
                StompCommand.STOMP,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN
        );

        Message<?> result = interceptor.preSend(connect, channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                result,
                StompHeaderAccessor.class
        );
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
        assertThat(accessor.getUser().getName()).isEqualTo(EMAIL);
    }

    @Test
    void connectRejectsMissingOrMalformedAuthorizationHeaders() {
        Message<byte[]> missing = stomp(StompCommand.CONNECT, null, null, null, null);
        Message<byte[]> wrongScheme = stomp(
                StompCommand.CONNECT,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Basic credentials"
        );

        assertThatThrownBy(() -> interceptor.preSend(missing, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> interceptor.preSend(wrongScheme, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(jwtProvider, userDetailsService, redisTemplate,
                sessionRevocationService);
    }

    @Test
    void connectRejectsARefreshToken() {
        when(jwtProvider.isAccessToken("refresh-token")).thenReturn(false);
        Message<byte[]> connect = stomp(
                StompCommand.CONNECT,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Bearer refresh-token"
        );

        assertThatThrownBy(() -> interceptor.preSend(connect, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(jwtProvider, never()).extractUsername("refresh-token");
        verifyNoInteractions(userDetailsService, redisTemplate, sessionRevocationService);
    }

    @Test
    void connectRejectsABlacklistedAccessToken() {
        when(jwtProvider.isAccessToken(TOKEN)).thenReturn(true);
        when(jwtProvider.extractJwtId(TOKEN)).thenReturn("revoked-jti");
        when(redisTemplate.hasKey("blacklist:jti:revoked-jti")).thenReturn(true);
        Message<byte[]> connect = stomp(
                StompCommand.CONNECT,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN
        );

        assertThatThrownBy(() -> interceptor.preSend(connect, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(jwtProvider, never()).extractUsername(TOKEN);
        verifyNoInteractions(userDetailsService, sessionRevocationService);
    }

    @Test
    void sendRevalidatesTheConnectionTokenAndAllowsOnlyTheChatApplicationRoute() {
        User user = activeUser();
        stubValidToken(user);
        Message<byte[]> send = stomp(
                StompCommand.SEND,
                "/app/chat.send",
                connectedPrincipal(user),
                null,
                null
        );

        Message<?> result = interceptor.preSend(send, channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                result,
                StompHeaderAccessor.class
        );
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(EMAIL);
        verify(jwtProvider).isTokenValid(TOKEN, user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/queue/messages", "/user/queue/chat-errors"})
    void subscribeRevalidatesTheConnectionTokenForEachAllowedUserQueue(String destination) {
        User user = activeUser();
        stubValidToken(user);
        Message<byte[]> subscribe = stomp(
                StompCommand.SUBSCRIBE,
                destination,
                connectedPrincipal(user),
                null,
                null
        );

        Message<?> result = interceptor.preSend(subscribe, channel);

        assertThat(result).isSameAs(subscribe);
        verify(jwtProvider).isTokenValid(TOKEN, user);
    }

    @Test
    void sendAndSubscribeRejectDestinationsOutsideTheAllowlist() {
        User user = activeUser();
        stubValidToken(user);
        Message<byte[]> forbiddenSend = stomp(
                StompCommand.SEND,
                "/topic/broadcast",
                connectedPrincipal(user),
                null,
                null
        );
        Message<byte[]> forbiddenSubscribe = stomp(
                StompCommand.SUBSCRIBE,
                "/queue/messages",
                connectedPrincipal(user),
                null,
                null
        );

        assertThatThrownBy(() -> interceptor.preSend(forbiddenSend, channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(forbiddenSubscribe, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAuthenticatedConnectionIsRejectedAfterItsSessionIsRevoked() {
        User user = activeUser();
        when(jwtProvider.isAccessToken(TOKEN)).thenReturn(true);
        when(jwtProvider.extractJwtId(TOKEN)).thenReturn("active-jti");
        when(redisTemplate.hasKey("blacklist:jti:active-jti")).thenReturn(false);
        when(jwtProvider.extractUsername(TOKEN)).thenReturn(EMAIL);
        when(jwtProvider.extractSessionVersion(TOKEN)).thenReturn(7L);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(user);
        when(sessionRevocationService.isSessionActive(user, 7L)).thenReturn(false);
        Message<byte[]> send = stomp(
                StompCommand.SEND,
                "/app/chat.send",
                connectedPrincipal(user),
                null,
                null
        );

        assertThatThrownBy(() -> interceptor.preSend(send, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(jwtProvider, never()).isTokenValid(TOKEN, user);
    }

    @Test
    void sendWithoutTheAuthenticatedConnectionPrincipalIsRejected() {
        Message<byte[]> send = stomp(
                StompCommand.SEND,
                "/app/chat.send",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> interceptor.preSend(send, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(jwtProvider, userDetailsService, redisTemplate,
                sessionRevocationService);
    }

    @Test
    void outboundMessageIsDeliveredWhileTheRememberedSessionTokenRemainsValid() {
        User user = activeUser();
        String sessionId = "session-active";
        stubValidToken(user);
        Message<byte[]> connect = stomp(
                StompCommand.CONNECT,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN,
                sessionId
        );
        Message<byte[]> outbound = stomp(
                StompCommand.MESSAGE,
                "/queue/messages-user123",
                null,
                null,
                null,
                sessionId
        );
        interceptor.preSend(connect, channel);

        Message<?> result = interceptor.preSend(outbound, channel);

        assertThat(result).isSameAs(outbound);
    }

    @Test
    void outboundMessageIsDroppedWhenTheSessionIsRevokedAfterConnect() {
        User user = activeUser();
        String sessionId = "session-revoked";
        stubValidToken(user);
        when(sessionRevocationService.isSessionActive(user, 7L)).thenReturn(true, false);
        Message<byte[]> connect = stomp(
                StompCommand.CONNECT,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN,
                sessionId
        );
        Message<byte[]> outbound = stomp(
                StompCommand.MESSAGE,
                "/queue/messages-user123",
                null,
                null,
                null,
                sessionId
        );
        interceptor.preSend(connect, channel);

        assertThat(interceptor.preSend(outbound, channel)).isNull();
        // The failed validation also forgets the session, so subsequent frames stay dropped.
        assertThat(interceptor.preSend(outbound, channel)).isNull();
    }

    @Test
    void outboundMessageIsDroppedWhenTheAccessTokenExpiresAfterConnect() {
        User user = activeUser();
        String sessionId = "session-expired";
        stubValidToken(user);
        when(jwtProvider.isTokenValid(TOKEN, user)).thenReturn(true, false);
        Message<byte[]> connect = stomp(
                StompCommand.CONNECT,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN,
                sessionId
        );
        Message<byte[]> outbound = stomp(
                StompCommand.MESSAGE,
                "/queue/messages-user123",
                null,
                null,
                null,
                sessionId
        );
        interceptor.preSend(connect, channel);

        assertThat(interceptor.preSend(outbound, channel)).isNull();
    }

    @Test
    void disconnectFrameForgetsTheRememberedSession() {
        User user = activeUser();
        String sessionId = "session-disconnected";
        stubValidToken(user);
        Message<byte[]> connect = stomp(
                StompCommand.CONNECT,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN,
                sessionId
        );
        Message<byte[]> disconnect = stomp(
                StompCommand.DISCONNECT,
                null,
                null,
                null,
                null,
                sessionId
        );
        Message<byte[]> outbound = stomp(
                StompCommand.MESSAGE,
                "/queue/messages-user123",
                null,
                null,
                null,
                sessionId
        );
        interceptor.preSend(connect, channel);

        interceptor.preSend(disconnect, channel);

        assertThat(interceptor.preSend(outbound, channel)).isNull();
    }

    @Test
    void sessionDisconnectEventAlsoForgetsTheRememberedSession() {
        User user = activeUser();
        String sessionId = "session-event-disconnected";
        stubValidToken(user);
        Message<byte[]> connect = stomp(
                StompCommand.CONNECT,
                null,
                null,
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN,
                sessionId
        );
        Message<byte[]> outbound = stomp(
                StompCommand.MESSAGE,
                "/queue/messages-user123",
                null,
                null,
                null,
                sessionId
        );
        interceptor.preSend(connect, channel);

        interceptor.handleSessionDisconnect(new SessionDisconnectEvent(
                this,
                connect,
                sessionId,
                CloseStatus.NORMAL
        ));

        assertThat(interceptor.preSend(outbound, channel)).isNull();
    }

    private void stubValidToken(User user) {
        when(jwtProvider.isAccessToken(TOKEN)).thenReturn(true);
        when(jwtProvider.extractJwtId(TOKEN)).thenReturn("active-jti");
        when(redisTemplate.hasKey("blacklist:jti:active-jti")).thenReturn(false);
        when(jwtProvider.extractUsername(TOKEN)).thenReturn(EMAIL);
        when(jwtProvider.extractSessionVersion(TOKEN)).thenReturn(7L);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(user);
        when(sessionRevocationService.isSessionActive(user, 7L)).thenReturn(true);
        when(jwtProvider.isTokenValid(TOKEN, user)).thenReturn(true);
    }

    private Principal connectedPrincipal(User user) {
        return new UsernamePasswordAuthenticationToken(user, TOKEN, user.getAuthorities());
    }

    private User activeUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .fullName("Chat User")
                .pinCode("RML-123456")
                .status(AccountStatus.ACTIVE)
                .sessionVersion(7L)
                .build();
    }

    private Message<byte[]> stomp(
            StompCommand command,
            String destination,
            Principal principal,
            String authorizationHeader,
            String authorizationValue
    ) {
        return stomp(
                command,
                destination,
                principal,
                authorizationHeader,
                authorizationValue,
                null
        );
    }

    private Message<byte[]> stomp(
            StompCommand command,
            String destination,
            Principal principal,
            String authorizationHeader,
            String authorizationValue,
            String sessionId
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }
        if (principal != null) {
            accessor.setUser(principal);
        }
        if (authorizationHeader != null) {
            accessor.setNativeHeader(authorizationHeader, authorizationValue);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
