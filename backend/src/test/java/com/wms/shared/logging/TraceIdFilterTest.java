package com.wms.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TraceIdFilterTest {

    @Mock
    private FilterChain filterChain;

    private TraceIdFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("フィルタチェーン実行中にMDCにtraceIdが設定される")
    void doFilterInternal_setsTraceIdInMdc() throws ServletException, IOException {
        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        doAnswer(invocation -> {
            capturedTraceId.set(MDC.get(TraceIdFilter.TRACE_ID_KEY));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(capturedTraceId.get()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("traceIdはハイフンなしの32文字UUIDである")
    void doFilterInternal_traceIdIsHexString() throws ServletException, IOException {
        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        doAnswer(invocation -> {
            capturedTraceId.set(MDC.get(TraceIdFilter.TRACE_ID_KEY));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(capturedTraceId.get())
                .hasSize(32)
                .matches("[0-9a-f]{32}");
    }

    @Test
    @DisplayName("レスポンスヘッダーにX-Trace-Idが設定される")
    void doFilterInternal_setsTraceIdResponseHeader() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        String headerValue = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(headerValue).isNotNull().isNotEmpty();
        assertThat(headerValue).hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    @DisplayName("MDCのtraceIdとレスポンスヘッダーのtraceIdが一致する")
    void doFilterInternal_mdcAndHeaderMatch() throws ServletException, IOException {
        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        doAnswer(invocation -> {
            capturedTraceId.set(MDC.get(TraceIdFilter.TRACE_ID_KEY));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        String headerValue = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(headerValue).isEqualTo(capturedTraceId.get());
    }

    @Test
    @DisplayName("フィルタチェーン完了後にMDCからtraceIdが削除される")
    void doFilterInternal_removesTraceIdFromMdcAfterChain() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("フィルタチェーンが例外をスローしてもMDCからtraceIdが削除される")
    void doFilterInternal_removesTraceIdFromMdcOnException() throws ServletException, IOException {
        doAnswer(invocation -> {
            throw new ServletException("test error");
        }).when(filterChain).doFilter(any(), any());

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (ServletException e) {
            // expected
        }

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("フィルタチェーンが呼ばれる")
    void doFilterInternal_delegatesToFilterChain() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
