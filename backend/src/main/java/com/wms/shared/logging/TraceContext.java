package com.wms.shared.logging;

import org.slf4j.MDC;

/**
 * MDCからtraceIdを取得するユーティリティ。
 */
public final class TraceContext {

    private TraceContext() {}

    public static String getCurrentTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return traceId != null ? traceId : "unknown";
    }
}
