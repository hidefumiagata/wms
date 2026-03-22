# アーキテクチャ設計書 — 共通基盤設計

> 本書はアーキテクチャブループリント [08-common-infrastructure.md](../architecture-blueprint/08-common-infrastructure.md) で定義された方針に基づき、
> フロントエンド・バックエンド双方の共通基盤の実装設計を詳述する。

---

## 目次

1. [共通エラーハンドリング設計](#1-共通エラーハンドリング設計)
2. [共通DTO設計](#2-共通dto設計)
3. [共通バリデーション設計](#3-共通バリデーション設計)
4. [共通ログ設計](#4-共通ログ設計)
5. [共通日時処理設計](#5-共通日時処理設計)
6. [共通コード管理設計](#6-共通コード管理設計)
7. [共通メッセージ管理設計](#7-共通メッセージ管理設計)
8. [監査証跡設計](#8-監査証跡設計)
9. [共通ユーティリティ設計](#9-共通ユーティリティ設計)

---

## 1. 共通エラーハンドリング設計

> エラーレスポンス形式・例外クラス階層・エラーコード体系は [architecture-blueprint/08-common-infrastructure.md](../architecture-blueprint/08-common-infrastructure.md) を参照。
> 本セクションでは実装詳細を記述する。

### 1.1 バックエンド — エラーレスポンスDTO

```java
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
    public static ErrorResponse of(String code, String message,
                                    String traceId) {
        return new ErrorResponse(code, message,
            OffsetDateTime.now(), traceId, null);
    }

    /** バリデーションエラー用ファクトリ */
    public static ErrorResponse validation(String message,
                                            String traceId,
                                            List<FieldError> details) {
        return new ErrorResponse("VALIDATION_ERROR", message,
            OffsetDateTime.now(), traceId, details);
    }
}
```

### 1.2 バックエンド — GlobalExceptionHandler

```java
package com.wms.shared.exception;

import com.wms.shared.dto.ErrorResponse;
import com.wms.shared.logging.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
        LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- カスタム業務例外 ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleViolationException ex) {
        return buildResponse(
            HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(OptimisticLockConflictException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(
            InvalidStateTransitionException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex);
    }

    // --- Jakarta Bean Validation ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> details =
            ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                    fe.getField(), fe.getDefaultMessage()))
                .toList();

        ErrorResponse body = ErrorResponse.validation(
            "入力内容にエラーがあります",
            TraceContext.getCurrentTraceId(),
            details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(body);
    }

    // --- Spring Security ---

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex) {
        ErrorResponse body = ErrorResponse.of(
            "FORBIDDEN", "この操作を実行する権限がありません",
            TraceContext.getCurrentTraceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex) {
        ErrorResponse body = ErrorResponse.of(
            "UNAUTHORIZED", "認証に失敗しました",
            TraceContext.getCurrentTraceId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(body);
    }

    // --- その他すべての例外 ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex) {
        log.error("Unhandled exception: traceId={}",
            TraceContext.getCurrentTraceId(), ex);
        ErrorResponse body = ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "システムエラーが発生しました",
            TraceContext.getCurrentTraceId());
        return ResponseEntity.status(
            HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // --- ヘルパー ---

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, WmsException ex) {
        log.warn("Business exception: code={}, message={}, traceId={}",
            ex.getErrorCode(), ex.getMessage(),
            TraceContext.getCurrentTraceId());
        ErrorResponse body = ErrorResponse.of(
            ex.getErrorCode(), ex.getMessage(),
            TraceContext.getCurrentTraceId());
        return ResponseEntity.status(status).body(body);
    }
}
```

### 1.3 バックエンド — カスタム例外クラス

> 例外クラス階層は [architecture-blueprint/08-common-infrastructure.md](../architecture-blueprint/08-common-infrastructure.md) を参照。

```java
package com.wms.shared.exception;

/** 全カスタム例外の基底クラス */
public abstract class WmsException extends RuntimeException {
    private final String errorCode;

    protected WmsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected WmsException(String errorCode, String message,
                           Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
```

```java
package com.wms.shared.exception;

/** 404 Not Found — 指定リソースが存在しない */
public class ResourceNotFoundException extends WmsException {
    public ResourceNotFoundException(String errorCode,
                                      String message) {
        super(errorCode, message);
    }

    /** 汎用ファクトリ: "XXX が見つかりません (id=123)" */
    public static ResourceNotFoundException of(
            String errorCode, String resourceName, Object id) {
        return new ResourceNotFoundException(errorCode,
            String.format("%s が見つかりません (id=%s)",
                resourceName, id));
    }
}
```

```java
package com.wms.shared.exception;

/** 409 Conflict — 一意制約違反・重複登録 */
public class DuplicateResourceException extends WmsException {
    public DuplicateResourceException(String errorCode,
                                       String message) {
        super(errorCode, message);
    }
}
```

```java
package com.wms.shared.exception;

/** 422 Unprocessable Entity — 業務ルール違反 */
public class BusinessRuleViolationException extends WmsException {
    public BusinessRuleViolationException(String errorCode,
                                           String message) {
        super(errorCode, message);
    }
}
```

```java
package com.wms.shared.exception;

/** 409 Conflict — 楽観的ロック競合 */
public class OptimisticLockConflictException extends WmsException {
    private static final String DEFAULT_CODE = "OPTIMISTIC_LOCK_CONFLICT";

    public OptimisticLockConflictException() {
        super(DEFAULT_CODE,
            "他のユーザーが更新済みです。画面を再読み込みしてください");
    }

    public OptimisticLockConflictException(String message) {
        super(DEFAULT_CODE, message);
    }
}
```

```java
package com.wms.shared.exception;

/** 409 Conflict — 状態遷移不正 */
public class InvalidStateTransitionException extends WmsException {
    public InvalidStateTransitionException(String errorCode,
                                            String message) {
        super(errorCode, message);
    }

    public static InvalidStateTransitionException of(
            String errorCode,
            String currentStatus, String targetStatus) {
        return new InvalidStateTransitionException(errorCode,
            String.format(
                "現在のステータス「%s」から「%s」への遷移はできません",
                currentStatus, targetStatus));
    }
}
```

### 1.4 フロントエンド — Axiosインターセプターによる共通エラー処理

> レスポンスインターセプターの処理方針は [architecture-blueprint/03-frontend-architecture.md](../architecture-blueprint/03-frontend-architecture.md) を参照。

```typescript
// src/utils/api.ts
import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

// リフレッシュ制御
let isRefreshing = false
let pendingRequests: Array<{
  resolve: (value: any) => void
  reject: (reason: any) => void
  config: InternalAxiosRequestConfig
}> = []

function processPendingRequests() {
  pendingRequests.forEach(({ resolve, config }) => {
    resolve(apiClient.request(config))
  })
  pendingRequests = []
}

function rejectPendingRequests(error: any) {
  pendingRequests.forEach(({ reject }) => reject(error))
  pendingRequests = []
}

// レスポンスインターセプター
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean
    }
    const status = error.response?.status

    // 401: トークンリフレッシュ試行
    if (status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      if (isRefreshing) {
        // リフレッシュ中は待機キューに追加
        return new Promise((resolve, reject) => {
          pendingRequests.push({
            resolve, reject, config: originalRequest,
          })
        })
      }

      isRefreshing = true
      try {
        await axios.post(
          `${import.meta.env.VITE_API_BASE_URL}/api/v1/auth/refresh`,
          {},
          { withCredentials: true },
        )
        processPendingRequests()
        return apiClient.request(originalRequest)
      } catch {
        rejectPendingRequests(error)
        router.push({
          name: 'login',
          query: { reason: 'session_expired' },
        })
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    // 403: 権限不足
    if (status === 403) {
      ElMessage.error('この操作を実行する権限がありません')
      return Promise.reject(error)
    }

    // 500: サーバーエラー
    if (status === 500) {
      ElMessage.error('システムエラーが発生しました')
      return Promise.reject(error)
    }

    // 400, 409, 422: Composable側でハンドリング
    return Promise.reject(error)
  },
)

export default apiClient

/** APIエラーレスポンスの型定義 */
export interface ApiErrorResponse {
  code: string
  message: string
  timestamp: string
  traceId: string
  details?: Array<{ field: string; message: string }>
}
```

### 1.5 フロントエンド — Composableでの業務エラー処理パターン

```typescript
// composables/shared/useApiErrorHandler.ts
import { ElMessage } from 'element-plus'
import type { ApiErrorResponse } from '@/utils/api'
import type { AxiosError } from 'axios'

/**
 * 業務エラー（400/409/422）を処理するユーティリティ。
 * 各Composableの try/catch 内で使用する。
 */
export function useApiErrorHandler() {

  /**
   * バリデーションエラー（400）を VeeValidate の setFieldError に反映する。
   */
  function handleValidationError(
    error: AxiosError<ApiErrorResponse>,
    setFieldError: (field: string, message: string) => void,
  ): boolean {
    if (error.response?.status !== 400) return false
    const details = error.response.data.details
    if (!details?.length) return false

    details.forEach(({ field, message }) => {
      setFieldError(field, message)
    })
    return true
  }

  /**
   * 楽観的ロック競合（409 + OPTIMISTIC_LOCK_CONFLICT）を処理する。
   */
  function handleOptimisticLockError(
    error: AxiosError<ApiErrorResponse>,
  ): boolean {
    if (error.response?.status !== 409) return false
    if (error.response.data.code !== 'OPTIMISTIC_LOCK_CONFLICT') return false

    ElMessage.error(
      '他のユーザーが更新済みです。画面を再読み込みしてください',
    )
    return true
  }

  /**
   * 汎用業務エラー（409/422）をトースト表示する。
   */
  function handleBusinessError(
    error: AxiosError<ApiErrorResponse>,
  ): boolean {
    const status = error.response?.status
    if (status !== 409 && status !== 422) return false

    ElMessage.error(
      error.response?.data.message ?? '業務エラーが発生しました',
    )
    return true
  }

  return {
    handleValidationError,
    handleOptimisticLockError,
    handleBusinessError,
  }
}
```

---

## 2. 共通DTO設計

> DTO命名規則・Entity-DTO変換方式は [architecture-blueprint/04-backend-architecture.md](../architecture-blueprint/04-backend-architecture.md) を参照。

### 2.1 バックエンド — ページネーションレスポンスDTO

```java
package com.wms.shared.dto;

import java.util.List;

/**
 * ページング一覧レスポンスの共通ラッパー。
 * Spring Data の Page を本DTOに変換して返す。
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    /**
     * Spring Data Page からファクトリ変換する。
     *
     * @param springPage Spring Data の Page オブジェクト
     * @param converter  Entity → ResponseDTO の変換関数
     */
    public static <E, T> PageResponse<T> from(
            org.springframework.data.domain.Page<E> springPage,
            java.util.function.Function<E, T> converter) {
        List<T> content = springPage.getContent().stream()
            .map(converter)
            .toList();
        return new PageResponse<>(
            content,
            springPage.getNumber(),
            springPage.getSize(),
            springPage.getTotalElements(),
            springPage.getTotalPages()
        );
    }
}
```

### 2.2 バックエンド — ページネーションリクエストの制御

```java
package com.wms.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableSpringDataWebSupport
public class PaginationConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver resolver =
            new PageableHandlerMethodArgumentResolver();
        resolver.setMaxPageSize(100);   // sizeの上限: 100
        resolver.setFallbackPageable(
            org.springframework.data.domain.PageRequest.of(0, 20));
        resolvers.add(resolver);
    }
}
```

### 2.3 バックエンド — 検索条件DTOパターン

```java
package com.wms.master.dto;

/**
 * 検索条件DTO。各モジュールの dto/ に配置する。
 * クエリパラメータを @ModelAttribute で受け取る。
 */
public record WarehouseSearchCriteria(
    String keyword,        // 倉庫コード・倉庫名の部分一致
    Boolean isActive       // 有効/無効フィルタ（null = 全件）
) {}
```

検索条件を JPA Specification に変換するパターン:

```java
package com.wms.master.repository;

import com.wms.master.dto.WarehouseSearchCriteria;
import com.wms.master.entity.Warehouse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class WarehouseSpecifications {

    public static Specification<Warehouse> fromCriteria(
            WarehouseSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.keyword() != null
                    && !criteria.keyword().isBlank()) {
                String pattern =
                    "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(
                        root.get("warehouseCode")), pattern),
                    cb.like(cb.lower(
                        root.get("warehouseName")), pattern)
                ));
            }

            if (criteria.isActive() != null) {
                predicates.add(
                    cb.equal(root.get("isActive"),
                        criteria.isActive()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

### 2.4 バックエンド — Controller での使用例

```java
@GetMapping("/api/v1/master/warehouses")
public ResponseEntity<PageResponse<WarehouseResponse>> list(
        @ModelAttribute WarehouseSearchCriteria criteria,
        Pageable pageable) {
    Page<Warehouse> page = warehouseRepository.findAll(
        WarehouseSpecifications.fromCriteria(criteria), pageable);
    return ResponseEntity.ok(
        PageResponse.from(page, WarehouseResponse::from));
}
```

### 2.5 フロントエンド — 共通型定義

```typescript
// src/types/common.ts

/** ページングレスポンス共通型 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** ページングリクエスト共通型 */
export interface PageRequest {
  page: number
  size: number
  sort?: string
}

/** APIエラーレスポンス型 */
export interface ApiErrorResponse {
  code: string
  message: string
  timestamp: string
  traceId: string
  details?: FieldError[]
}

export interface FieldError {
  field: string
  message: string
}
```

### 2.6 フロントエンド — ページネーション Composable

```typescript
// src/composables/shared/usePagination.ts
import { ref, computed } from 'vue'

/**
 * ページネーションの状態管理。
 * 各一覧画面の Composable 内でインラインで状態を管理する方針だが、
 * ページング計算ロジックのみ共通化する。
 */
export function usePagination(defaultSize = 20) {
  const page = ref(0)
  const size = ref(defaultSize)
  const totalElements = ref(0)
  const totalPages = ref(0)

  const hasNext = computed(() => page.value < totalPages.value - 1)
  const hasPrev = computed(() => page.value > 0)

  function updateFromResponse(response: {
    page: number; size: number
    totalElements: number; totalPages: number
  }) {
    page.value = response.page
    size.value = response.size
    totalElements.value = response.totalElements
    totalPages.value = response.totalPages
  }

  function goToPage(p: number) {
    page.value = Math.max(0, Math.min(p, totalPages.value - 1))
  }

  function reset() {
    page.value = 0
  }

  return {
    page, size, totalElements, totalPages,
    hasNext, hasPrev,
    updateFromResponse, goToPage, reset,
  }
}
```

---

## 3. 共通バリデーション設計

### 3.1 バリデーション層構成

| 層 | 技術 | 責務 |
|---|------|------|
| **フロントエンド（入力時）** | VeeValidate + Zod | UXのためのリアルタイムバリデーション |
| **バックエンド Controller層** | Jakarta Bean Validation | リクエスト形式の検証（型・必須・文字数等） |
| **バックエンド Service層** | Javaロジック | 業務ルールの検証（重複チェック・状態遷移・在庫制約等） |

### 3.2 バックエンド — Jakarta Bean Validation

```java
package com.wms.master.dto;

import jakarta.validation.constraints.*;

public record CreateWarehouseRequest(
    @NotBlank(message = "倉庫コードは必須です")
    @Size(max = 50, message = "倉庫コードは50文字以内で入力してください")
    @Pattern(regexp = "^[A-Z]{4}$",
        message = "倉庫コードは英大文字4文字で入力してください")
    String warehouseCode,

    @NotBlank(message = "倉庫名は必須です")
    @Size(max = 200, message = "倉庫名は200文字以内で入力してください")
    String warehouseName,

    @Size(max = 200,
        message = "倉庫名カナは200文字以内で入力してください")
    String warehouseNameKana,

    @Size(max = 500,
        message = "住所は500文字以内で入力してください")
    String address
) {}
```

### 3.3 バックエンド — カスタムバリデーションアノテーション

パスワードポリシーなど複合バリデーションにはカスタムアノテーションを使用する。

> パスワードポリシーの値は [architecture-blueprint/10-security-architecture.md](../architecture-blueprint/10-security-architecture.md) を参照。

```java
package com.wms.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface PasswordPolicy {
    String message() default
        "パスワードは8〜128文字で、英大文字・英小文字・数字を各1文字以上含めてください";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
package com.wms.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator
        implements ConstraintValidator<PasswordPolicy, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    @Override
    public boolean isValid(String value,
                           ConstraintValidatorContext context) {
        if (value == null) return true; // @NotNull で別途制御
        if (value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH) {
            return false;
        }
        boolean hasUpper = value.chars()
            .anyMatch(Character::isUpperCase);
        boolean hasLower = value.chars()
            .anyMatch(Character::isLowerCase);
        boolean hasDigit = value.chars()
            .anyMatch(Character::isDigit);
        return hasUpper && hasLower && hasDigit;
    }
}
```

### 3.4 フロントエンド — Zodスキーマ定義パターン

```typescript
// composables/master/useWarehouseForm.ts（一部抜粋）
import { z } from 'zod'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'

const warehouseSchema = z.object({
  warehouseCode: z.string()
    .min(1, '倉庫コードは必須です')
    .max(50, '倉庫コードは50文字以内で入力してください')
    .regex(/^[A-Z]{4}$/, '倉庫コードは英大文字4文字で入力してください'),
  warehouseName: z.string()
    .min(1, '倉庫名は必須です')
    .max(200, '倉庫名は200文字以内で入力してください'),
  warehouseNameKana: z.string()
    .max(200, '倉庫名カナは200文字以内で入力してください')
    .optional(),
  address: z.string()
    .max(500, '住所は500文字以内で入力してください')
    .optional(),
})

type WarehouseFormValues = z.infer<typeof warehouseSchema>
```

### 3.5 フロントエンド — VeeValidateとElement Plusの統合

```typescript
// composables/master/useWarehouseForm.ts（VeeValidate統合部分）
export function useWarehouseForm(mode: 'create' | 'edit') {
  const { handleSubmit, setFieldError, resetForm, errors, values }
    = useForm<WarehouseFormValues>({
      validationSchema: toTypedSchema(warehouseSchema),
    })

  // Element Plus の el-form-item に errors をバインド
  // テンプレート例:
  // <el-form-item :error="errors.warehouseCode">
  //   <el-input v-model="values.warehouseCode" />
  // </el-form-item>

  return { handleSubmit, setFieldError, resetForm, errors, values }
}
```

### 3.6 バリデーションルール一覧

| ルール種別 | FE（Zod） | BE（Jakarta BV） | BE（Service） |
|-----------|:---------:|:----------------:|:------------:|
| 必須チェック | `min(1, ...)` | `@NotBlank` | -- |
| 文字数上限 | `max(N, ...)` | `@Size(max=N)` | -- |
| 正規表現 | `regex(...)` | `@Pattern(...)` | -- |
| 数値範囲 | `min(N).max(M)` | `@Min` / `@Max` | -- |
| 日付形式 | Zod date | -- | -- |
| コード重複 | -- | -- | Repository照会 |
| 状態遷移 | -- | -- | ステータスチェック |
| 在庫制約 | -- | -- | 在庫照会 |
| 楽観的ロック | -- | -- | `@Version` 比較 |

---

## 4. 共通ログ設計

> ログ収集フロー・標準フォーマット・PII マスキングは [architecture-blueprint/08-common-infrastructure.md](../architecture-blueprint/08-common-infrastructure.md) を参照。

#### サーブレットフィルター実行順序

| 順序 | フィルター | @Order | 役割 |
|------|-----------|--------|------|
| 1 | `TraceIdFilter` | `Ordered.HIGHEST_PRECEDENCE` | traceId 生成・MDC 設定 |
| 2 | `RequestLoggingFilter` | `Ordered.HIGHEST_PRECEDENCE + 1` | リクエスト計時・アクセスログ |
| 3 | Spring Security FilterChain | デフォルト | 認証・認可（JWT検証、userId MDC設定） |

> **設計意図**: TraceIdFilter を最優先で実行し、後続の全フィルター・ログに traceId が付与されることを保証する。RequestLoggingFilter はその直後に配置し、セキュリティフィルターを含むリクエスト全体の処理時間を計測する。

### 4.1 バックエンド — 相関ID（TraceId）管理

リクエストごとにUUID v4 を生成し、MDC（Mapped Diagnostic Context）に設定する。全ログに自動付与される。

```java
package com.wms.shared.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {
        try {
            String traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put(TRACE_ID_KEY, traceId);

            // レスポンスヘッダーにも付与（デバッグ用）
            if (response instanceof HttpServletResponse httpRes) {
                httpRes.setHeader(TRACE_ID_HEADER, traceId);
            }

            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
```

```java
package com.wms.shared.logging;

import org.slf4j.MDC;

/** MDC から現在の traceId を取得するヘルパー */
public final class TraceContext {

    private TraceContext() {}

    public static String getCurrentTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return traceId != null ? traceId : "unknown";
    }
}
```

### 4.2 バックエンド — Logback JSON設定

プロファイルごとに異なるエンコーダーを使用し、両環境でPIIマスキングを適用する。

```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
  <!-- 開発環境: テキスト形式 + PIIマスキング -->
  <springProfile name="dev">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="com.wms.shared.logging.PiiMaskingPatternLayoutEncoder">
        <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] [%X{userId}] - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="DEBUG">
      <appender-ref ref="CONSOLE" />
    </root>
  </springProfile>

  <!-- 本番環境: JSON形式 + PIIマスキング -->
  <springProfile name="prd">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
        <includeMdcKeyName>module</includeMdcKeyName>
        <!-- デフォルトの message/stackTrace を PIIマスキング版で置換 -->
        <provider class="com.wms.shared.logging.PiiMaskingMessageJsonProvider">
          <fieldName>message</fieldName>
        </provider>
        <provider class="com.wms.shared.logging.PiiMaskingStackTraceJsonProvider">
          <fieldName>stack_trace</fieldName>
        </provider>
        <fieldNames>
          <timestamp>timestamp</timestamp>
          <message>[ignore]</message>
          <stackTrace>[ignore]</stackTrace>
          <logger>logger</logger>
          <level>level</level>
        </fieldNames>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="JSON" />
    </root>
  </springProfile>
</configuration>
```

### 4.3 バックエンド — 操作ログAOP

主要な業務操作をAOPで横断的にロギングする。

```java
package com.wms.shared.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceLoggingAspect {

    private static final Logger log =
        LoggerFactory.getLogger(ServiceLoggingAspect.class);

    /**
     * Service 層の public メソッドの実行時間をログ出力する。
     */
    @Around("execution(* com.wms..service.*Service.*(..))")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint)
            throws Throwable {
        String className = joinPoint.getTarget().getClass()
            .getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // モジュール名を MDC に設定
        String module = extractModule(
            joinPoint.getTarget().getClass().getPackageName());
        MDC.put("module", module);

        log.info("START {}.{}", className, methodName);
        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("END {}.{} [{}ms]",
                className, methodName, elapsed);
            return result;
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("FAIL {}.{} [{}ms] - {}",
                className, methodName, elapsed, ex.getMessage());
            throw ex;
        } finally {
            MDC.remove("module");
        }
    }

    private String extractModule(String packageName) {
        // com.wms.inbound.service -> inbound
        String[] parts = packageName.split("\\.");
        return parts.length >= 3 ? parts[2] : "unknown";
    }
}
```

### 4.4 バックエンド — PII マスキング

PIIマスキングはロジック（`PiiMasker`）と環境別の Logback 拡張クラスに分離している。

| クラス | 役割 |
|--------|------|
| `PiiMasker` | マスキングロジック本体（メール・電話・JWT・パスワード系） |
| `PiiMaskingPatternLayoutEncoder` | dev プロファイル: テキストフォーマットログのマスキング |
| `PiiMaskingMessageJsonProvider` | prd プロファイル: LogstashEncoder の `message` フィールドへの適用 |
| `PiiMaskingStackTraceJsonProvider` | prd プロファイル: LogstashEncoder のスタックトレースへの適用 |

```java
package com.wms.shared.logging;

import java.util.regex.Pattern;

/**
 * ログメッセージ中のPII（個人情報）・機密情報をマスクするユーティリティ。
 * LogstashEncoder の MessageJsonProvider カスタマイズで自動適用される。
 */
public final class PiiMasker {

    private PiiMasker() {}

    // ReDoS対策: possessive quantifier(++) 使用
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile(
            "[a-zA-Z0-9._%+\\-]++@[a-zA-Z0-9\\-]++(?:\\.[a-zA-Z0-9\\-]++)*\\.[a-zA-Z]{2,}");

    // 先頭セグメント2桁以上に制限、ワードバウンダリ付き
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("\\b0[0-9]{1,3}-[0-9]{1,4}-[0-9]{3,4}\\b");

    // JWE(5パート)対応、Base64URLパディング(=)対応
    private static final Pattern JWT_PATTERN =
        Pattern.compile(
            "eyJ[a-zA-Z0-9_=-]+\\.[a-zA-Z0-9_=-]+\\.[a-zA-Z0-9_=-]+(?:\\.[a-zA-Z0-9_=-]+)*");

    // KV形式（key=value / key:value）、token キーワード含む
    // (?!\[JWT): JWT-REDACTED済みの値を再マスキングしない
    private static final Pattern PASSWORD_KV_PATTERN =
        Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token)(\\s*[=:]\\s*)(?!\\[JWT)\\S+");

    // JSON形式（"key": "value"）
    private static final Pattern PASSWORD_JSON_PATTERN =
        Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token)(\"\\s*:\\s*\")([^\"]*)\"");

    public static String mask(String message) {
        if (message == null) return null;
        if (!mayContainSensitiveData(message)) return message; // fast-path
        String masked = EMAIL_PATTERN.matcher(message).replaceAll("***@***.***");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("***-****-****");
        masked = JWT_PATTERN.matcher(masked).replaceAll("[JWT-REDACTED]");
        masked = PASSWORD_JSON_PATTERN.matcher(masked).replaceAll("$1$2*****\"");
        masked = PASSWORD_KV_PATTERN.matcher(masked).replaceAll("$1$2*****");
        return masked;
    }
}
```

### 4.5 センシティブデータのログマスキング

認証系APIのリクエストボディにはパスワードが平文で含まれるため、全てのログ出力経路でマスキングを行う。

#### センシティブエンドポイント一覧（SSOT）

> **この一覧がセンシティブエンドポイントの唯一の定義場所（SSOT）です。**
> 新たにパスワードや個人情報を平文で受け取るエンドポイントを追加した場合は、必ずこの一覧に追加してください。
> `SensitiveDataFilter` と `SensitiveDataTelemetryInitializer` はこの一覧を参照してマスキングを行います。

| パス | API ID | センシティブ項目 | 追加時期 |
|------|--------|-----------------|---------|
| `POST /api/v1/auth/login` | API-AUTH-001 | パスワード | 初期 |
| `POST /api/v1/auth/change-password` | API-AUTH-004 | 旧パスワード + 新パスワード | 初期 |
| `POST /api/v1/auth/password-reset/confirm` | API-AUTH-006 | 新パスワード | 初期 |

**運用ルール**: エンドポイント追加時に以下を確認する
1. リクエストボディにパスワード、トークン、シークレット等が含まれるか
2. 含まれる場合はこの一覧に追加し、`SENSITIVE_PATHS` 定数を更新する

#### 対策1: リクエストログフィルター（logback経路）

`SensitiveDataFilter` を `TraceIdFilter` の後段に配置し、認証系パスのリクエストボディをログ出力前にマスキングする。
アプリケーションログ（1）とエラーログ（3）の両方をカバーする。

```java
@Component
public class SensitiveDataFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/change-password",
        "/api/v1/auth/password-reset/confirm"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SENSITIVE_PATHS.contains(request.getRequestURI())) {
            // ContentCachingRequestWrapperを使用してボディをマスク
            // MDCにrequestBody=[MASKED]を設定
            MDC.put("requestBody", "[MASKED]");
        }
        chain.doFilter(request, response);
    }
}
```

#### 対策2: Application Insights テレメトリマスキング

`SensitiveDataTelemetryInitializer` で認証系APIのリクエストボディをテレメトリから除外する。

```java
@Component
public class SensitiveDataTelemetryInitializer implements TelemetryInitializer {

    private static final Set<String> SENSITIVE_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/change-password",
        "/api/v1/auth/password-reset/confirm"
    );

    @Override
    public void initialize(Telemetry telemetry) {
        if (telemetry instanceof RequestTelemetry requestTelemetry) {
            String url = requestTelemetry.getUrl().getPath();
            if (SENSITIVE_PATHS.stream().anyMatch(url::startsWith)) {
                requestTelemetry.getProperties().put("requestBody", "[MASKED]");
            }
        }
    }
}
```

### 4.6 バックエンド — ユーザーID の MDC 設定

JWT認証フィルターでユーザーIDをMDCに設定し、全ログに自動付与する。

```java
package com.wms.shared.security;

// JwtAuthenticationFilter 内の一部（認証成功後）
MDC.put("userId", String.valueOf(userId));
// フィルターチェーン完了後に MDC.remove("userId")
```

### 4.7 フロントエンド — コンソールログ方針

| 環境 | ログレベル | 方針 |
|------|----------|------|
| 開発 | DEBUG | `console.log`, `console.warn`, `console.error` を自由に使用 |
| 本番 | ERROR のみ | Vite の `define` で `console.log` / `console.warn` を無効化 |

```typescript
// vite.config.ts
export default defineConfig({
  // ...
  esbuild: {
    drop: process.env.NODE_ENV === 'production'
      ? ['console', 'debugger']
      : [],
  },
})
```

---

## 5. 共通日時処理設計

### 5.1 設計方針

| 項目 | 方針 |
|------|------|
| **タイムゾーン** | Asia/Tokyo（JST）固定。UTC変換なし |
| **営業日** | `business_date` テーブルの単一レコードから都度取得（キャッシュなし） |
| **日付型** | `LocalDate`（営業日・予定日等） |
| **日時型** | `OffsetDateTime`（タイムスタンプ系。JST固定） |
| **JSON直列化** | ISO 8601 形式（`yyyy-MM-dd'T'HH:mm:ss+09:00`） |

### 5.2 バックエンド — JVM タイムゾーン設定

```java
package com.wms;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }

    @PostConstruct
    void setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
    }
}
```

### 5.3 バックエンド — Jackson 日時シリアライズ設定

```java
package com.wms.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule module = new JavaTimeModule();

        // LocalDate → "yyyy-MM-dd"
        module.addSerializer(java.time.LocalDate.class,
            new LocalDateSerializer(
                DateTimeFormatter.ISO_LOCAL_DATE));

        // OffsetDateTime → ISO 8601 with offset
        // デフォルトの OffsetDateTimeSerializer で
        // "2026-03-13T09:00:00+09:00" 形式になる

        mapper.registerModule(module);
        mapper.disable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
