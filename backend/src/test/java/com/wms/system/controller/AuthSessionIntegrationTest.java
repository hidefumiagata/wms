package com.wms.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.shared.test.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AUTH: セッション管理結合テスト")
@TestPropertySource(properties = {
        "jwt.access-token-expiration=2000"
})
class AuthSessionIntegrationTest extends IntegrationTestBase {

    // ----- SC-019: Logout → 204, cookies cleared -----
    @Test
    @DisplayName("SC-019: ログアウト → 204, Cookieクリア")
    void logout_success() {
        // Login
        ResponseEntity<String> loginResponse = loginRaw("admin001", "Admin@1234");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String cookies = buildCookieHeader(loginResponse);
        assertThat(cookies).isNotNull();

        // Logout
        HttpHeaders logoutHeaders = createJsonHeaders();
        logoutHeaders.set("Cookie", cookies);

        ResponseEntity<String> logoutResponse = postNoBody("/api/v1/auth/logout", logoutHeaders);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify cookies are cleared (Max-Age=0)
        var setCookies = logoutResponse.getHeaders().get("Set-Cookie");
        assertThat(setCookies).isNotNull();
        boolean accessCleared = setCookies.stream()
                .anyMatch(c -> c.startsWith("access_token=") && c.contains("Max-Age=0"));
        boolean refreshCleared = setCookies.stream()
                .anyMatch(c -> c.startsWith("refresh_token=") && c.contains("Max-Age=0"));
        assertThat(accessCleared).isTrue();
        assertThat(refreshCleared).isTrue();
    }

    // ----- SC-020: Access token expired → refresh → 200, new cookies -----
    @Test
    @DisplayName("SC-020: アクセストークン期限切れ → リフレッシュ → 200")
    void refresh_afterAccessTokenExpired_success() throws Exception {
        // Login (access token expires in 2 seconds)
        ResponseEntity<String> loginResponse = loginRaw("wh_manager01", "Manager@1234");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String cookies = buildCookieHeader(loginResponse);
        assertThat(cookies).isNotNull();

        // Wait for access token to expire
        Thread.sleep(3000);

        // Refresh
        HttpHeaders refreshHeaders = createJsonHeaders();
        refreshHeaders.set("Cookie", cookies);

        ResponseEntity<String> refreshResponse = postNoBody("/api/v1/auth/refresh", refreshHeaders);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify new cookies are set
        assertThat(extractCookie(refreshResponse, "access_token")).isNotNull();
        assertThat(extractCookie(refreshResponse, "refresh_token")).isNotNull();

        // Verify response body
        JsonNode body = parseJson(refreshResponse.getBody());
        assertThat(body.get("userCode").asText()).isEqualTo("wh_manager01");
        assertThat(body.get("role").asText()).isEqualTo("WAREHOUSE_MANAGER");
    }

    // ----- SC-021: Expired refresh token in DB → 401 -----
    @Test
    @DisplayName("SC-021: DBのリフレッシュトークン期限切れ → 401")
    void refresh_expiredRefreshTokenInDb_returns401() throws Exception {
        // Login
        ResponseEntity<String> loginResponse = loginRaw("wh_staff01", "Staff@1234");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String cookies = buildCookieHeader(loginResponse);
        assertThat(cookies).isNotNull();

        // Expire the refresh token in DB
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'wh_staff01'", Long.class);
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = ? WHERE user_id = ?",
                OffsetDateTime.now().minusMinutes(10), userId);

        // Wait for access token to expire so refresh is needed
        Thread.sleep(3000);

        // Try to refresh
        HttpHeaders refreshHeaders = createJsonHeaders();
        refreshHeaders.set("Cookie", cookies);

        ResponseEntity<String> refreshResponse = postNoBody("/api/v1/auth/refresh", refreshHeaders);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
