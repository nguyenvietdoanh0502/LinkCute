package com.hadilao.be.core.security;

import com.hadilao.be.modules.user.entity.User;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_PREFIX = "blacklist:jti:";
    private static final Set<String> ALLOWED_SEND_DESTINATIONS = Set.of(
            "/app/chat.send",
            "/app/call.signal"
    );
    private static final Set<String> ALLOWED_SUBSCRIBE_DESTINATIONS = Set.of(
            "/user/queue/messages",
            "/user/queue/chat-errors",
            "/user/queue/call-signals",
            "/user/queue/call-errors"
    );

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final SessionRevocationService sessionRevocationService;
    private final ConcurrentMap<String, Authentication> sessions = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor simpAccessor = MessageHeaderAccessor.getAccessor(
                message,
                SimpMessageHeaderAccessor.class
        );
        if (simpAccessor == null) {
            return message;
        }

        StompHeaderAccessor accessor = simpAccessor instanceof StompHeaderAccessor stompAccessor
                ? stompAccessor
                : null;
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command == null) {
            if (SimpMessageType.MESSAGE.equals(simpAccessor.getMessageType())
                    && simpAccessor.getSessionId() != null) {
                return isOutboundSessionActive(simpAccessor.getSessionId()) ? message : null;
            }
            return message;
        }

        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            Authentication authentication = authenticate(extractBearerToken(accessor));
            accessor.setUser(authentication);
            rememberSession(accessor.getSessionId(), authentication);
            return message;
        }

        if (StompCommand.MESSAGE.equals(command)) {
            return isOutboundSessionActive(accessor.getSessionId()) ? message : null;
        }

        if (StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command)) {
            Authentication authentication = revalidate(accessor.getUser());
            accessor.setUser(authentication);
            rememberSession(accessor.getSessionId(), authentication);
            authorizeDestination(command, accessor.getDestination());
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            forgetSession(accessor.getSessionId());
        }

        return message;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        forgetSession(event.getSessionId());
    }

    private boolean isOutboundSessionActive(String sessionId) {
        Authentication authentication = sessionId == null ? null : sessions.get(sessionId);
        try {
            Authentication refreshed = revalidate(authentication);
            rememberSession(sessionId, refreshed);
            return true;
        } catch (AuthenticationCredentialsNotFoundException exception) {
            forgetSession(sessionId);
            return false;
        } catch (RuntimeException exception) {
            // Fail closed for this frame, but keep the session token so a transient
            // Redis/database outage does not permanently brick an otherwise valid socket.
            return false;
        }
    }

    private void rememberSession(String sessionId, Authentication authentication) {
        if (sessionId != null && authentication != null) {
            sessions.put(sessionId, authentication);
        }
    }

    private void forgetSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private Authentication revalidate(java.security.Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getCredentials() instanceof String token)
                || token.isBlank()) {
            throw unauthenticated();
        }
        return authenticate(token);
    }

    private Authentication authenticate(String token) {
        try {
            if (token == null || !jwtProvider.isAccessToken(token)) {
                throw unauthenticated();
            }

            String jwtId = jwtProvider.extractJwtId(token);
            if (jwtId != null && Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jwtId))) {
                throw unauthenticated();
            }

            String username = jwtProvider.extractUsername(token);
            Long sessionVersion = jwtProvider.extractSessionVersion(token);
            if (username == null) {
                throw unauthenticated();
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!(userDetails instanceof User user)
                    || !sessionRevocationService.isSessionActive(user, sessionVersion)
                    || !jwtProvider.isTokenValid(token, userDetails)) {
                throw unauthenticated();
            }

            return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    token,
                    userDetails.getAuthorities()
            );
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException exception) {
            throw unauthenticated();
        }
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION.toLowerCase());
        }
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw unauthenticated();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw unauthenticated();
        }
        return token;
    }

    private void authorizeDestination(StompCommand command, String destination) {
        if (StompCommand.SEND.equals(command) && !ALLOWED_SEND_DESTINATIONS.contains(destination)) {
            throw new AccessDeniedException("STOMP destination is not allowed");
        }
        if (StompCommand.SUBSCRIBE.equals(command) && !ALLOWED_SUBSCRIBE_DESTINATIONS.contains(destination)) {
            throw new AccessDeniedException("STOMP destination is not allowed");
        }
    }

    private AuthenticationCredentialsNotFoundException unauthenticated() {
        return new AuthenticationCredentialsNotFoundException("WebSocket authentication failed");
    }
}
