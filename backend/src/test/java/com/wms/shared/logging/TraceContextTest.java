package com.wms.shared.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDCにtraceIdが設定されている場合、その値を返す")
    void getCurrentTraceId_traceIdInMdc_returnsTraceId() {
        String expectedTraceId = "abc123def456";
        MDC.put(TraceIdFilter.TRACE_ID_KEY, expectedTraceId);

        String result = TraceContext.getCurrentTraceId();

        assertThat(result).isEqualTo(expectedTraceId);
    }

    @Test
    @DisplayName("MDCが空の場合、unknownを返す")
    void getCurrentTraceId_mdcIsEmpty_returnsUnknown() {
        String result = TraceContext.getCurrentTraceId();

        assertThat(result).isEqualTo("unknown");
    }

    @Test
    @DisplayName("MDCのtraceIdがnullの場合、unknownを返す")
    void getCurrentTraceId_traceIdIsNull_returnsUnknown() {
        MDC.put("otherKey", "someValue");

        String result = TraceContext.getCurrentTraceId();

        assertThat(result).isEqualTo("unknown");
    }
}
