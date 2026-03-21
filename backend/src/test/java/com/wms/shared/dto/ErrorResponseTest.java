package com.wms.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    @DisplayName("of() ファクトリ: code, message, traceIdが設定され、detailsはnull")
    void of_withCodeMessageTraceId_createsResponseWithoutDetails() {
        ErrorResponse response = ErrorResponse.of("NOT_FOUND", "リソースが見つかりません", "trace-001");

        assertThat(response.code()).isEqualTo("NOT_FOUND");
        assertThat(response.message()).isEqualTo("リソースが見つかりません");
        assertThat(response.traceId()).isEqualTo("trace-001");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.details()).isNull();
    }

    @Test
    @DisplayName("validation() ファクトリ: codeがVALIDATION_ERRORでdetailsが設定される")
    void validation_withMessageAndDetails_createsValidationErrorResponse() {
        List<ErrorResponse.FieldError> details = List.of(
                new ErrorResponse.FieldError("name", "名前は必須です"),
                new ErrorResponse.FieldError("code", "コードは必須です")
        );

        ErrorResponse response = ErrorResponse.validation("入力エラー", "trace-002", details);

        assertThat(response.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.message()).isEqualTo("入力エラー");
        assertThat(response.traceId()).isEqualTo("trace-002");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.details()).hasSize(2);
    }

    @Test
    @DisplayName("FieldError レコード: field と message が正しく保持される")
    void fieldError_withFieldAndMessage_holdsValues() {
        ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError("email", "メールアドレスが不正です");

        assertThat(fieldError.field()).isEqualTo("email");
        assertThat(fieldError.message()).isEqualTo("メールアドレスが不正です");
    }

    @Test
    @DisplayName("of() で生成したtimestampが現在時刻に近い")
    void of_default_timestampIsRecentlyCreated() {
        ErrorResponse response = ErrorResponse.of("TEST", "test", "trace-003");

        assertThat(response.timestamp()).isNotNull();
        // timestamp should be within the last few seconds
        assertThat(response.timestamp().toInstant())
                .isCloseTo(java.time.Instant.now(), org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
    }
}
