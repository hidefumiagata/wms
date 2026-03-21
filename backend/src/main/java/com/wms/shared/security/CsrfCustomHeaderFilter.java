package com.wms.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF対策としてカスタムヘッダ（X-Requested-With）の存在を検証するフィルタ。
 * SameSite=Lax Cookieと組み合わせて二重防御を実現する。
 *
 * <p>状態変更メソッド（POST/PUT/PATCH/DELETE）に対して
 * X-Requested-Withヘッダの存在を要求する。
 * ブラウザのCORSプリフライト仕様により、クロスオリジンからの
 * カスタムヘッダ付きリクエストは自動的にブロックされる。</p>
 */
@Component
public class CsrfCustomHeaderFilter extends OncePerRequestFilter {

    private static final String CUSTOM_HEADER = "X-Requested-With";
    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        if (STATE_CHANGING_METHODS.contains(request.getMethod())) {
            String headerValue = request.getHeader(CUSTOM_HEADER);
            if (headerValue == null || headerValue.isBlank()) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"error\":\"Forbidden\",\"message\":\"Missing required header: X-Requested-With\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
