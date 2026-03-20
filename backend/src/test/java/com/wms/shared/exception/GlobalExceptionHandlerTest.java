package com.wms.shared.exception;

import com.wms.shared.dto.ErrorResponse;
import com.wms.shared.logging.TraceIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String TEST_TRACE_ID = "abc123def456";

    @InjectMocks
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        MDC.put(TraceIdFilter.TRACE_ID_KEY, TEST_TRACE_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(TraceIdFilter.TRACE_ID_KEY);
    }

    @Test
    @DisplayName("ResourceNotFoundException -> 404 NOT_FOUND")
    void handleNotFound_returns404() {
        var ex = new ResourceNotFoundException("ITEM_NOT_FOUND", "商品が見つかりません");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ITEM_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("商品が見つかりません");
        assertThat(response.getBody().traceId()).isEqualTo(TEST_TRACE_ID);
    }

    @Test
    @DisplayName("DuplicateResourceException -> 409 CONFLICT")
    void handleDuplicate_returns409() {
        var ex = new DuplicateResourceException("DUPLICATE_CODE", "コードが重複しています");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicate(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_CODE");
        assertThat(response.getBody().message()).isEqualTo("コードが重複しています");
        assertThat(response.getBody().traceId()).isEqualTo(TEST_TRACE_ID);
    }

    @Test
    @DisplayName("BusinessRuleViolationException -> 422 UNPROCESSABLE_ENTITY")
    void handleBusinessRule_returns422() {
        var ex = new BusinessRuleViolationException("INSUFFICIENT_STOCK", "在庫が不足しています");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessRule(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(response.getBody().message()).isEqualTo("在庫が不足しています");
    }

    @Test
    @DisplayName("OptimisticLockConflictException -> 409 CONFLICT")
    void handleOptimisticLock_returns409() {
        var ex = new OptimisticLockConflictException();

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
    }

    @Test
    @DisplayName("InvalidStateTransitionException -> 409 CONFLICT")
    void handleInvalidState_returns409() {
        var ex = InvalidStateTransitionException.of("INVALID_TRANSITION", "DRAFT", "SHIPPED");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_TRANSITION");
        assertThat(response.getBody().message()).contains("DRAFT").contains("SHIPPED");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException -> 400 BAD_REQUEST with field errors")
    void handleValidation_returns400WithDetails() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("obj", "name", "名前は必須です");
        FieldError fieldError2 = new FieldError("obj", "code", "コードは必須です");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        org.springframework.core.MethodParameter methodParam = mock(org.springframework.core.MethodParameter.class, withSettings().lenient());
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParam, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("入力内容にエラーがあります");
        assertThat(response.getBody().details()).hasSize(2);
        assertThat(response.getBody().details().get(0).field()).isEqualTo("name");
        assertThat(response.getBody().details().get(0).message()).isEqualTo("名前は必須です");
        assertThat(response.getBody().details().get(1).field()).isEqualTo("code");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("ConstraintViolationException -> 400 BAD_REQUEST with field errors")
    void handleConstraintViolation_returns400WithDetails() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("warehouseId");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be null");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().details()).hasSize(1);
        assertThat(response.getBody().details().get(0).field()).isEqualTo("warehouseId");
        assertThat(response.getBody().details().get(0).message()).isEqualTo("must not be null");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException -> 400 BAD_REQUEST")
    void handleMessageNotReadable_returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("parse error", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST_BODY");
        assertThat(response.getBody().message()).isEqualTo("リクエストボディの形式が不正です");
    }

    @Test
    @DisplayName("AccessDeniedException -> 403 FORBIDDEN")
    void handleAccessDenied_returns403() {
        AccessDeniedException ex = new AccessDeniedException("access denied");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("この操作を実行する権限がありません");
    }

    @Test
    @DisplayName("AuthenticationException -> 401 UNAUTHORIZED")
    void handleAuthentication_returns401() {
        BadCredentialsException ex = new BadCredentialsException("bad credentials");

        ResponseEntity<ErrorResponse> response = handler.handleAuthentication(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().message()).isEqualTo("認証に失敗しました");
    }

    @Test
    @DisplayName("RateLimitExceededException -> 429 TOO_MANY_REQUESTS")
    void handleRateLimit_returns429() {
        RateLimitExceededException ex = new RateLimitExceededException();

        ResponseEntity<ErrorResponse> response = handler.handleRateLimit(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(response.getBody().message()).contains("リクエスト回数の上限");
    }

    @Test
    @DisplayName("General Exception -> 500 INTERNAL_SERVER_ERROR")
    void handleGeneral_returns500() {
        RuntimeException ex = new RuntimeException("unexpected error");

        ResponseEntity<ErrorResponse> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).isEqualTo("システムエラーが発生しました");
        assertThat(response.getBody().traceId()).isEqualTo(TEST_TRACE_ID);
    }

    @Test
    @DisplayName("traceIdが未設定の場合 'unknown' が返される")
    void handleException_withoutTraceId_returnsUnknown() {
        MDC.remove(TraceIdFilter.TRACE_ID_KEY);
        RuntimeException ex = new RuntimeException("no trace");

        ResponseEntity<ErrorResponse> response = handler.handleGeneral(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().traceId()).isEqualTo("unknown");
    }
}
