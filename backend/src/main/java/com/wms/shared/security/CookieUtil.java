package com.wms.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final boolean secure;

    public CookieUtil(
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${cookie.secure:true}") boolean secure) {
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.secure = secure;
    }

    /** アクセストークンCookieを設定する */
    public void addAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(accessTokenExpiration / 1000)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** リフレッシュトークンCookieを設定する */
    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/v1/auth/refresh")
                .maxAge(refreshTokenExpiration / 1000)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** 認証Cookieを削除する（ログアウト用） */
    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/v1/auth/refresh")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
    }
}
