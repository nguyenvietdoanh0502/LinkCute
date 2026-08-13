package com.hadilao.be.modules.auth.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRequestSecurityPolicyTest {

    @Test
    void rejectsMissingRequestedWithHeader() {
        AuthRequestSecurityPolicy policy = new AuthRequestSecurityPolicy(List.of());

        assertThatThrownBy(() -> policy.validate(request(null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void acceptsSameOriginWhenCorsAllowListIsEmpty() {
        AuthRequestSecurityPolicy policy = new AuthRequestSecurityPolicy(List.of());

        assertThatCode(() -> policy.validate(request("XMLHttpRequest", "https://api.example.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsConfiguredCrossOrigin() {
        AuthRequestSecurityPolicy policy =
                new AuthRequestSecurityPolicy(List.of("https://app.example.com"));

        assertThatCode(() -> policy.validate(request("XMLHttpRequest", "https://app.example.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsHostileCrossOrigin() {
        AuthRequestSecurityPolicy policy =
                new AuthRequestSecurityPolicy(List.of("https://app.example.com"));

        assertThatThrownBy(() -> policy.validate(request("XMLHttpRequest", "https://evil.example")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    private MockHttpServletRequest request(String requestedWith, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setScheme("https");
        request.setServerName("api.example.com");
        request.setServerPort(443);
        if (requestedWith != null) {
            request.addHeader(AuthRequestSecurityPolicy.X_REQUESTED_WITH, requestedWith);
        }
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
