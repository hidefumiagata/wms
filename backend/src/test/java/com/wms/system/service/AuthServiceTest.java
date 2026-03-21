package com.wms.system.service;

import com.wms.shared.security.CookieUtil;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.entity.RefreshToken;
import com.wms.system.entity.User;
import com.wms.system.repository.RefreshTokenRepository;
import com.wms.system.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CookieUtil cookieUtil;
    @Mock private SystemParameterService systemParameterService;
    @Mock private HttpServletResponse response;
    @Mock private Claims claims;

    @InjectMocks private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 86400000L);
        activeUser = User.builder()
                .id(1L)
                .userCode("admin001")
                .fullName("テストユーザー")
                .email("admin@example.com")
                .passwordHash("$2a$12$hashedpassword")
                .role("SYSTEM_ADMIN")
                .isActive(true)
                .locked(false)
                .failedLoginCount(0)
                .passwordChangeRequired(false)
                .build();
    }

    // --- login ---

    @Test
    void login_validCredentials_returnsUser() {
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        when(userRepository.findByUserCode("admin001")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("correct-password", activeUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), anyBoolean())).thenReturn("jwt-token");
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-refresh-token");

        User result = authService.login("admin001", "correct-password", response);

        assertThat(result.getUserCode()).isEqualTo("admin001");
        assertThat(result.getFailedLoginCount()).isEqualTo(0);
        verify(cookieUtil).addAccessTokenCookie(eq(response), eq("jwt-token"));
        verify(cookieUtil).addRefreshTokenCookie(eq(response), anyString());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_userNotFound_throwsBadCredentials() {
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        when(userRepository.findByUserCode("unknown")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("unknown", "password", response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("ユーザーコードまたはパスワードが正しくありません");
    }

    @Test
    void login_wrongPassword_incrementsFailedCount() {
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        when(userRepository.findByUserCode("admin001")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong-password", activeUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin001", "wrong-password", response))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(activeUser.getFailedLoginCount()).isEqualTo(1);
        verify(userRepository).save(activeUser);
    }

    @Test
    void login_inactiveUser_throwsBadCredentials() {
        activeUser.setIsActive(false);
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        when(userRepository.findByUserCode("admin001")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin001", "password", response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("ユーザーコードまたはパスワードが正しくありません");
    }

    @Test
    void login_lockedUser_throwsBadCredentials() {
        activeUser.setLocked(true);
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        when(userRepository.findByUserCode("admin001")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin001", "password", response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("ユーザーコードまたはパスワードが正しくありません");
    }

    @Test
    void login_5thFailure_locksAccount() {
        activeUser.setFailedLoginCount(4);
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        when(userRepository.findByUserCode("admin001")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", activeUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin001", "wrong", response))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(activeUser.getLocked()).isTrue();
        assertThat(activeUser.getLockedAt()).isNotNull();
    }

    // --- logout ---

    @Test
    void logout_validToken_clearsTokensAndCookies() {
        when(jwtTokenProvider.parseTokenAllowExpired("jwt")).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(1L);

        authService.logout("jwt", response);

        verify(refreshTokenRepository).deleteByUserId(1L);
        verify(cookieUtil).clearAuthCookies(response);
    }

    @Test
    void logout_parseTokenFails_stillClearsCookies() {
        when(jwtTokenProvider.parseTokenAllowExpired("bad-jwt")).thenThrow(new RuntimeException("parse error"));

        authService.logout("bad-jwt", response);

        verify(cookieUtil).clearAuthCookies(response);
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void logout_nullToken_stillClearsCookies() {
        authService.logout(null, response);

        verify(cookieUtil).clearAuthCookies(response);
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void logout_blankToken_stillClearsCookies() {
        authService.logout("  ", response);

        verify(cookieUtil).clearAuthCookies(response);
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    // --- refresh ---

    @Test
    void refresh_validTokens_returnsUser() {
        when(jwtTokenProvider.parseTokenAllowExpired("expired-jwt")).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(1L);

        RefreshToken storedToken = RefreshToken.builder()
                .userId(1L).tokenHash("hashed").expiresAt(OffsetDateTime.now().plusHours(1)).build();
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches("raw-refresh", "hashed")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), anyBoolean())).thenReturn("new-jwt");
        when(passwordEncoder.encode(anyString())).thenReturn("new-hashed-refresh");

        User result = authService.refresh("raw-refresh", "expired-jwt", response);

        assertThat(result.getUserCode()).isEqualTo("admin001");
        verify(refreshTokenRepository, atLeast(2)).deleteByUserId(1L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refresh_nullRefreshToken_throwsBadCredentials() {
        assertThatThrownBy(() -> authService.refresh(null, "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_blankRefreshToken_throwsBadCredentials() {
        assertThatThrownBy(() -> authService.refresh("  ", "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_nullAccessToken_throwsBadCredentials() {
        assertThatThrownBy(() -> authService.refresh("raw", null, response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_blankAccessToken_throwsBadCredentials() {
        assertThatThrownBy(() -> authService.refresh("raw", "  ", response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_emptyRefreshToken_throwsBadCredentials() {
        assertThatThrownBy(() -> authService.refresh("", "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_noStoredToken_throwsBadCredentials() {
        when(jwtTokenProvider.parseTokenAllowExpired("jwt")).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(1L);
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("raw", "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_expiredRefreshToken_throwsBadCredentials() {
        when(jwtTokenProvider.parseTokenAllowExpired("jwt")).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(1L);

        RefreshToken expiredToken = RefreshToken.builder()
                .userId(1L).tokenHash("hashed").expiresAt(OffsetDateTime.now().minusHours(1)).build();
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh("raw", "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    void refresh_tokenMismatch_deletesAllTokens() {
        when(jwtTokenProvider.parseTokenAllowExpired("jwt")).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(1L);

        RefreshToken storedToken = RefreshToken.builder()
                .userId(1L).tokenHash("hashed").expiresAt(OffsetDateTime.now().plusHours(1)).build();
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches("wrong-raw", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("wrong-raw", "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    void refresh_parseTokenFails_throwsBadCredentials() {
        when(jwtTokenProvider.parseTokenAllowExpired("bad-jwt")).thenThrow(new RuntimeException("parse error"));

        assertThatThrownBy(() -> authService.refresh("raw", "bad-jwt", response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_userNotFound_throwsBadCredentials() {
        when(jwtTokenProvider.parseTokenAllowExpired("jwt")).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(1L);

        RefreshToken storedToken = RefreshToken.builder()
                .userId(1L).tokenHash("hashed").expiresAt(OffsetDateTime.now().plusHours(1)).build();
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches("raw", "hashed")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("raw", "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_lockedUser_throwsBadCredentials() {
        activeUser.setLocked(true);
        when(jwtTokenProvider.parseTokenAllowExpired("jwt")).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(1L);

        RefreshToken storedToken = RefreshToken.builder()
                .userId(1L).tokenHash("hashed").expiresAt(OffsetDateTime.now().plusHours(1)).build();
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches("raw", "hashed")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.refresh("raw", "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_inactiveUser_throwsBadCredentials() {
        activeUser.setIsActive(false);
        when(jwtTokenProvider.parseTokenAllowExpired("jwt")).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(1L);

        RefreshToken storedToken = RefreshToken.builder()
                .userId(1L).tokenHash("hashed").expiresAt(OffsetDateTime.now().plusHours(1)).build();
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches("raw", "hashed")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.refresh("raw", "jwt", response))
                .isInstanceOf(BadCredentialsException.class);
    }
}