```

### 5.4 バックエンド — 営業日取得サービス

```java
package com.wms.system.service;

import com.wms.system.entity.BusinessDate;
import com.wms.system.repository.BusinessDateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class BusinessDateService {

    private final BusinessDateRepository repository;

    public BusinessDateService(
            BusinessDateRepository repository) {
        this.repository = repository;
    }

    /**
     * 現在の営業日を取得する。
     * キャッシュは使用しない（常にDBから取得）。
     */
    @Transactional(readOnly = true)
    public LocalDate getCurrentBusinessDate() {
        return repository.findFirst()
            .map(BusinessDate::getBusinessDate)
            .orElseThrow(() -> new IllegalStateException(
                "business_date テーブルにレコードが存在しません"));
    }
}
```

### 5.5 バックエンド — Entity での日時カラム定義パターン

```java
package com.wms.shared.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "created_at", nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    // getter / setter 省略 → 8章の AuditingListener で自動設定
}
```

### 5.6 フロントエンド — 日時フォーマットユーティリティ

```typescript
// src/utils/date.ts

/**
 * ISO 8601 文字列をフロントエンド表示用にフォーマットする。
 * タイムゾーンはJST前提のため変換は行わない。
 */

/** "2026-03-13" → "2026/03/13" */
export function formatDate(isoDate: string): string {
  return isoDate.replace(/-/g, '/')
}

