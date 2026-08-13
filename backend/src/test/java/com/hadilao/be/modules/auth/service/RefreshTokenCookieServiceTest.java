package com.hadilao.be.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenCookieServiceTest {

    @Test
    void writesSecureHttpOnlyHostOnlyCookieUsingConfiguredExpiry() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(2_592_000_000L, "None");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.write(response, "secret.refresh.token");

        String header = response.getHeader("Set-Cookie");
        assertThat(header)
                .contains("__Host-linkcute_refresh=secret.refresh.token")
                .contains("Path=/")
                .contains("Max-Age=2592000")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=None")
                .doesNotContain("Domain=");
    }

    @Test
    void clearsCookieWithMatchingSecurityAttributes() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(60_000L, "Lax");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("__Host-linkcute_refresh=;")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("Domain=");
    }

    @Test
    void rejectsUnsupportedSameSiteValue() {
        assertThatThrownBy(() -> new RefreshTokenCookieService(60_000L, "anything"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
