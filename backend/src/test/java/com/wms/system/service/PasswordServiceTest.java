package com.wms.system.service;

import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.system.entity.PasswordResetToken;
import com.wms.system.entity.User;
import com.wms.system.repository.PasswordResetTokenRepository;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SystemParameterService systemParameterService;

    @InjectMocks private PasswordService passwordService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .userCode("admin001")
                .fullName("テストユーザー")
                .email("admin@example.com")
                .passwordHash("$2a$12$currenthash")
                .role("SYSTEM_ADMIN")
                .isActive(true)
                .locked(false)
                .failedLoginCount(0)
                .passwordChangeRequired(true)
                .build();
    }

    // --- changePassword ---

    @Test
    void changePassword_userNotFound_throwsBadCredentials() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordService.changePassword(99L, "current", "newPass1"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changePassword_validInput_updatesPasswordAndFlags() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        when(passwordEncoder.matches("current", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("newPass1", user.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode("newPass1")).thenReturn("$2a$12$newhash");

        passwordService.changePassword(1L, "current", "newPass1");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
        assertThat(user.getPasswordChangeRequired()).isFalse();
        assertThat(user.getFailedLoginCount()).isEqualTo(0);
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_wrongCurrent_throwsBadCredentials() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> passwordService.changePassword(1L, "wrong", "newPass1"))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    void changePassword_samePassword_throwsDuplicateResource() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);
        // currentPasswordの検証成功 + newPasswordが現在パスワードと同一
        when(passwordEncoder.matches("currentPw", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("newPwSameAsCurrent", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> passwordService.changePassword(1L, "currentPw", "newPwSameAsCurrent"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("異なるものを設定");
    }

    @Test
    void changePassword_lockedAccount_throwsBadCredentials() {
        user.setLocked(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).thenReturn(5);

        assertThatThrownBy(() -> passwordService.changePassword(1L, "current", "newPass1"))
                .isInstanceOf(BadCredentialsException.class);
    }

    // --- requestPasswordReset ---

    @Test
    void requestPasswordReset_existingUser_generatesToken() {
        when(userRepository.findByUserCodeOrEmail("admin001", "admin001"))
                .thenReturn(Optional.of(user));
        when(systemParameterService.getIntValue("PASSWORD_RESET_EXPIRY_MINUTES")).thenReturn(30);

        String token = passwordService.requestPasswordReset("admin001");

        assertThat(token).isNotNull();
        verify(passwordResetTokenRepository).deleteByUserId(1L);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void requestPasswordReset_unknownUser_returnsNull() {
        when(userRepository.findByUserCodeOrEmail("unknown", "unknown"))
                .thenReturn(Optional.empty());

        String token = passwordService.requestPasswordReset("unknown");

        assertThat(token).isNull();
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void requestPasswordReset_inactiveUser_returnsNull() {
        user.setIsActive(false);
        when(userRepository.findByUserCodeOrEmail("admin001", "admin001"))
                .thenReturn(Optional.of(user));

        String token = passwordService.requestPasswordReset("admin001");

        assertThat(token).isNull();
    }

    // --- confirmPasswordReset ---

    @Test
    void confirmPasswordReset_validToken_updatesPasswordAndUnlocks() {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(1L)
                .tokenHash(sha256("valid-token"))
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .build();
        when(passwordResetTokenRepository.findByTokenHash(sha256("valid-token")))
                .thenReturn(Optional.of(resetToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1")).thenReturn("$2a$12$newhash");

        passwordService.confirmPasswordReset("valid-token", "NewPass1");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
        assertThat(user.getPasswordChangeRequired()).isFalse();
        assertThat(user.getLocked()).isFalse();
        verify(passwordResetTokenRepository).deleteByUserId(1L);
    }

    @Test
    void confirmPasswordReset_invalidToken_throwsBusinessRule() {
        when(passwordResetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordService.confirmPasswordReset("bad-token", "NewPass1"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("無効");
    }

    @Test
    void confirmPasswordReset_userNotFound_throwsBusinessRule() {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(99L)
                .tokenHash(sha256("valid-token2"))
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .build();
        when(passwordResetTokenRepository.findByTokenHash(sha256("valid-token2")))
                .thenReturn(Optional.of(resetToken));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordService.confirmPasswordReset("valid-token2", "NewPass1"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("無効");
    }

    @Test
    void confirmPasswordReset_expiredToken_throwsBusinessRule() {
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .userId(1L)
                .tokenHash(sha256("expired-token"))
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        when(passwordResetTokenRepository.findByTokenHash(sha256("expired-token")))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> passwordService.confirmPasswordReset("expired-token", "NewPass1"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("有効期限");
        verify(passwordResetTokenRepository).deleteByUserId(1L);
    }

    // helper: same SHA-256 as PasswordService
    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
