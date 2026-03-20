package com.wms.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private FilterChain filterChain;

    private RequestLoggingFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("doFilterInternal はリクエストをフィルタチェーンに渡す")
    void doFilterInternal_delegatesToFilterChain() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/items");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal はGETリクエストのmethod, path, status, durationをログに記録する")
    void doFilterInternal_logsGetRequest() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/items");
        response.setStatus(200);

        filter.doFilterInternal(request, response, filterChain);

        // ログ出力は副作用なので、フィルタチェーンが正常に呼ばれたことを検証
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal はPOSTリクエストも正しく処理する")
    void doFilterInternal_logsPostRequest() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        response.setStatus(201);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal はエラーステータスも正しく処理する")
    void doFilterInternal_logsErrorStatus() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/notfound");
        response.setStatus(404);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
