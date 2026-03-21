package com.wms.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestLoggingFilter: リクエストログフィルター")
class RequestLoggingFilterTest {

    @Mock
    private FilterChain filterChain;

    private RequestLoggingFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        filterLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("正常系: フィルタチェーンに委譲される")
    void doFilterInternal_success_delegatesToFilterChain() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/items");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("正常系: GETリクエストのmethod, path, status, durationがINFOログに記録される")
    void doFilterInternal_getRequest_logsMethodPathStatusDuration() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/items");
        response.setStatus(200);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String msg = event.getFormattedMessage();
        assertThat(msg).contains("method=GET");
        assertThat(msg).contains("path=/api/v1/items");
        assertThat(msg).contains("status=200");
        assertThat(msg).contains("duration=");
    }

    @Test
    @DisplayName("正常系: POSTリクエストも正しくログされる")
    void doFilterInternal_postRequest_logsCorrectly() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        response.setStatus(201);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String msg = event.getFormattedMessage();
        assertThat(msg).contains("method=POST");
        assertThat(msg).contains("path=/api/v1/auth/login");
        assertThat(msg).contains("status=201");
    }

    @Test
    @DisplayName("正常系: エラーステータスでもINFOログが出力される")
    void doFilterInternal_errorStatus_logsAsInfo() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/notfound");
        response.setStatus(404);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("status=404");
    }

    @Test
    @DisplayName("異常系: doFilter例外時にWARNログが出力され例外が再スローされる")
    void doFilterInternal_exception_logsWarnAndRethrows() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/error");
        doThrow(new ServletException("filter error"))
            .when(filterChain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
            .isInstanceOf(ServletException.class)
            .hasMessage("filter error");

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        String msg = event.getFormattedMessage();
        assertThat(msg).contains("method=POST");
        assertThat(msg).contains("path=/api/v1/error");
        assertThat(msg).contains("error=filter error");
    }

    @Test
    @DisplayName("@OrderがTraceIdFilterの後に設定されている")
    void orderAnnotation_isAfterTraceIdFilter() {
        Order order = RequestLoggingFilter.class.getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }

    // --- CRLFサニタイズ ---

    @Test
    @DisplayName("CRLFサニタイズ: CRLFを含むURIがサニタイズされてログ出力される")
    void doFilterInternal_crlfInUri_sanitizedInLog() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/items\r\nInjected-Header: evil");
        response.setStatus(200);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(listAppender.list).hasSize(1);
        String msg = listAppender.list.get(0).getFormattedMessage();
        assertThat(msg).doesNotContain("\r");
        assertThat(msg).doesNotContain("\n");
        assertThat(msg).contains("path=/api/v1/itemsInjected-Header: evil");
    }

    @Test
    @DisplayName("CRLFサニタイズ: LFのみ含むURIもサニタイズされる")
    void doFilterInternal_lfInUri_sanitizedInLog() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/items\nfake-log-line");
        response.setStatus(200);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(listAppender.list).hasSize(1);
        String msg = listAppender.list.get(0).getFormattedMessage();
        assertThat(msg).doesNotContain("\n");
        assertThat(msg).contains("path=/api/v1/itemsfake-log-line");
    }

    @Test
    @DisplayName("CRLFサニタイズ: CRのみ含むURIもサニタイズされる")
    void doFilterInternal_crInUri_sanitizedInLog() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/items\rfake");
        response.setStatus(200);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(listAppender.list).hasSize(1);
        String msg = listAppender.list.get(0).getFormattedMessage();
        assertThat(msg).doesNotContain("\r");
        assertThat(msg).contains("path=/api/v1/itemsfake");
    }

    @Test
    @DisplayName("CRLFサニタイズ: 例外時もURIがサニタイズされてログ出力される")
    void doFilterInternal_exceptionWithCrlfUri_sanitizedInLog() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/items\r\nevil");
        doThrow(new ServletException("error"))
            .when(filterChain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
            .isInstanceOf(ServletException.class);

        assertThat(listAppender.list).hasSize(1);
        String msg = listAppender.list.get(0).getFormattedMessage();
        assertThat(msg).doesNotContain("\r");
        assertThat(msg).doesNotContain("\n");
        assertThat(msg).contains("path=/api/v1/itemsevil");
    }

    // --- sanitizeUri 単体テスト ---

    @ParameterizedTest(name = "sanitizeUri(\"{0}\") => \"{1}\"")
    @CsvSource({
        "/api/v1/items, /api/v1/items",
        "' ', ' '",
    })
    @DisplayName("sanitizeUri: CRLF無しの入力はそのまま返る")
    void sanitizeUri_noCrlf_returnsUnchanged(String input, String expected) {
        assertThat(RequestLoggingFilter.sanitizeUri(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("sanitizeUri: nullは空文字を返す")
    void sanitizeUri_null_returnsEmpty() {
        assertThat(RequestLoggingFilter.sanitizeUri(null)).isEmpty();
    }

    @Test
    @DisplayName("sanitizeUri: CRLFが除去される")
    void sanitizeUri_crlfRemoved() {
        assertThat(RequestLoggingFilter.sanitizeUri("/path\r\nevil")).isEqualTo("/pathevil");
    }

    @Test
    @DisplayName("sanitizeUri: LFのみが除去される")
    void sanitizeUri_lfRemoved() {
        assertThat(RequestLoggingFilter.sanitizeUri("/path\nevil")).isEqualTo("/pathevil");
    }

    @Test
    @DisplayName("sanitizeUri: CRのみが除去される")
    void sanitizeUri_crRemoved() {
        assertThat(RequestLoggingFilter.sanitizeUri("/path\revil")).isEqualTo("/pathevil");
    }

    // --- SENSITIVE_PATHS ---

    @Test
    @DisplayName("SENSITIVE_PATHS: ログインパスが含まれる")
    void sensitivePaths_containsLogin() {
        assertThat(RequestLoggingFilter.SENSITIVE_PATHS).contains("/api/v1/auth/login");
    }

    @Test
    @DisplayName("SENSITIVE_PATHS: パスワード変更パスが含まれる")
    void sensitivePaths_containsChangePassword() {
        assertThat(RequestLoggingFilter.SENSITIVE_PATHS).contains("/api/v1/auth/change-password");
    }

    @Test
    @DisplayName("SENSITIVE_PATHS: パスワードリセット確認パスが含まれる")
    void sensitivePaths_containsPasswordResetConfirm() {
        assertThat(RequestLoggingFilter.SENSITIVE_PATHS).contains("/api/v1/auth/password-reset/confirm");
    }

    @Test
    @DisplayName("SENSITIVE_PATHS: 08-common-infrastructure.md §4.5と一致する3件")
    void sensitivePaths_hasExactlyThreeEntries() {
        assertThat(RequestLoggingFilter.SENSITIVE_PATHS).hasSize(3);
    }
}