/** "2026-03-13T09:00:00+09:00" → "2026/03/13 09:00" */
export function formatDateTime(isoDateTime: string): string {
  const dt = new Date(isoDateTime)
  const y = dt.getFullYear()
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const d = String(dt.getDate()).padStart(2, '0')
  const h = String(dt.getHours()).padStart(2, '0')
  const min = String(dt.getMinutes()).padStart(2, '0')
  return `${y}/${m}/${d} ${h}:${min}`
}

/** "2026-03-13T09:00:00+09:00" → "2026/03/13 09:00:00" */
export function formatDateTimeFull(isoDateTime: string): string {
  const dt = new Date(isoDateTime)
  const y = dt.getFullYear()
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const d = String(dt.getDate()).padStart(2, '0')
  const h = String(dt.getHours()).padStart(2, '0')
  const min = String(dt.getMinutes()).padStart(2, '0')
  const s = String(dt.getSeconds()).padStart(2, '0')
  return `${y}/${m}/${d} ${h}:${min}:${s}`
}
```

---

## 6. 共通コード管理設計

### 6.1 設計方針

| 種別 | 管理方式 | 変更可否 |
|------|---------|---------|
| **区分値（固定）** | Java Enum + TypeScript Enum | 開発者のみ変更可 |
| **システムパラメータ** | `system_parameters` テーブル | 管理画面から変更可 |
| **マスタデータ** | 各マスタテーブル | 業務画面から変更可 |

### 6.2 バックエンド — Enum 定義パターン

```java
package com.wms.shared.enums;

