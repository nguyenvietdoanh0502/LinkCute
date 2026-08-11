package com.hadilao.be.core.config;

import com.hadilao.be.core.security.JwtStompChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final int MAX_MESSAGE_BYTES = 16 * 1024;

    private final JwtStompChannelInterceptor jwtStompChannelInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            JwtStompChannelInterceptor jwtStompChannelInterceptor,
            @Value("${roamly.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.jwtStompChannelInterceptor = jwtStompChannelInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint("/ws");
        String[] origins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        if (origins.length > 0) {
            endpoint.setAllowedOrigins(origins);
        }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(MAX_MESSAGE_BYTES)
                .setSendBufferSizeLimit(MAX_MESSAGE_BYTES * 4)
                .setSendTimeLimit(15_000)
                .setTimeToFirstMessage(10_000);
    }
}
