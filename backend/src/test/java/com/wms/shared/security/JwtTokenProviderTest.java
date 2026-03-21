package com.wms.shared.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";
    private static final long ACCESS_TOKEN_EXPIRATION = 900_000L; // 15 min

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, ACCESS_TOKEN_EXPIRATION);
    }

    @Test
    void generateAccessToken_validInput_returnsTokenWithCorrectClaims() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin001", "SYSTEM_ADMIN", false);

        assertThat(token).isNotBlank();

        Claims claims = jwtTokenProvider.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("userCode", String.class)).isEqualTo("admin001");
        assertThat(claims.get("role", String.class)).isEqualTo("SYSTEM_ADMIN");
        assertThat(claims.get("passwordChangeRequired", Boolean.class)).isFalse();
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin001", "SYSTEM_ADMIN", false);
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void validateToken_null_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
    }

    @Test
    void parseTokenAllowExpired_expiredToken_returnsClaims() {
        // 有効期限1msでトークン生成 → 即期限切れ
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 1L);
        String token = shortLived.generateAccessToken(1L, "admin001", "SYSTEM_ADMIN", false);

        // 少し待つ
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(shortLived.validateToken(token)).isFalse();

        // 期限切れでもClaims取得可能
        Claims claims = shortLived.parseTokenAllowExpired(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("userCode", String.class)).isEqualTo("admin001");
    }

    @Test
    void parseTokenAllowExpired_validToken_returnsClaims() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin001", "SYSTEM_ADMIN", false);
        Claims claims = jwtTokenProvider.parseTokenAllowExpired(token);
        assertThat(claims.getSubject()).isEqualTo("1");
    }

    @Test
    void getUserIdFromClaims_validClaims_returnsUserId() {
        String token = jwtTokenProvider.generateAccessToken(42L, "user42", "VIEWER", true);
        Claims claims = jwtTokenProvider.parseToken(token);

        assertThat(jwtTokenProvider.getUserIdFromClaims(claims)).isEqualTo(42L);
    }
}