/**
 * 保管条件。
 * DBには文字列値（"AMBIENT" 等）で保存する。
 */
public enum StorageCondition {
    AMBIENT("常温"),
    REFRIGERATED("冷蔵"),
    FROZEN("冷凍");

    private final String displayName;

    StorageCondition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** 取引先種別 */
public enum PartnerType {
    SUPPLIER("仕入先"),
    CUSTOMER("出荷先"),
    BOTH("両方");

    private final String displayName;

    PartnerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** ユーザーロール */
public enum UserRole {
    SYSTEM_ADMIN("システム管理者"),
    WAREHOUSE_MANAGER("倉庫管理者"),
    WAREHOUSE_STAFF("倉庫スタッフ"),
    VIEWER("閲覧者");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** 荷姿 */
public enum UnitType {
    CASE("ケース"),
    BALL("ボール"),
    PIECE("バラ");

    private final String displayName;

    UnitType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** エリア種別 */
public enum AreaType {
    STOCK("在庫エリア"),
    INBOUND("入荷エリア"),
    OUTBOUND("出荷エリア"),
    RETURN("返品エリア");

    private final String displayName;

    AreaType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** 入荷伝票ステータス */
public enum InboundStatus {
    PLANNED("入荷予定"),
    CONFIRMED("入荷確認済"),
    INSPECTING("検品中"),
    PARTIAL_STORED("一部入庫"),
    STORED("入庫完了"),
    CANCELLED("キャンセル");

