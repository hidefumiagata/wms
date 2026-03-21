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

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

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
            if (thrown != null) {
                log.warn("API request failed: method={}, path={}, duration={}ms, error={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        durationMs,
                        thrown.getMessage());
            } else {
                log.info("API request completed: method={}, path={}, status={}, duration={}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        durationMs);
            }
        }
    }
}
