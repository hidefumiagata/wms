package com.wms.system.controller;

import com.wms.shared.test.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AUTH: パスワード変更結合テスト")
class AuthPasswordChangeIntegrationTest extends IntegrationTestBase {

    // BCrypt(12) hashes
    private static final String HASH_CURRENT = "$2a$12$0Jyy9aA3U1NJuTk0UM9NIuQTqfl4NfBdOu5wBMDK71CTGSYtRyPzm"; // Current@1234
    private static final String HASH_INITIAL = "$2a$12$0Y7fIyoybKcvnJg0rHJmoefnGSvVUQs0GmFBsv0Jt0bcOESrTEziK"; // Initial@1234
    private static final String HASH_MANAGER = "$2a$12$TntL0dGRVd7PsnUU3saqAeYibge4ojQEyylSmUade3Bmfb9LbTECy"; // Manager@1234

    @BeforeEach
    void resetTestUsers() {
        // Reset pw_change_user
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, password_change_required = false, "
                        + "failed_login_count = 0, locked = false, locked_at = NULL "
                        + "WHERE user_code = 'pw_change_user'",
                HASH_CURRENT);

        // Reset new_user
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, password_change_required = true, "
                        + "failed_login_count = 0, locked = false, locked_at = NULL "
                        + "WHERE user_code = 'new_user'",
                HASH_INITIAL);

        // Reset pw_lock_user
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, password_change_required = false, "
                        + "failed_login_count = 0, locked = false, locked_at = NULL "
                        + "WHERE user_code = 'pw_lock_user'",
                HASH_MANAGER);
    }

    // ----- SC-010: Initial password change required -----
    @Test
    @DisplayName("SC-010: 初回ログイン時パスワード変更 → 204")
    void changePassword_initialLogin_success() {
        HttpHeaders headers = loginAndGetHeaders("new_user", "Initial@1234");

        String body = "{\"currentPassword\":\"Initial@1234\",\"newPassword\":\"NewSecure@9999\"}";
        ResponseEntity<String> response = postJson("/api/v1/auth/change-password", body, headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify password_change_required is now false
        Boolean required = jdbcTemplate.queryForObject(
                "SELECT password_change_required FROM users WHERE user_code = 'new_user'",
                Boolean.class);
        assertThat(required).isFalse();
    }

    // ----- SC-011: Change password, then login with new password -----
    @Test
    @DisplayName("SC-011: パスワード変更後、新パスワードでログイン成功")
    void changePassword_thenLoginWithNewPassword() {
        HttpHeaders headers = loginAndGetHeaders("pw_change_user", "Current@1234");

        String body = "{\"currentPassword\":\"Current@1234\",\"newPassword\":\"Changed@5678\"}";
        ResponseEntity<String> response = postJson("/api/v1/auth/change-password", body, headers);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Login with new password
        ResponseEntity<String> loginResponse = loginRaw("pw_change_user", "Changed@5678");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ----- SC-012: Wrong current password → 401 -----
    @Test
    @DisplayName("SC-012: 現在のパスワード誤り → 401")
    void changePassword_wrongCurrentPassword_returns401() {
        HttpHeaders headers = loginAndGetHeaders("pw_change_user", "Current@1234");

        String body = "{\"currentPassword\":\"WrongPassword\",\"newPassword\":\"NewSecure@9999\"}";
        ResponseEntity<String> response = postJson("/api/v1/auth/change-password", body, headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----- SC-013: Empty new password → 400 -----
    @Test
    @DisplayName("SC-013: 空の新パスワード → 400")
    void changePassword_emptyNewPassword_returns400() {
        HttpHeaders headers = loginAndGetHeaders("pw_change_user", "Current@1234");

        String body = "{\"currentPassword\":\"Current@1234\",\"newPassword\":\"\"}";
        ResponseEntity<String> response = postJson("/api/v1/auth/change-password", body, headers);

        // OpenAPI validation: minLength=1 → 400
        assertThat(response.getStatusCode().value()).isIn(400, 422);
    }

    // ----- SC-014: Same password → 409 -----
    @Test
    @DisplayName("SC-014: 同一パスワード → 409")
    void changePassword_samePassword_returns409() {
        HttpHeaders headers = loginAndGetHeaders("pw_change_user", "Current@1234");

        String body = "{\"currentPassword\":\"Current@1234\",\"newPassword\":\"Current@1234\"}";
        ResponseEntity<String> response = postJson("/api/v1/auth/change-password", body, headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ----- SC-022: 5x wrong current password in change-password → locked -----
    @Test
    @DisplayName("SC-022: パスワード変更で5回誤り → アカウントロック")
    void changePassword_5WrongCurrentPasswords_locksAccount() {
        HttpHeaders headers = loginAndGetHeaders("pw_lock_user", "Manager@1234");

        for (int i = 0; i < 5; i++) {
            String body = "{\"currentPassword\":\"Wrong@" + i + "\",\"newPassword\":\"NewSecure@9999\"}";
            postJson("/api/v1/auth/change-password", body, headers);
        }

        // Verify account is locked
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT locked FROM users WHERE user_code = 'pw_lock_user'", Boolean.class);
        assertThat(locked).isTrue();

        // Verify login is now rejected
        ResponseEntity<String> loginResponse = loginRaw("pw_lock_user", "Manager@1234");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