    private final String displayName;

    InboundStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** 出荷伝票ステータス */
public enum OutboundStatus {
    ORDERED("受注"),
    PARTIAL_ALLOCATED("一部引当"),
    ALLOCATED("引当完了（ピッキング指示済み）"),
    PICKING_COMPLETED("ピッキング完了"),
    INSPECTING("出荷検品中"),
    SHIPPED("出荷完了"),
    CANCELLED("キャンセル");

    private final String displayName;

    OutboundStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** 在庫変動種別 */
public enum MovementType {
    INBOUND("入庫"),
    OUTBOUND("出庫"),
    MOVE_OUT("移動元出庫"),
    MOVE_IN("移動先入庫"),
    BREAKDOWN_OUT("ばらし元出庫"),
    BREAKDOWN_IN("ばらし先入庫"),
    CORRECTION("在庫訂正"),
    STOCKTAKE_ADJUSTMENT("棚卸差異調整"),
    INBOUND_CANCEL("入荷キャンセル戻し"),
    RETURN_OUT("返品出庫");

    private final String displayName;

    MovementType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** 棚卸ステータス */
public enum StocktakeStatus {
    STARTED("棚卸中"),
    CONFIRMED("確定済");

    private final String displayName;

    StocktakeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

```java
package com.wms.shared.enums;

/** ばらし指示ステータス */
public enum UnpackInstructionStatus {
    INSTRUCTED("指示済"),
    COMPLETED("完了");

    private final String displayName;

    UnpackInstructionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### 6.3 バックエンド — JPA Enum マッピング

```java
// Entity 内での Enum マッピング
@Enumerated(EnumType.STRING)
@Column(name = "storage_condition", nullable = false)
private StorageCondition storageCondition;
```

### 6.4 フロントエンド — Enum / 定数定義

```typescript
// src/constants/enums.ts

export const StorageCondition = {
  AMBIENT: 'AMBIENT',
  REFRIGERATED: 'REFRIGERATED',
  FROZEN: 'FROZEN',
} as const

export type StorageCondition =
  typeof StorageCondition[keyof typeof StorageCondition]

export const StorageConditionLabel: Record<StorageCondition, string> = {
  AMBIENT: '常温',
  REFRIGERATED: '冷蔵',
  FROZEN: '冷凍',
}

export const PartnerType = {
  SUPPLIER: 'SUPPLIER',
  CUSTOMER: 'CUSTOMER',
  BOTH: 'BOTH',
} as const

export type PartnerType = typeof PartnerType[keyof typeof PartnerType]

export const PartnerTypeLabel: Record<PartnerType, string> = {
  SUPPLIER: '仕入先',
  CUSTOMER: '出荷先',
  BOTH: '両方',
}

export const UserRole = {
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  WAREHOUSE_MANAGER: 'WAREHOUSE_MANAGER',
  WAREHOUSE_STAFF: 'WAREHOUSE_STAFF',
  VIEWER: 'VIEWER',
} as const

export type UserRole = typeof UserRole[keyof typeof UserRole]

export const UserRoleLabel: Record<UserRole, string> = {
  SYSTEM_ADMIN: 'システム管理者',
  WAREHOUSE_MANAGER: '倉庫管理者',
  WAREHOUSE_STAFF: '倉庫スタッフ',
  VIEWER: '閲覧者',
}

export const UnitType = {
  CASE: 'CASE',
  BALL: 'BALL',
  PIECE: 'PIECE',
} as const

export type UnitType = typeof UnitType[keyof typeof UnitType]

export const UnitTypeLabel: Record<UnitType, string> = {
  CASE: 'ケース',
  BALL: 'ボール',
  PIECE: 'バラ',
}

export const AreaType = {
  STOCK: 'STOCK',
  INBOUND: 'INBOUND',
  OUTBOUND: 'OUTBOUND',
  RETURN: 'RETURN',
} as const

export type AreaType = typeof AreaType[keyof typeof AreaType]

export const AreaTypeLabel: Record<AreaType, string> = {
  STOCK: '在庫エリア',
  INBOUND: '入荷エリア',
  OUTBOUND: '出荷エリア',
  RETURN: '返品エリア',
}

export const InboundStatus = {
  PLANNED: 'PLANNED',
  CONFIRMED: 'CONFIRMED',
  INSPECTING: 'INSPECTING',
  PARTIAL_STORED: 'PARTIAL_STORED',
  STORED: 'STORED',
  CANCELLED: 'CANCELLED',
} as const

export type InboundStatus =
  typeof InboundStatus[keyof typeof InboundStatus]

export const InboundStatusLabel: Record<InboundStatus, string> = {
  PLANNED: '入荷予定',
  CONFIRMED: '入荷確認済',
  INSPECTING: '検品中',
  PARTIAL_STORED: '一部入庫',
  STORED: '入庫完了',
  CANCELLED: 'キャンセル',
}

export const OutboundStatus = {
  ORDERED: 'ORDERED',
  PARTIAL_ALLOCATED: 'PARTIAL_ALLOCATED',
  ALLOCATED: 'ALLOCATED',
  PICKING_COMPLETED: 'PICKING_COMPLETED',
  INSPECTING: 'INSPECTING',
  SHIPPED: 'SHIPPED',
  CANCELLED: 'CANCELLED',
} as const

export type OutboundStatus =
  typeof OutboundStatus[keyof typeof OutboundStatus]

export const OutboundStatusLabel: Record<OutboundStatus, string> = {
  ORDERED: '受注',
  PARTIAL_ALLOCATED: '一部引当',
  ALLOCATED: '引当完了（ピッキング指示済み）',
  PICKING_COMPLETED: 'ピッキング完了',
  INSPECTING: '出荷検品中',
  SHIPPED: '出荷完了',
  CANCELLED: 'キャンセル',
}

export const MovementType = {
  INBOUND: 'INBOUND',
  OUTBOUND: 'OUTBOUND',
  MOVE_OUT: 'MOVE_OUT',
  MOVE_IN: 'MOVE_IN',
  BREAKDOWN_OUT: 'BREAKDOWN_OUT',
  BREAKDOWN_IN: 'BREAKDOWN_IN',
  CORRECTION: 'CORRECTION',
  STOCKTAKE_ADJUSTMENT: 'STOCKTAKE_ADJUSTMENT',
  INBOUND_CANCEL: 'INBOUND_CANCEL',
  RETURN_OUT: 'RETURN_OUT',
} as const

export type MovementType =
  typeof MovementType[keyof typeof MovementType]

export const MovementTypeLabel: Record<MovementType, string> = {
  INBOUND: '入庫',
  OUTBOUND: '出庫',
  MOVE_OUT: '移動元出庫',
  MOVE_IN: '移動先入庫',
  BREAKDOWN_OUT: 'ばらし元出庫',
  BREAKDOWN_IN: 'ばらし先入庫',
  CORRECTION: '在庫訂正',
  STOCKTAKE_ADJUSTMENT: '棚卸差異調整',
  INBOUND_CANCEL: '入荷キャンセル戻し',
  RETURN_OUT: '返品出庫',
}

export const StocktakeStatus = {
  STARTED: 'STARTED',
  CONFIRMED: 'CONFIRMED',
} as const

export type StocktakeStatus =
  typeof StocktakeStatus[keyof typeof StocktakeStatus]

export const StocktakeStatusLabel: Record<StocktakeStatus, string> = {
  STARTED: '棚卸中',
  CONFIRMED: '確定済',
}

export const UnpackInstructionStatus = {
  INSTRUCTED: 'INSTRUCTED',
  COMPLETED: 'COMPLETED',
} as const

export type UnpackInstructionStatus =
  typeof UnpackInstructionStatus[keyof typeof UnpackInstructionStatus]

export const UnpackInstructionStatusLabel: Record<UnpackInstructionStatus, string> = {
  INSTRUCTED: '指示済',
  COMPLETED: '完了',
}
```

### 6.5 フロントエンド — Enum をセレクトボックスに変換するヘルパー

```typescript
// src/utils/enum-helper.ts

export interface SelectOption {
  value: string
  label: string
}

/**
 * ラベルマップを Element Plus の el-select 用オプション配列に変換する。
 */
export function toSelectOptions(
  labelMap: Record<string, string>,
): SelectOption[] {
  return Object.entries(labelMap).map(([value, label]) => ({
    value,
    label,
  }))
}

// 使用例:
// const storageOptions = toSelectOptions(StorageConditionLabel)
// → [{ value: 'AMBIENT', label: '常温' }, ...]
```

---

## 7. 共通メッセージ管理設計

### 7.1 バックエンド — メッセージ管理

バックエンドのエラーメッセージは例外クラス生成時に直接指定する（メッセージファイル分離は行わない）。

理由:
- メッセージは業務コンテキストに密結合であり、例外スロー箇所での記述が可読性に優れる
- バックエンドのレスポンスメッセージは i18n 不要（フロントエンドで表示言語を制御する）
- メッセージファイル管理のオーバーヘッドを回避する

```java
// Service 層での使用例
throw new ResourceNotFoundException(
    "INBOUND_SLIP_NOT_FOUND",
    "入荷伝票が見つかりません");

throw new BusinessRuleViolationException(
    "CANNOT_DEACTIVATE_HAS_INVENTORY",
    "在庫があるため無効化できません");

throw new InvalidStateTransitionException(
    "INBOUND_INVALID_STATUS",
    "現在のステータスではその操作はできません");
```

### 7.2 フロントエンド — vue-i18n メッセージ管理

#### ディレクトリ構成

```
src/locales/
├── ja.json     # 日本語メッセージ
└── en.json     # 英語メッセージ
```

#### メッセージキー体系

```
{モジュール}.{画面/機能}.{カテゴリ}.{キー}
```

```json
// src/locales/ja.json
{
  "common": {
    "button": {
      "search": "検索",
      "clear": "クリア",
      "create": "新規登録",
      "save": "保存",
      "cancel": "キャンセル",
      "delete": "削除",
      "edit": "編集",
      "back": "戻る",
      "close": "閉じる",
      "download": "ダウンロード"
    },
    "label": {
      "active": "有効",
      "inactive": "無効",
      "all": "全て",
      "businessDate": "営業日",
      "totalCount": "{count}件"
    },
    "message": {
      "confirmDelete": "削除してよろしいですか？",
      "confirmDeactivate": "無効化してよろしいですか？",
      "confirmActivate": "有効化してよろしいですか？",
      "saved": "保存しました",
      "deleted": "削除しました",
      "deactivated": "無効化しました",
      "activated": "有効化しました",
      "sessionExpired": "セッションがタイムアウトしました",
      "loggedOut": "ログアウトしました",
      "forbidden": "この操作を実行する権限がありません",
      "serverError": "システムエラーが発生しました",
      "optimisticLock": "他のユーザーが更新済みです。画面を再読み込みしてください",
      "validationError": "入力内容にエラーがあります",
      "networkError": "通信エラーが発生しました"
    },
    "validation": {
      "required": "{field}は必須です",
      "maxLength": "{field}は{max}文字以内で入力してください",
      "minValue": "{field}は{min}以上で入力してください",
      "maxValue": "{field}は{max}以下で入力してください",
      "invalidFormat": "{field}の形式が正しくありません"
    },
    "pagination": {
      "total": "全{total}件",
      "perPage": "{size}件/ページ"
    }
  },
  "master": {
    "warehouse": {
      "title": "倉庫管理",
      "list": "倉庫一覧",
      "create": "倉庫登録",
      "edit": "倉庫編集",
      "field": {
        "warehouseCode": "倉庫コード",
        "warehouseName": "倉庫名",
        "warehouseNameKana": "倉庫名カナ",
        "address": "住所"
      }
    }
  },
  "inbound": {
    "slip": {
      "title": "入荷管理",
      "list": "入荷予定一覧"
    },
    "status": {
      "PLANNED": "入荷予定",
      "CONFIRMED": "入荷確認済",
      "INSPECTING": "検品中",
      "PARTIAL_STORED": "一部入庫",
      "STORED": "入庫完了",
      "CANCELLED": "キャンセル"
    }
  },
  "outbound": {
    "status": {
      "ORDERED": "受注",
      "PARTIAL_ALLOCATED": "一部引当",
      "ALLOCATED": "引当完了（ピッキング指示済み）",
      "PICKING_COMPLETED": "ピッキング完了",
      "INSPECTING": "出荷検品中",
      "SHIPPED": "出荷完了",
      "CANCELLED": "キャンセル"
    }
  },
  "inventory": {
    "movementType": {
      "INBOUND": "入庫",
      "OUTBOUND": "出庫",
      "MOVE_OUT": "移動元出庫",
      "MOVE_IN": "移動先入庫",
      "BREAKDOWN_OUT": "ばらし元出庫",
      "BREAKDOWN_IN": "ばらし先入庫",
      "CORRECTION": "在庫訂正",
      "STOCKTAKE_ADJUSTMENT": "棚卸差異調整",
      "INBOUND_CANCEL": "入荷キャンセル戻し",
      "RETURN_OUT": "返品出庫"
    },
    "stocktakeStatus": {
      "STARTED": "棚卸中",
      "CONFIRMED": "確定済"
    }
  }
}
```

#### vue-i18n 初期化

```typescript
// src/plugins/i18n.ts
import { createI18n } from 'vue-i18n'
import ja from '@/locales/ja.json'
import en from '@/locales/en.json'

const i18n = createI18n({
  legacy: false,           // Composition API モード
  locale: 'ja',            // デフォルト言語
  fallbackLocale: 'ja',
  messages: { ja, en },
})

export default i18n
```

#### テンプレートでの使用例

```vue
<template>
  <el-button type="primary" @click="onSearch">
    {{ $t('common.button.search') }}
  </el-button>

