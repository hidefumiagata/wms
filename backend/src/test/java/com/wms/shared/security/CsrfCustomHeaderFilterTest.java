package com.wms.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CsrfCustomHeaderFilterTest {

    private CsrfCustomHeaderFilter filter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new CsrfCustomHeaderFilter(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("状態変更メソッド + X-Requested-Withヘッダあり → 通過")
    void doFilterInternal_stateChangingMethodWithHeader_passes(String method) throws Exception {
        request.setMethod(method);
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("状態変更メソッド + X-Requested-Withヘッダなし → 403")
    void doFilterInternal_stateChangingMethodWithoutHeader_returns403(String method) throws Exception {
        request.setMethod(method);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_HEADER_MISSING");
        assertThat(response.getContentAsString()).contains("X-Requested-With");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("状態変更メソッド + X-Requested-Withが空白 → 403")
    void doFilterInternal_stateChangingMethodWithBlankHeader_returns403(String method) throws Exception {
        request.setMethod(method);
        request.addHeader("X-Requested-With", "   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("GETリクエスト → ヘッダなしでも通過")
    void doFilterInternal_getWithoutHeader_passes() throws Exception {
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("OPTIONSリクエスト → ヘッダなしでも通過")
    void doFilterInternal_optionsWithoutHeader_passes() throws Exception {
        request.setMethod("OPTIONS");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("HEADリクエスト → ヘッダなしでも通過")
    void doFilterInternal_headWithoutHeader_passes() throws Exception {
        request.setMethod("HEAD");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
