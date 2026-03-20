package com.wms.system.controller;

import com.wms.generated.api.AuthApi;
import com.wms.generated.model.ChangePasswordRequest;
import com.wms.generated.model.LoginRequest;
import com.wms.generated.model.LoginResponse;
import com.wms.generated.model.MessageResponse;
import com.wms.generated.model.PasswordResetConfirmRequest;
import com.wms.generated.model.PasswordResetRequestBody;
import com.wms.generated.model.RefreshResponse;
import com.wms.generated.model.UserRole;
import com.wms.shared.exception.RateLimitExceededException;
import com.wms.shared.security.RateLimiterService;
import com.wms.shared.security.WmsUserDetails;
import com.wms.system.entity.User;
import com.wms.system.service.AuthService;
import com.wms.system.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final PasswordService passwordService;
    private final RateLimiterService rateLimiterService;

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest loginRequest) {
        HttpServletRequest httpRequest = getHttpServletRequest();
        HttpServletResponse httpResponse = getHttpServletResponse();

        // レート制限: 同一IPから15分間に20回まで
        if (!rateLimiterService.tryConsumeLogin(getClientIp(httpRequest))) {
            throw new RateLimitExceededException();
        }

        User user = authService.login(
                loginRequest.getUserCode(), loginRequest.getPassword(), httpResponse);
        return ResponseEntity.ok(toLoginResponse(user));
    }

    @Override
    public ResponseEntity<Void> logout() {
        HttpServletRequest httpRequest = getHttpServletRequest();
        HttpServletResponse httpResponse = getHttpServletResponse();

        String accessToken = extractCookie(httpRequest, "access_token");
        authService.logout(accessToken, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RefreshResponse> refreshToken(String refreshToken) {
        HttpServletRequest httpRequest = getHttpServletRequest();
        HttpServletResponse httpResponse = getHttpServletResponse();

        String accessToken = extractCookie(httpRequest, "access_token");
        User user = authService.refresh(refreshToken, accessToken, httpResponse);
        return ResponseEntity.ok(toRefreshResponse(user));
    }

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequest changePasswordRequest) {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        passwordService.changePassword(
                userDetails.getUserId(),
                changePasswordRequest.getCurrentPassword(),
                changePasswordRequest.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MessageResponse> requestPasswordReset(
            PasswordResetRequestBody passwordResetRequestBody) {
        HttpServletRequest httpRequest = getHttpServletRequest();

        // レート制限: 同一IPから15分間に5回、同一identifierから15分間に3回
        String clientIp = getClientIp(httpRequest);
        String identifier = passwordResetRequestBody.getIdentifier();
        if (!rateLimiterService.tryConsumePasswordResetByIp(clientIp)
                || !rateLimiterService.tryConsumePasswordResetByIdentifier(identifier)) {
            throw new RateLimitExceededException();
        }

        passwordService.requestPasswordReset(identifier);
        // ユーザー列挙防止: 常に同じレスポンスを返す
        return ResponseEntity.ok(new MessageResponse()
                .message("If the account exists, a password reset email has been sent."));
    }

    @Override
    public ResponseEntity<MessageResponse> confirmPasswordReset(
            PasswordResetConfirmRequest passwordResetConfirmRequest) {
        passwordService.confirmPasswordReset(
                passwordResetConfirmRequest.getToken(),
                passwordResetConfirmRequest.getNewPassword());
        return ResponseEntity.ok(new MessageResponse()
                .message("Password has been reset successfully."));
    }

    // --- Helpers ---

    private LoginResponse toLoginResponse(User user) {
        return new LoginResponse()
                .userId(user.getId())
                .userCode(user.getUserCode())
                .fullName(user.getFullName())
                .role(UserRole.fromValue(user.getRole()))
                .passwordChangeRequired(user.getPasswordChangeRequired());
    }

    private RefreshResponse toRefreshResponse(User user) {
        return new RefreshResponse()
                .userId(user.getId())
                .userCode(user.getUserCode())
                .fullName(user.getFullName())
                .role(UserRole.fromValue(user.getRole()));
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest getHttpServletRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
    }

    private HttpServletResponse getHttpServletResponse() {
        return Objects.requireNonNull(
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                        .getResponse(),
                "HttpServletResponse is not available in current request context");
    }
}
