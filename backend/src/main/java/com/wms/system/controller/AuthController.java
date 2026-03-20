package com.wms.system.controller;

import com.wms.shared.exception.RateLimitExceededException;
import com.wms.shared.security.RateLimiterService;
import com.wms.shared.security.WmsUserDetails;
import com.wms.system.entity.User;
import com.wms.system.service.AuthService;
import com.wms.system.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;
    private final RateLimiterService rateLimiterService;

    // --- DTOs ---

    public record LoginRequest(
            @NotBlank(message = "ユーザーコードは必須です")
            String userCode,
            @NotBlank(message = "パスワードは必須です")
            String password
    ) {}

    public record LoginResponse(
            Long userId,
            String userCode,
            String fullName,
            String role,
            boolean passwordChangeRequired
    ) {
        public static LoginResponse from(User user) {
            return new LoginResponse(
                    user.getId(), user.getUserCode(), user.getFullName(),
                    user.getRole(), user.getPasswordChangeRequired());
        }
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "現在のパスワードは必須です")
            String currentPassword,
            @NotBlank(message = "新しいパスワードは必須です")
            @Size(min = 8, max = 128, message = "パスワードは8〜128文字で入力してください")
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)\\S+$",
                     message = "パスワードは大文字・小文字・数字を含む必要があります")
            String newPassword
    ) {}

    public record PasswordResetRequest(
            @NotBlank(message = "ユーザーコードまたはメールアドレスは必須です")
            String identifier
    ) {}

    public record PasswordResetConfirmRequest(
            @NotBlank(message = "トークンは必須です")
            String token,
            @NotBlank(message = "新しいパスワードは必須です")
            @Size(min = 8, max = 128, message = "パスワードは8〜128文字で入力してください")
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)\\S+$",
                     message = "パスワードは大文字・小文字・数字を含む必要があります")
            String newPassword
    ) {}

    // --- Endpoints ---

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        // レート制限: 同一IPから15分間に20回まで
        if (!rateLimiterService.tryConsumeLogin(getClientIp(httpRequest))) {
            throw new RateLimitExceededException();
        }
        User user = authService.login(request.userCode(), request.password(), response);
        return ResponseEntity.ok(LoginResponse.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        User user = authService.refresh(request, response);
        return ResponseEntity.ok(LoginResponse.from(user));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal WmsUserDetails userDetails) {
        passwordService.changePassword(
                userDetails.getUserId(),
                request.currentPassword(),
                request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        // レート制限: 同一IPから15分間に5回、同一identifierから15分間に3回
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.tryConsumePasswordResetByIp(clientIp)
                || !rateLimiterService.tryConsumePasswordResetByIdentifier(request.identifier())) {
            throw new RateLimitExceededException();
        }
        passwordService.requestPasswordReset(request.identifier());
        // ユーザー列挙防止: 常に同じレスポンスを返す
        return ResponseEntity.ok(Map.of(
                "message", "If the account exists, a password reset email has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, String>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of(
                "message", "Password has been reset successfully."));
    }

    // --- Helper ---

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // 最初のIPがクライアントIP
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
