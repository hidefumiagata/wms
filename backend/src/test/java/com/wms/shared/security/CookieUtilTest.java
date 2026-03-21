package com.wms.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

    private static final long ACCESS_TOKEN_EXPIRATION = 900_000L;   // 15 minutes in ms
    private static final long REFRESH_TOKEN_EXPIRATION = 604_800_000L; // 7 days in ms
    private static final boolean SECURE = true;

    private CookieUtil cookieUtil;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        cookieUtil = new CookieUtil(ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION, SECURE);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("addAccessTokenCookie: 正しいCookie属性が設定される")
    void addAccessTokenCookie_default_setsCorrectAttributes() {
        // Act
        cookieUtil.addAccessTokenCookie(response, "test-access-token");

        // Assert
        String setCookieHeader = response.getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("access_token=test-access-token");
        assertThat(setCookieHeader).contains("HttpOnly");
        assertThat(setCookieHeader).contains("Secure");
        assertThat(setCookieHeader).contains("SameSite=Lax");
        assertThat(setCookieHeader).contains("Path=/");
        assertThat(setCookieHeader).contains("Max-Age=" + (ACCESS_TOKEN_EXPIRATION / 1000));
    }

    @Test
    @DisplayName("addRefreshTokenCookie: 正しいパスが設定される")
    void addRefreshTokenCookie_default_setsCorrectPath() {
        // Act
        cookieUtil.addRefreshTokenCookie(response, "test-refresh-token");

        // Assert
        String setCookieHeader = response.getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("refresh_token=test-refresh-token");
        assertThat(setCookieHeader).contains("Path=/api/v1/auth/refresh");
        assertThat(setCookieHeader).contains("HttpOnly");
        assertThat(setCookieHeader).contains("Secure");
        assertThat(setCookieHeader).contains("SameSite=Lax");
        assertThat(setCookieHeader).contains("Max-Age=" + (REFRESH_TOKEN_EXPIRATION / 1000));
    }

    @Test
    @DisplayName("clearAuthCookies: Max-Age=0でCookieが削除される")
    void clearAuthCookies_default_setsMaxAgeToZero() {
        // Act
        cookieUtil.clearAuthCookies(response);

        // Assert
        List<String> setCookieHeaders = response.getHeaders("Set-Cookie");
        assertThat(setCookieHeaders).hasSize(2);

        String accessCookieHeader = setCookieHeaders.stream()
                .filter(h -> h.contains("access_token"))
                .findFirst()
                .orElse(null);
        String refreshCookieHeader = setCookieHeaders.stream()
                .filter(h -> h.contains("refresh_token"))
                .findFirst()
                .orElse(null);

        assertThat(accessCookieHeader).isNotNull();
        assertThat(accessCookieHeader).contains("Max-Age=0");
        assertThat(accessCookieHeader).contains("Path=/");

        assertThat(refreshCookieHeader).isNotNull();
        assertThat(refreshCookieHeader).contains("Max-Age=0");
        assertThat(refreshCookieHeader).contains("Path=/api/v1/auth/refresh");
    }

    @Test
    @DisplayName("secure=falseの場合、Secure属性が含まれない")
    void addAccessTokenCookie_secureIsFalse_doesNotSetSecureAttribute() {
        // Arrange
        CookieUtil insecureCookieUtil = new CookieUtil(ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION, false);
        MockHttpServletResponse insecureResponse = new MockHttpServletResponse();

        // Act
        insecureCookieUtil.addAccessTokenCookie(insecureResponse, "token");

        // Assert
        String setCookieHeader = insecureResponse.getHeader("Set-Cookie");
        assertThat(setCookieHeader).doesNotContain("Secure");
    }
}
