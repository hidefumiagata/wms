package com.wms.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    /**
     * センシティブエンドポイント一覧。
     * これらのパスではリクエストボディのログ出力を行ってはならない。
     * 将来ボディログ機能を追加する際は、このセットでガードすること。
     *
     * @see docs/architecture-design/08-common-infrastructure.md §4.5 センシティブエンドポイント一覧
     */
    static final Set<String> SENSITIVE_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/change-password",
            "/api/v1/auth/password-reset/confirm"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.nanoTime();
        Throwable thrown = null;

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            thrown = ex;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            String sanitizedPath = sanitizeUri(request.getRequestURI());
            if (thrown != null) {
                log.warn("API request failed: method={}, path={}, duration={}ms, error={}",
                        request.getMethod(),
                        sanitizedPath,
                        durationMs,
                        thrown.getMessage());
            } else {
                log.info("API request completed: method={}, path={}, status={}, duration={}ms",
                        request.getMethod(),
                        sanitizedPath,
                        response.getStatus(),
                        durationMs);
            }
        }
    }

    /**
     * URIからCRLF文字を除去し、ログインジェクションを防止する。
     */
    static String sanitizeUri(String uri) {
        if (uri == null) {
            return "";
        }
        return uri.replace("\r", "").replace("\n", "");
    }
}
