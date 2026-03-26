package com.wms.shared.exception;

import com.wms.shared.dto.ErrorResponse;
import com.wms.shared.logging.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.validation.ConstraintViolationException;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // --- カスタム業務例外 ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleViolationException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(OptimisticLockConflictException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidStateTransitionException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex);
    }

    // --- Jakarta Bean Validation ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> details = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ErrorResponse body = ErrorResponse.validation(
                "入力内容にエラーがあります",
                TraceContext.getCurrentTraceId(),
                details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // --- @Validated パス/クエリパラメータ ---

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorResponse.FieldError> details = ex.getConstraintViolations().stream()
                .map(cv -> new ErrorResponse.FieldError(
                        cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();

        ErrorResponse body = ErrorResponse.validation(
                "入力内容にエラーがあります",
                TraceContext.getCurrentTraceId(),
                details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // --- @Validated メソッドパラメータ (Spring Boot 3.2+) ---

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ErrorResponse.FieldError> details = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(err -> new ErrorResponse.FieldError(
                                result.getMethodParameter().getParameterName(),
                                err.getDefaultMessage())))
                .toList();

        ErrorResponse body = ErrorResponse.validation(
                "入力内容にエラーがあります",
                TraceContext.getCurrentTraceId(),
                details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // --- 不正リクエスト ---

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {
        ErrorResponse body = ErrorResponse.of(
                "MISSING_PARAMETER", ex.getMessage(),
                TraceContext.getCurrentTraceId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        ErrorResponse body = ErrorResponse.of(
                "INVALID_REQUEST_BODY", "リクエストボディの形式が不正です",
                TraceContext.getCurrentTraceId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // --- Spring Security ---

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse body = ErrorResponse.of(
                "FORBIDDEN", "この操作を実行する権限がありません",
                TraceContext.getCurrentTraceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        ErrorResponse body = ErrorResponse.of(
                "UNAUTHORIZED", "認証に失敗しました",
                TraceContext.getCurrentTraceId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // --- レートリミット ---

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: traceId={}", TraceContext.getCurrentTraceId());
        ErrorResponse body = ErrorResponse.of(
                "RATE_LIMIT_EXCEEDED", ex.getMessage(),
                TraceContext.getCurrentTraceId());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    // --- ResponseStatusException（HTTP ステータスをそのまま返す） ---

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException ex) {
        ErrorResponse body = ErrorResponse.of(
                ex.getStatusCode().toString(), ex.getReason(),
                TraceContext.getCurrentTraceId());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    // --- その他すべての例外 ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception: traceId={}", TraceContext.getCurrentTraceId(), ex);
        ErrorResponse body = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "システムエラーが発生しました",
                TraceContext.getCurrentTraceId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // --- ヘルパー ---

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, WmsException ex) {
        log.warn("Business exception: code={}, message={}, traceId={}",
                ex.getErrorCode(), ex.getMessage(), TraceContext.getCurrentTraceId());
        ErrorResponse body = ErrorResponse.of(
                ex.getErrorCode(), ex.getMessage(), TraceContext.getCurrentTraceId());
        return ResponseEntity.status(status).body(body);
    }
}
