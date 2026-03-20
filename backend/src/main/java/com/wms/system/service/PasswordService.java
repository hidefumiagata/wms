package com.wms.system.service;

import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.system.entity.PasswordResetToken;
import com.wms.system.entity.User;
import com.wms.system.repository.PasswordResetTokenRepository;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemParameterService systemParameterService;

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("認証に失敗しました"));

        int lockThreshold = systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT");

        if (user.getLocked()) {
            throw new BadCredentialsException("アカウントがロックされています");
        }

        // 現在のパスワード検証
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            user.incrementFailedLogin(lockThreshold);
            userRepository.save(user);
            if (user.getLocked()) {
                throw new BadCredentialsException("アカウントがロックされています");
            }
            throw new BadCredentialsException("ユーザーコードまたはパスワードが正しくありません");
        }

        // 同一パスワードチェック
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new DuplicateResourceException("SAME_PASSWORD",
                    "新しいパスワードは現在のパスワードと異なるものを設定してください");
        }

        // パスワード更新
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.resetFailedLogin();
        userRepository.save(user);

        log.info("Password changed: userId={}", userId);
    }

    @Transactional
    public String requestPasswordReset(String identifier) {
        // ユーザー検索（user_code or email）
        Optional<User> userOpt = userRepository.findByUserCodeOrEmail(identifier, identifier);

        if (userOpt.isEmpty() || !userOpt.get().getIsActive()) {
            // ユーザー列挙防止: 存在しなくても成功レスポンスを返す
            log.info("Password reset requested for non-existent/inactive user: identifier={}", identifier);
            return null;
        }

        User user = userOpt.get();

        // トークン生成
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        int expiryMinutes = systemParameterService.getIntValue("PASSWORD_RESET_EXPIRY_MINUTES");

        // 既存トークンを削除してから新規作成
        passwordResetTokenRepository.deleteByUserId(user.getId());
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusMinutes(expiryMinutes))
                .build();
        passwordResetTokenRepository.save(resetToken);

        // TODO: メール送信（Azure Communication Services）— 現時点ではログ出力のみ
        log.info("Password reset token generated: userId={}, token={} (DEV ONLY - remove in production)",
                user.getId(), rawToken);

        return rawToken;
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        String tokenHash = sha256(token);

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "INVALID_TOKEN", "パスワードリセットリンクが無効です"));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.deleteByUserId(resetToken.getUserId());
            throw new BusinessRuleViolationException("INVALID_TOKEN",
                    "パスワードリセットリンクの有効期限が切れています");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "INVALID_TOKEN", "パスワードリセットリンクが無効です"));

        // パスワード更新 + アカウントロック解除
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.unlock();
        userRepository.save(user);

        // 全リセットトークン削除
        passwordResetTokenRepository.deleteByUserId(user.getId());

        log.info("Password reset confirmed: userId={}", user.getId());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
