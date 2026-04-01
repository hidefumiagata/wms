package com.wms.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.shared.test.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AUTH: パスワードリセット結合テスト")
class AuthPasswordResetIntegrationTest extends IntegrationTestBase {

    private static final String HASH_RESET = "$2a$12$5JtcSjImM9V1tzkXK0luoOPx/eivHz7/YtPNxwYsrhzC7o890ineG"; // Reset@1234

    @BeforeEach
    void resetTestUsers() {
        // Reset reset_user state
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, password_change_required = false, "
                        + "failed_login_count = 0, locked = false, locked_at = NULL "
                        + "WHERE user_code = 'reset_user'",
                HASH_RESET);
        // Clean up any existing reset tokens for reset_user
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'reset_user'", Long.class);
        jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
    }

    // ----- SC-015: Request password reset, then confirm with known token -----
    @Test
    @DisplayName("SC-015: パスワードリセット要求 → トークン挿入 → 確認 → 成功")
    void passwordReset_requestAndConfirm_success() throws Exception {
        HttpHeaders headers = createJsonHeaders();

        // Request reset
        String requestBody = "{\"identifier\":\"reset_user\"}";
        ResponseEntity<String> requestResponse = postJson(
                "/api/v1/auth/password-reset/request", requestBody, headers);
        assertThat(requestResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify token was created in DB
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'reset_user'", Long.class);
        Integer tokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?",
                Integer.class, userId);
        assertThat(tokenCount).isGreaterThan(0);

        // Delete the auto-generated token and insert our own known token
        jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);
        jdbcTemplate.update(
                "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, created_at) "
                        + "VALUES (?, ?, ?, now())",
                userId, tokenHash, OffsetDateTime.now().plusMinutes(30));

        // Confirm reset
        String confirmBody = String.format(
                "{\"token\":\"%s\",\"newPassword\":\"ResetNew@5678\"}", rawToken);
        ResponseEntity<String> confirmResponse = postJson(
                "/api/v1/auth/password-reset/confirm", confirmBody, headers);
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = parseJson(confirmResponse.getBody());
        assertThat(body.get("message").asText()).contains("successfully");

        // Verify login with new password works
        ResponseEntity<String> loginResponse = loginRaw("reset_user", "ResetNew@5678");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ----- SC-016: Expired token → error -----
    @Test
    @DisplayName("SC-016: 期限切れトークン → エラー")
    void passwordReset_expiredToken_returnsError() {
        HttpHeaders headers = createJsonHeaders();

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'reset_user'", Long.class);

        // Insert expired token
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);
        jdbcTemplate.update(
                "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, created_at) "
                        + "VALUES (?, ?, ?, now())",
                userId, tokenHash, OffsetDateTime.now().minusMinutes(10));

        // Try to confirm with expired token
        String confirmBody = String.format(
                "{\"token\":\"%s\",\"newPassword\":\"NewPass@1234\"}", rawToken);
        ResponseEntity<String> response = postJson(
                "/api/v1/auth/password-reset/confirm", confirmBody, headers);

        assertThat(response.getStatusCode().value()).isIn(400, 422);
    }

    // ----- SC-017: Fake token → error -----
    @Test
    @DisplayName("SC-017: 不正トークン → エラー")
    void passwordReset_fakeToken_returnsError() {
        HttpHeaders headers = createJsonHeaders();

        String confirmBody = "{\"token\":\"fake-token-does-not-exist\",\"newPassword\":\"NewPass@1234\"}";
        ResponseEntity<String> response = postJson(
                "/api/v1/auth/password-reset/confirm", confirmBody, headers);

        assertThat(response.getStatusCode().value()).isIn(400, 422);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
