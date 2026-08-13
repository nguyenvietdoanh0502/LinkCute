package com.hadilao.be.modules.auth.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuthRequestSecurityPolicy {

    public static final String X_REQUESTED_WITH = "X-Requested-With";
    private static final String XML_HTTP_REQUEST = "XMLHttpRequest";

    private final Set<String> allowedOrigins;

    public AuthRequestSecurityPolicy(
            @Value("${roamly.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validate(HttpServletRequest request) {
        if (!XML_HTTP_REQUEST.equals(request.getHeader(X_REQUESTED_WITH))) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (CorsUtils.isCorsRequest(request) && !allowedOrigins.contains(origin)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
