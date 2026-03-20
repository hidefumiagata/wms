package com.wms.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp,
        String traceId,
        List<FieldError> details
) {
    public record FieldError(
            String field,
            String message
    ) {}

    /** 業務エラー用ファクトリ */
    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, OffsetDateTime.now(), traceId, null);
    }

    /** バリデーションエラー用ファクトリ */
    public static ErrorResponse validation(String message, String traceId,
                                            List<FieldError> details) {
        return new ErrorResponse("VALIDATION_ERROR", message,
                OffsetDateTime.now(), traceId, details);
    }
}
