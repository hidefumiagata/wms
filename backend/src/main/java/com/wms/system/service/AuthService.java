package com.wms.system.service;

import com.wms.shared.security.CookieUtil;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.entity.RefreshToken;
import com.wms.system.entity.User;
import com.wms.system.repository.RefreshTokenRepository;
import com.wms.system.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final CookieUtil cookieUtil;
    private final SystemParameterService systemParameterService;

    // BCryptダミーハッシュ（タイミング攻撃対策）— 有効なBCryptハッシュを使用する
    // echo -n "dummy-for-timing-attack-mitigation" | BCrypt(strength=12)
    private static final String DUMMY_HASH = "$2a$12$K4G0PXpOSGVlhPsmHXveNOYPenMdDBTMDO1YBr8Em5aFKTjCx4wHa";

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public User login(String userCode, String password, HttpServletResponse response) {
        int lockThreshold = systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT");

        Optional<User> userOpt = userRepository.findByUserCode(userCode);

        if (userOpt.isEmpty()) {
            // タイミング攻撃対策: ユーザーが存在しなくてもBCrypt比較を実行
            passwordEncoder.matches(password, DUMMY_HASH);
            throw new BadCredentialsException("ユーザーコードまたはパスワードが正しくありません");
        }

        User user = userOpt.get();

        // アカウント状態チェック（ユーザー列挙防止: 理由を問わず同一メッセージ）
        if (!user.getIsActive() || user.getLocked()) {
            // タイミング攻撃対策: パスワード照合と同等の処理時間を確保
            passwordEncoder.matches(password, user.getPasswordHash());
            log.warn("Login rejected: userCode={}, isActive={}, locked={}",
                    userCode, user.getIsActive(), user.getLocked());
            throw new BadCredentialsException("ユーザーコードまたはパスワードが正しくありません");
        }

        // パスワード検証
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.incrementFailedLogin(lockThreshold);
            userRepository.save(user);
            log.warn("Login failed: userCode={}, failedCount={}, locked={}",
                    userCode, user.getFailedLoginCount(), user.getLocked());
            throw new BadCredentialsException("ユーザーコードまたはパスワードが正しくありません");
        }

        // ログイン成功
        user.resetFailedLogin();
        userRepository.save(user);

        // トークン発行
        issueTokens(user, response);

        log.info("Login successful: userCode={}, role={}", user.getUserCode(), user.getRole());
        return user;
    }

    @Transactional
    public void logout(String accessToken, HttpServletResponse response) {
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Claims claims = jwtTokenProvider.parseTokenAllowExpired(accessToken);
                Long userId = jwtTokenProvider.getUserIdFromClaims(claims);
                refreshTokenRepository.deleteByUserId(userId);
                log.info("Logout successful: userId={}", userId);
            } catch (Exception e) {
                log.warn("Failed to parse token during logout", e);
            }
        }
        cookieUtil.clearAuthCookies(response);
    }

    @Transactional
    public User refresh(String refreshTokenRaw, String accessToken,
                         HttpServletResponse response) {
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            throw new BadCredentialsException("再度ログインしてください");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadCredentialsException("再度ログインしてください");
        }

        // access_tokenからuserIdを取得（期限切れ許容）
        Claims claims;
        try {
            claims = jwtTokenProvider.parseTokenAllowExpired(accessToken);
        } catch (Exception e) {
            throw new BadCredentialsException("再度ログインしてください");
        }

        Long userId = jwtTokenProvider.getUserIdFromClaims(claims);

        // DB上のリフレッシュトークンを検証
        RefreshToken storedToken = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new BadCredentialsException("再度ログインしてください"));

        if (storedToken.isExpired()) {
            refreshTokenRepository.deleteByUserId(userId);
            throw new BadCredentialsException("再度ログインしてください");
        }

        if (!passwordEncoder.matches(refreshTokenRaw, storedToken.getTokenHash())) {
            // トークンが不一致 → replay attack の可能性。全削除
            refreshTokenRepository.deleteByUserId(userId);
            throw new BadCredentialsException("再度ログインしてください");
        }

        // ユーザー状態チェック
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("再度ログインしてください"));
        if (!user.getIsActive() || user.getLocked()) {
            refreshTokenRepository.deleteByUserId(userId);
            throw new BadCredentialsException("再度ログインしてください");
        }

        // トークンローテーション
        refreshTokenRepository.deleteByUserId(userId);
        issueTokens(user, response);

        log.info("Token refresh successful: userId={}", userId);
        return user;
    }

    private void issueTokens(User user, HttpServletResponse response) {
        // アクセストークン
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUserCode(), user.getRole(),
                user.getPasswordChangeRequired());
        cookieUtil.addAccessTokenCookie(response, accessToken);

        // リフレッシュトークン
        String rawRefreshToken = UUID.randomUUID().toString();
        String hashedRefreshToken = passwordEncoder.encode(rawRefreshToken);

        refreshTokenRepository.deleteByUserId(user.getId());
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashedRefreshToken)
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);
        cookieUtil.addRefreshTokenCookie(response, rawRefreshToken);
    }
}
