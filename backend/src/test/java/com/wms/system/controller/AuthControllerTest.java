package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.generated.model.ChangePasswordRequest;
import com.wms.generated.model.LoginRequest;
import com.wms.generated.model.PasswordResetConfirmRequest;
import com.wms.generated.model.PasswordResetRequestBody;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.shared.security.RateLimiterService;
import com.wms.shared.security.WmsUserDetails;
import com.wms.system.entity.User;
import com.wms.system.service.AuthService;
import com.wms.system.service.PasswordService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordService passwordService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    // SecurityConfig dependencies - need to be mocked for context to load
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static User createTestUser() {
        return User.builder()
                .id(1L)
                .userCode("USR001")
                .fullName("Test User")
                .email("test@example.com")
                .passwordHash("hashed")
                .role("SYSTEM_ADMIN")
                .passwordChangeRequired(false)
                .build();
    }

    // ========== login ==========

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("正常系: ログイン成功時に200とLoginResponseを返す")
        void login_validCredentials_returns200WithLoginResponse() throws Exception {
            User user = createTestUser();
            when(rateLimiterService.tryConsumeLogin(anyString())).thenReturn(true);
            when(authService.login(eq("USR001"), eq("password123"), any())).thenReturn(user);

            LoginRequest request = new LoginRequest()
                    .userCode("USR001")
                    .password("password123");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.userCode").value("USR001"))
                    .andExpect(jsonPath("$.fullName").value("Test User"))
                    .andExpect(jsonPath("$.role").value("SYSTEM_ADMIN"))
                    .andExpect(jsonPath("$.passwordChangeRequired").value(false));

            verify(rateLimiterService).tryConsumeLogin(anyString());
            verify(authService).login(eq("USR001"), eq("password123"), any());
        }

        @Test
        @DisplayName("異常系: レートリミット超過時に429を返す")
        void login_rateLimitExceeded_returns429() throws Exception {
            when(rateLimiterService.tryConsumeLogin(anyString())).thenReturn(false);

            LoginRequest request = new LoginRequest()
                    .userCode("USR001")
                    .password("password123");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("正常系: X-Forwarded-ForヘッダからクライアントIPを取得する")
        void login_withXForwardedFor_usesFirstIp() throws Exception {
            User user = createTestUser();
            when(rateLimiterService.tryConsumeLogin(eq("203.0.113.1"))).thenReturn(true);
            when(authService.login(eq("USR001"), eq("password123"), any())).thenReturn(user);

            LoginRequest request = new LoginRequest()
                    .userCode("USR001")
                    .password("password123");

            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "203.0.113.1, 10.0.0.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(rateLimiterService).tryConsumeLogin("203.0.113.1");
        }

        @Test
        @DisplayName("異常系: リクエストボディが不正JSONの場合は400を返す")
        void login_invalidJson_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("異常系: 認証失敗時に401を返す")
        void login_badCredentials_returns401() throws Exception {
            when(rateLimiterService.tryConsumeLogin(anyString())).thenReturn(true);
            when(authService.login(anyString(), anyString(), any()))
                    .thenThrow(new org.springframework.security.authentication.BadCredentialsException("invalid"));

            LoginRequest request = new LoginRequest()
                    .userCode("USR001")
                    .password("wrong");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("正常系: X-Forwarded-Forが空文字の場合はremoteAddrを使用する")
        void login_withBlankXForwardedFor_usesRemoteAddr() throws Exception {
            User user = createTestUser();
            when(rateLimiterService.tryConsumeLogin(anyString())).thenReturn(true);
            when(authService.login(eq("USR001"), eq("password123"), any())).thenReturn(user);

            LoginRequest request = new LoginRequest()
                    .userCode("USR001")
                    .password("password123");

            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "  ")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ========== logout ==========

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("正常系: ログアウト成功時に204を返す")
        void logout_validRequest_returns204() throws Exception {
            doNothing().when(authService).logout(any(), any());

            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isNoContent());

            verify(authService).logout(isNull(), any());
        }

        @Test
        @DisplayName("正常系: access_tokenクッキーがある場合、トークン値をAuthServiceに渡す")
        void logout_withAccessTokenCookie_passesTokenToService() throws Exception {
            doNothing().when(authService).logout(eq("my-access-token"), any());

            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie("access_token", "my-access-token")))
                    .andExpect(status().isNoContent());

            verify(authService).logout(eq("my-access-token"), any());
        }
    }

    // ========== refreshToken ==========

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTokenTests {

        @Test
        @DisplayName("正常系: トークンリフレッシュ成功時に200とRefreshResponseを返す")
        void refreshToken_validToken_returns200WithRefreshResponse() throws Exception {
            User user = createTestUser();
            when(authService.refresh(anyString(), any(), any())).thenReturn(user);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", "valid-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.userCode").value("USR001"))
                    .andExpect(jsonPath("$.fullName").value("Test User"))
                    .andExpect(jsonPath("$.role").value("SYSTEM_ADMIN"));

            verify(authService).refresh(anyString(), any(), any());
        }
    }

    // ========== changePassword ==========

    @Nested
    @DisplayName("POST /api/v1/auth/change-password")
    class ChangePasswordTests {

        @Test
        @DisplayName("正常系: パスワード変更成功時に204を返す")
        void changePassword_validRequest_returns204() throws Exception {
            // Set up authenticated user in SecurityContext
            WmsUserDetails userDetails = new WmsUserDetails(
                    1L, "USR001", "password", null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            doNothing().when(passwordService).changePassword(eq(1L), eq("oldPass"), eq("newPass123!"));

            ChangePasswordRequest request = new ChangePasswordRequest()
                    .currentPassword("oldPass")
                    .newPassword("newPass123!");

            try {
                mockMvc.perform(post("/api/v1/auth/change-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isNoContent());

                verify(passwordService).changePassword(eq(1L), eq("oldPass"), eq("newPass123!"));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("異常系: 現在のパスワードが不正の場合は401を返す")
        void changePassword_wrongCurrent_returns401() throws Exception {
            WmsUserDetails userDetails = new WmsUserDetails(
                    1L, "USR001", "password", null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

            doThrow(new org.springframework.security.authentication.BadCredentialsException("wrong"))
                    .when(passwordService).changePassword(eq(1L), eq("wrongPass"), eq("newPass123!"));

            ChangePasswordRequest request = new ChangePasswordRequest()
                    .currentPassword("wrongPass")
                    .newPassword("newPass123!");

            try {
                mockMvc.perform(post("/api/v1/auth/change-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isUnauthorized());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("異常系: 同一パスワードの場合は409を返す")
        void changePassword_samePassword_returns409() throws Exception {
            WmsUserDetails userDetails = new WmsUserDetails(
                    1L, "USR001", "password", null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

            doThrow(new com.wms.shared.exception.DuplicateResourceException("SAME_PASSWORD", "同一パスワード"))
                    .when(passwordService).changePassword(eq(1L), eq("samePass"), eq("samePass"));

            ChangePasswordRequest request = new ChangePasswordRequest()
                    .currentPassword("samePass")
                    .newPassword("samePass");

            try {
                mockMvc.perform(post("/api/v1/auth/change-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isConflict());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    // ========== requestPasswordReset ==========

    @Nested
    @DisplayName("POST /api/v1/auth/password-reset/request")
    class RequestPasswordResetTests {

        @Test
        @DisplayName("正常系: パスワードリセット申請成功時に200と固定メッセージを返す")
        void requestPasswordReset_validIdentifier_returns200WithMessage() throws Exception {
            when(rateLimiterService.tryConsumePasswordResetByIp(anyString())).thenReturn(true);
            when(rateLimiterService.tryConsumePasswordResetByIdentifier(anyString())).thenReturn(true);
            when(passwordService.requestPasswordReset("test@example.com")).thenReturn(null);

            PasswordResetRequestBody request = new PasswordResetRequestBody()
                    .identifier("test@example.com");

            mockMvc.perform(post("/api/v1/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message")
                            .value("If the account exists, a password reset email has been sent."));

            verify(passwordService).requestPasswordReset("test@example.com");
        }

        @Test
        @DisplayName("異常系: IPレートリミット超過時に429を返す")
        void requestPasswordReset_rateLimitExceeded_byIp() throws Exception {
            when(rateLimiterService.tryConsumePasswordResetByIp(anyString())).thenReturn(false);

            PasswordResetRequestBody request = new PasswordResetRequestBody()
                    .identifier("test@example.com");

            mockMvc.perform(post("/api/v1/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("異常系: Identifierレートリミット超過時に429を返す")
        void requestPasswordReset_rateLimitExceeded_byIdentifier() throws Exception {
            when(rateLimiterService.tryConsumePasswordResetByIp(anyString())).thenReturn(true);
            when(rateLimiterService.tryConsumePasswordResetByIdentifier(anyString())).thenReturn(false);

            PasswordResetRequestBody request = new PasswordResetRequestBody()
                    .identifier("test@example.com");

            mockMvc.perform(post("/api/v1/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        }
    }

    // ========== confirmPasswordReset ==========

    @Nested
    @DisplayName("POST /api/v1/auth/password-reset/confirm")
    class ConfirmPasswordResetTests {

        @Test
        @DisplayName("正常系: パスワード再設定成功時に200と成功メッセージを返す")
        void confirmPasswordReset_validToken_returns200() throws Exception {
            doNothing().when(passwordService).confirmPasswordReset("valid-token", "newPass123!");

            PasswordResetConfirmRequest request = new PasswordResetConfirmRequest()
                    .token("valid-token")
                    .newPassword("newPass123!");

            mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message")
                            .value("Password has been reset successfully."));

            verify(passwordService).confirmPasswordReset("valid-token", "newPass123!");
        }

        @Test
        @DisplayName("異常系: 無効なトークンの場合は422を返す")
        void confirmPasswordReset_invalidToken_returns422() throws Exception {
            doThrow(new com.wms.shared.exception.BusinessRuleViolationException("INVALID_TOKEN", "無効"))
                    .when(passwordService).confirmPasswordReset(eq("bad-token"), anyString());

            PasswordResetConfirmRequest request = new PasswordResetConfirmRequest()
                    .token("bad-token")
                    .newPassword("newPass123!");

            mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
    }
}