  <span>{{ $t('common.pagination.total', { total: totalElements }) }}</span>
</template>
```

---

## 8. 監査証跡設計

### 8.1 設計方針

> 共通カラムパターンは [data-model/01-overview.md](../data-model/01-overview.md) を参照。

| カラム | 設定タイミング | 設定値 |
|--------|-------------|--------|
| `created_at` | INSERT 時のみ | 現在日時（JSTの `OffsetDateTime.now()`） |
| `created_by` | INSERT 時のみ | 認証済みユーザーのID（SecurityContext から取得） |
| `updated_at` | INSERT / UPDATE 時 | 現在日時 |
| `updated_by` | INSERT / UPDATE 時 | 認証済みユーザーのID |

### 8.2 バックエンド — Spring Data JPA Auditing

```java
package com.wms.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public org.springframework.data.domain.AuditorAware<Long>
            auditorProvider() {
        return new SecurityAuditorAware();
    }
}
```

```java
package com.wms.shared.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * SecurityContext からログインユーザーIDを取得する。
 * APIリクエスト経由のバッチ処理では、実行者のユーザーIDが自動取得される。
 * 初期データ投入時・未認証時は SYSTEM_USER_ID（0）を使用する。
 * 将来のスケジューラー自動実行時も SYSTEM_USER_ID を使用する（8.6 節参照）。
 */
public class SecurityAuditorAware
        implements AuditorAware<Long> {

    private static final Long SYSTEM_USER_ID = 0L;

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder
            .getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.of(SYSTEM_USER_ID);
        }

