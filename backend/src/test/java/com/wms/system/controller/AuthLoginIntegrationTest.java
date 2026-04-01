package com.wms.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.shared.test.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AUTH: ログイン結合テスト")
class AuthLoginIntegrationTest extends IntegrationTestBase {

    // ----- SC-001: SYSTEM_ADMIN login -----
    @Test
    @Order(1)
    @DisplayName("SC-001: SYSTEM_ADMIN ログイン成功")
    void login_systemAdmin_success() throws Exception {
        ResponseEntity<String> response = loginRaw("admin001", "Admin@1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = parseJson(response.getBody());
        assertThat(body.get("role").asText()).isEqualTo("SYSTEM_ADMIN");
        assertThat(body.get("userCode").asText()).isEqualTo("admin001");

        // Cookies set
        assertThat(extractCookie(response, "access_token")).isNotNull();
        assertThat(extractCookie(response, "refresh_token")).isNotNull();
    }

    // ----- SC-002: WAREHOUSE_MANAGER login -----
    @Test
    @Order(2)
    @DisplayName("SC-002: WAREHOUSE_MANAGER ログイン成功")
    void login_warehouseManager_success() throws Exception {
        ResponseEntity<String> response = loginRaw("wh_manager01", "Manager@1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = parseJson(response.getBody());
        assertThat(body.get("role").asText()).isEqualTo("WAREHOUSE_MANAGER");
    }

    // ----- SC-003: WAREHOUSE_STAFF login -----
    @Test
    @Order(3)
    @DisplayName("SC-003: WAREHOUSE_STAFF ログイン成功")
    void login_warehouseStaff_success() throws Exception {
        ResponseEntity<String> response = loginRaw("wh_staff01", "Staff@1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = parseJson(response.getBody());
        assertThat(body.get("role").asText()).isEqualTo("WAREHOUSE_STAFF");
    }

    // ----- SC-004: VIEWER login (uses R__01 test user viewer01 with Test@1234) -----
    @Test
    @Order(4)
    @DisplayName("SC-004: VIEWER ログイン成功")
    void login_viewer_success() throws Exception {
        ResponseEntity<String> response = loginRaw("viewer01", "Test@1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = parseJson(response.getBody());
        assertThat(body.get("role").asText()).isEqualTo("VIEWER");
    }

    // ----- SC-005: Wrong password → 401, failed_login_count incremented -----
    @Test
    @Order(5)
    @DisplayName("SC-005: パスワード誤り → 401, failed_login_count増加")
    void login_wrongPassword_returns401() {
        // Reset failed count first
        jdbcTemplate.update(
                "UPDATE users SET failed_login_count = 0, locked = false, locked_at = NULL WHERE user_code = 'admin001'");

        ResponseEntity<String> response = loginRaw("admin001", "WrongPassword");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT failed_login_count FROM users WHERE user_code = 'admin001'", Integer.class);
        assertThat(failedCount).isEqualTo(1);

        // Reset for other tests
        jdbcTemplate.update(
                "UPDATE users SET failed_login_count = 0, locked = false, locked_at = NULL WHERE user_code = 'admin001'");
    }

    // ----- SC-006: Non-existent user → 401 -----
    @Test
    @Order(6)
    @DisplayName("SC-006: 存在しないユーザー → 401")
    void login_nonExistentUser_returns401() {
        ResponseEntity<String> response = loginRaw("nonexistent_user", "anything");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----- SC-007: Locked user → 401 -----
    @Test
    @Order(7)
    @DisplayName("SC-007: ロック済みユーザー → 401")
    void login_lockedUser_returns401() {
        // Ensure user is locked
        jdbcTemplate.update(
                "UPDATE users SET locked = true, locked_at = now(), failed_login_count = 5 WHERE user_code = 'locked_user'");

        ResponseEntity<String> response = loginRaw("locked_user", "Correct@1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----- SC-008: Inactive user → 401 -----
    @Test
    @Order(8)
    @DisplayName("SC-008: 無効ユーザー → 401")
    void login_inactiveUser_returns401() {
        ResponseEntity<String> response = loginRaw("inactive_user", "Correct@1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----- SC-009: 5 wrong logins → locked, then admin unlocks → login succeeds -----
    @Test
    @Order(9)
    @DisplayName("SC-009: 5回失敗→ロック、管理者がアンロック後ログイン成功")
    void login_lockAfter5Failures_thenAdminUnlock() throws Exception {
        // Reset lock_target state
        jdbcTemplate.update(
                "UPDATE users SET failed_login_count = 0, locked = false, locked_at = NULL WHERE user_code = 'lock_target'");

        // 5 wrong logins
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = loginRaw("lock_target", "WrongPassword");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // Verify locked
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT locked FROM users WHERE user_code = 'lock_target'", Boolean.class);
        assertThat(locked).isTrue();

        // Login as admin and unlock
        HttpHeaders adminHeaders = loginAndGetHeaders("admin001", "Admin@1234");

        Long lockTargetId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'lock_target'", Long.class);

        ResponseEntity<String> unlockResponse = postNoBody(
                "/api/v1/master/users/" + lockTargetId + "/unlock", adminHeaders);
        assertThat(unlockResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify lock_target can login now
        ResponseEntity<String> loginResponse = loginRaw("lock_target", "Target@1234");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ----- CSRF header missing → 403 -----
    @Test
    @Order(10)
    @DisplayName("X-Requested-Withヘッダーなし → 403")
    void login_missingCsrfHeader_returns403() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        // intentionally no X-Requested-With

        String body = "{\"userCode\":\"admin001\",\"password\":\"Admin@1234\"}";
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
