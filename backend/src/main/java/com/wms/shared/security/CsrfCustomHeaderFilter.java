package com.wms.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.shared.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Slf4j
@Component
@RequiredArgsConstructor
public class CsrfCustomHeaderFilter extends OncePerRequestFilter {

    private static final String CUSTOM_HEADER = "X-Requested-With";
    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        if (STATE_CHANGING_METHODS.contains(request.getMethod())) {
            String headerValue = request.getHeader(CUSTOM_HEADER);
            if (headerValue == null || headerValue.isBlank()) {
                log.warn("CSRF protection: missing X-Requested-With header. method={}, uri={}, remoteAddr={}",
                        request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                String traceId = MDC.get("traceId");
                ErrorResponse body = ErrorResponse.of(
                        "CSRF_HEADER_MISSING",
                        "Missing required header: X-Requested-With",
                        traceId);
                response.getWriter().write(objectMapper.writeValueAsString(body));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