        // WmsUserDetails からユーザーIDを取得
        if (auth.getPrincipal() instanceof WmsUserDetails details) {
            return Optional.of(details.getUserId());
        }

        return Optional.of(SYSTEM_USER_ID);
    }
}
```

### 8.3 バックエンド — 基底 Entity クラス

```java
package com.wms.shared.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    // --- Getter ---
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
}
```

### 8.4 バックエンド — マスタ Entity 基底クラス

```java
package com.wms.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * マスタテーブル共通の基底クラス。
 * BaseEntity（監査カラム）+ is_active フラグ + 楽観的ロック。
 */
@MappedSuperclass
public abstract class BaseMasterEntity extends BaseEntity {

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Integer getVersion() { return version; }
}
```

### 8.5 バックエンド — 楽観的ロック処理

```java
// Service 層での楽観的ロック処理パターン
@Transactional
public WarehouseResponse update(
        Long id, UpdateWarehouseRequest request) {
    Warehouse warehouse = warehouseRepository.findById(id)
        .orElseThrow(() ->
            ResourceNotFoundException.of(
                "WAREHOUSE_NOT_FOUND", "倉庫", id));

    // クライアントが送信した version と DB の version を比較
    if (!warehouse.getVersion().equals(request.version())) {
        throw new OptimisticLockConflictException();
    }

    // 更新処理
    warehouse.setWarehouseName(request.warehouseName());
    warehouse.setWarehouseNameKana(request.warehouseNameKana());
    warehouse.setAddress(request.address());

    // save 時に @Version が自動インクリメントされる
    Warehouse saved = warehouseRepository.save(warehouse);
    return WarehouseResponse.from(saved);
}
```

### 8.6 バッチ処理の SecurityContext 設定方針

バッチ処理（日替処理等）は APIリクエスト経由で実行されるため、リクエスト時の JWT からユーザーIDを取得し、SecurityContext に設定する。
バッチ処理内で作成・更新されるレコードの `created_by` / `updated_by` には、実行者のユーザーIDが記録される。

| 実行方式 | SecurityContext の設定 | `created_by` / `updated_by` の値 |
|---------|----------------------|----------------------------------|
| **APIリクエスト経由**（画面からの手動実行） | JWT認証フィルターが自動設定 | リクエスト実行者のユーザーID |
| **スケジューラーによる自動実行**（将来対応） | 明示的に `SYSTEM_USER_ID`（= `0`）を設定 | `0`（システムユーザー） |

**APIリクエスト経由の場合:**

通常のAPI呼び出しと同様に、`JwtAuthenticationFilter` が SecurityContext にユーザー情報を設定する。
バッチ処理の Service メソッドは通常の `@Transactional` メソッドとして実行されるため、
`SecurityAuditorAware`（8.2 節参照）が SecurityContext からユーザーIDを自動取得する。
特別な追加実装は不要である。

```java
// バッチ処理 Controller の例
@PostMapping("/api/v1/batch/day-end")
@PreAuthorize("hasRole('SYSTEM_ADMIN') or hasRole('WAREHOUSE_MANAGER')")
public ResponseEntity<BatchResultResponse> executeDayEnd() {
    // SecurityContext には JWT 認証フィルターにより実行者情報が設定済み
    // バッチ処理内の全レコードの created_by / updated_by に
    // 実行者のユーザーIDが自動設定される
    BatchResultResponse result = batchService.executeDayEnd();
    return ResponseEntity.ok(result);
}
```

**将来的な自動実行への備え:**

スケジューラー（Spring `@Scheduled` 等）による自動実行を導入する場合は、
HTTPリクエストコンテキストが存在しないため、明示的に SecurityContext を設定する必要がある。
この場合は `SYSTEM_USER_ID`（= `0`）を使用する。

```java
// 将来のスケジューラー実行時のSecurityContext設定例
@Scheduled(cron = "0 0 0 * * *")
public void scheduledDayEnd() {
    // SecurityContext に SYSTEM_USER を設定
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            WmsUserDetails.systemUser(), null,
            List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))
        )
    );
    try {
        batchService.executeDayEnd();
    } finally {
        SecurityContextHolder.clearContext();
    }
}
```

> **注記:** 現時点ではバッチ処理はすべて APIリクエスト経由での手動実行とする。
> スケジューラーによる自動実行は将来要件として、導入時に上記パターンを適用する。

### 8.7 システムパラメータ取得方針

`SystemParameterService` はシステムパラメータを**都度DBから取得する（キャッシュしない）**。

**方針:**

| 項目 | 内容 |
|------|------|
| **キャッシュ** | 使用しない |
| **理由** | パラメータ変更の即時反映を保証するため。ShowCase規模ではDB負荷は問題にならない |
| **将来対応** | パフォーマンスが問題になる場合は、短いTTL（例: 5分）のキャッシュ（Spring Cache + Caffeine等）を導入する |

> システムパラメータのテーブル定義・初期データは [data-model/02-master-tables.md](../data-model/02-master-tables.md) を参照。

**実装例:**

```java
package com.wms.system.service;

import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.repository.SystemParameterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemParameterService {

    private final SystemParameterRepository repository;

    /**
     * システムパラメータの値を取得する。
     * キャッシュなし — 都度DB取得で即時反映を保証する。
     */
    public String getValue(String key) {
        return repository.findByParameterKey(key)
            .orElseThrow(() -> new ResourceNotFoundException(
                "SYSTEM_PARAMETER_NOT_FOUND",
                "システムパラメータが見つかりません (key=" + key + ")"))
            .getParameterValue();
    }

    /** int型として取得する */
    public int getIntValue(String key) {
        return Integer.parseInt(getValue(key));
    }

    /** boolean型として取得する */
    public boolean getBooleanValue(String key) {
        return Boolean.parseBoolean(getValue(key));
    }
}
```

---

## 9. 共通ユーティリティ設計

### 9.1 バックエンド — パッケージ構成

```
com.wms.shared/
├── config/           # Spring 設定
│   ├── CorsConfig.java
│   ├── JacksonConfig.java
│   ├── JpaAuditingConfig.java
│   ├── PaginationConfig.java
│   └── SecurityAuditorAware.java
├── dto/              # 共通DTO
│   ├── ErrorResponse.java
│   └── PageResponse.java
├── entity/           # 共通基底Entity
│   ├── BaseEntity.java
│   └── BaseMasterEntity.java
├── enums/            # 共通Enum
│   ├── StorageCondition.java
│   ├── PartnerType.java
│   ├── UserRole.java
│   ├── UnitType.java
│   ├── AreaType.java
│   ├── InboundStatus.java
│   ├── OutboundStatus.java
│   ├── MovementType.java
│   ├── StocktakeStatus.java
│   └── UnpackInstructionStatus.java
├── exception/        # 例外クラス
│   ├── WmsException.java
│   ├── ResourceNotFoundException.java
│   ├── DuplicateResourceException.java
│   ├── BusinessRuleViolationException.java
│   ├── OptimisticLockConflictException.java
│   ├── InvalidStateTransitionException.java
│   └── GlobalExceptionHandler.java
├── logging/          # ログ関連
│   ├── TraceIdFilter.java
│   ├── TraceContext.java
│   ├── PiiMasker.java
│   ├── PiiMaskingPatternLayoutEncoder.java
│   ├── PiiMaskingMessageJsonProvider.java
│   ├── PiiMaskingStackTraceJsonProvider.java
│   └── ServiceLoggingAspect.java
├── security/         # JWT 認証
│   └── ...（認証設計書で定義）
├── validation/       # カスタムバリデーション
│   ├── PasswordPolicy.java
│   └── PasswordPolicyValidator.java
└── util/             # ユーティリティ
```

### 9.2 フロントエンド — ディレクトリ構成

```
src/
├── composables/
│   └── shared/                   # 共通 Composable
│       ├── useApiErrorHandler.ts # エラー処理
│       └── usePagination.ts      # ページネーション
├── constants/
│   └── enums.ts                  # Enum / 定数
├── locales/
│   ├── ja.json                   # 日本語メッセージ
│   └── en.json                   # 英語メッセージ
├── plugins/
│   └── i18n.ts                   # vue-i18n 設定
├── types/
│   ├── common.ts                 # 共通型定義
│   └── generated/                # OpenAPI 自動生成型
└── utils/
    ├── api.ts                    # Axios インスタンス
    ├── date.ts                   # 日時フォーマット
    └── enum-helper.ts            # Enum ヘルパー
```

### 9.3 バックエンド — CORS設定

```java
package com.wms.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${wms.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

### 9.4 バックエンド — OpenAPI (Springdoc) 設定

```java
package com.wms.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI wmsOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("WMS API")
                .description("倉庫管理システム API")
                .version("v1"));
    }
}
```

### 9.5 バックエンド — JSON共通設定

```java
package com.wms.shared.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
            // null フィールドはレスポンスに含めない
            .serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
```

### 9.6 バックエンド — トランザクション管理方針

| 項目 | 方針 |
|------|------|
| **アノテーション** | `@Transactional` を Service 層の public メソッドに付与 |
| **読み取り専用** | 参照系メソッドには `@Transactional(readOnly = true)` |
| **伝播** | デフォルト（`REQUIRED`）。モジュール間 Service 呼び出しは呼び出し元のトランザクションに参加 |
| **ロールバック** | RuntimeException でロールバック（Spring デフォルト） |

```java
// Service 層の使用例
@Service
public class WarehouseService {

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> findAll(
            WarehouseSearchCriteria criteria,
            Pageable pageable) {
        // ...
    }

    @Transactional
    public WarehouseResponse create(
            CreateWarehouseRequest request) {
        // ...
    }
}
```

### 9.7 バックエンド — application.yml 共通設定

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false       # Controller 外で LazyLoad しない
    hibernate:
      ddl-auto: validate      # Flyway 管理のため validate のみ
    properties:
      hibernate:
        default_schema: public
        jdbc:
          time_zone: Asia/Tokyo
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  servlet:
    encoding:
      charset: UTF-8
      force: true

wms:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

---

## 付録: 設計判断記録

| 判断項目 | 採用案 | 不採用案 | 理由 |
|---------|-------|---------|------|
| Entity-DTO 変換 | 手動マッピング | MapStruct | フィールド数が限定的、可読性優先 |
| バックエンドメッセージ管理 | 例外生成時に直接指定 | メッセージプロパティファイル | 業務コンテキストとの密結合、i18n不要 |
| フロントエンドバリデーション | VeeValidate + Zod | 独自バリデーション | Element Plus との統合性、型安全性 |
| ログ形式 | JSON 構造化ログ | テキストログ | Log Analytics での検索・分析に適する |
| 営業日キャッシュ | なし（都度DB取得） | Spring Cache | 日替処理との整合性、キャッシュ無効化の複雑さ回避 |
| Enum管理 | Java Enum + TS const | DBテーブル | 固定値の型安全性、パフォーマンス |
| ページネーション | ページベース（Spring Data Pageable） | カーソルベース | WMS の一覧画面はページ番号指定が自然 |
