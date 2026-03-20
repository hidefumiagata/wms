package com.wms.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionClassesTest {

    @Nested
    @DisplayName("WmsException (tested via subclass)")
    class WmsExceptionTest {

        @Test
        @DisplayName("errorCodeとmessageが保持される")
        void constructor_setsErrorCodeAndMessage() {
            ResourceNotFoundException ex = new ResourceNotFoundException("NOT_FOUND", "not found");

            assertThat(ex.getErrorCode()).isEqualTo("NOT_FOUND");
            assertThat(ex.getMessage()).isEqualTo("not found");
        }

        @Test
        @DisplayName("causeコンストラクタでcauseが保持される")
        void causeConstructor_setsCause() {
            IllegalArgumentException cause = new IllegalArgumentException("root cause");
            // OptimisticLockConflictException uses the two-arg constructor, but to test cause
            // we use InvalidStateTransitionException via its constructor which delegates to WmsException(code, msg)
            // WmsException cause constructor is protected, so we test via a subclass that exposes it.
            // Since no subclass currently exposes the cause constructor directly, we verify via
            // the base constructor that the class hierarchy is correct.
            BusinessRuleViolationException ex = new BusinessRuleViolationException("CODE", "msg", cause);
            assertThat(ex.getErrorCode()).isEqualTo("CODE");
            assertThat(ex.getMessage()).isEqualTo("msg");
            assertThat(ex.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("ResourceNotFoundException")
    class ResourceNotFoundExceptionTest {

        @Test
        @DisplayName("コンストラクタ: errorCodeとmessageが設定される")
        void constructor_setsFields() {
            ResourceNotFoundException ex = new ResourceNotFoundException("ITEM_NOT_FOUND", "商品が見つかりません");

            assertThat(ex.getErrorCode()).isEqualTo("ITEM_NOT_FOUND");
            assertThat(ex.getMessage()).isEqualTo("商品が見つかりません");
        }

        @Test
        @DisplayName("of(): リソース名とIDでメッセージが生成される")
        void of_createsFormattedMessage() {
            ResourceNotFoundException ex = ResourceNotFoundException.of("WAREHOUSE_NOT_FOUND", "倉庫", 42L);

            assertThat(ex.getErrorCode()).isEqualTo("WAREHOUSE_NOT_FOUND");
            assertThat(ex.getMessage()).isEqualTo("倉庫 が見つかりません (id=42)");
        }

        @Test
        @DisplayName("of(): 文字列IDでも正しくフォーマットされる")
        void of_worksWithStringId() {
            ResourceNotFoundException ex = ResourceNotFoundException.of("USER_NOT_FOUND", "ユーザー", "admin");

            assertThat(ex.getMessage()).isEqualTo("ユーザー が見つかりません (id=admin)");
        }
    }

    @Nested
    @DisplayName("InvalidStateTransitionException")
    class InvalidStateTransitionExceptionTest {

        @Test
        @DisplayName("コンストラクタ: errorCodeとmessageが設定される")
        void constructor_setsFields() {
            InvalidStateTransitionException ex =
                    new InvalidStateTransitionException("INVALID_TRANSITION", "遷移エラー");

            assertThat(ex.getErrorCode()).isEqualTo("INVALID_TRANSITION");
            assertThat(ex.getMessage()).isEqualTo("遷移エラー");
        }

        @Test
        @DisplayName("of(): ステータス遷移メッセージが生成される")
        void of_createsFormattedMessage() {
            InvalidStateTransitionException ex =
                    InvalidStateTransitionException.of("ORDER_STATUS_ERROR", "DRAFT", "SHIPPED");

            assertThat(ex.getErrorCode()).isEqualTo("ORDER_STATUS_ERROR");
            assertThat(ex.getMessage()).contains("DRAFT");
            assertThat(ex.getMessage()).contains("SHIPPED");
            assertThat(ex.getMessage()).isEqualTo("現在のステータス「DRAFT」から「SHIPPED」への遷移はできません");
        }
    }

    @Nested
    @DisplayName("OptimisticLockConflictException")
    class OptimisticLockConflictExceptionTest {

        @Test
        @DisplayName("デフォルトコンストラクタ: デフォルトのcodeとmessageが設定される")
        void defaultConstructor_setsDefaults() {
            OptimisticLockConflictException ex = new OptimisticLockConflictException();

            assertThat(ex.getErrorCode()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
            assertThat(ex.getMessage()).isEqualTo("他のユーザーが更新済みです。画面を再読み込みしてください");
        }

        @Test
        @DisplayName("パラメータ付きコンストラクタ: カスタムのcodeとmessageが設定される")
        void parameterizedConstructor_setsCustomValues() {
            OptimisticLockConflictException ex =
                    new OptimisticLockConflictException("CUSTOM_LOCK", "カスタムメッセージ");

            assertThat(ex.getErrorCode()).isEqualTo("CUSTOM_LOCK");
            assertThat(ex.getMessage()).isEqualTo("カスタムメッセージ");
        }
    }

    @Nested
    @DisplayName("DuplicateResourceException")
    class DuplicateResourceExceptionTest {

        @Test
        @DisplayName("コンストラクタ: errorCodeとmessageが設定される")
        void constructor_setsFields() {
            DuplicateResourceException ex =
                    new DuplicateResourceException("DUPLICATE_CODE", "コードが重複しています");

            assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_CODE");
            assertThat(ex.getMessage()).isEqualTo("コードが重複しています");
        }
    }

    @Nested
    @DisplayName("BusinessRuleViolationException")
    class BusinessRuleViolationExceptionTest {

        @Test
        @DisplayName("コンストラクタ: errorCodeとmessageが設定される")
        void constructor_setsFields() {
            BusinessRuleViolationException ex =
                    new BusinessRuleViolationException("INSUFFICIENT_STOCK", "在庫が不足しています");

            assertThat(ex.getErrorCode()).isEqualTo("INSUFFICIENT_STOCK");
            assertThat(ex.getMessage()).isEqualTo("在庫が不足しています");
        }
    }

    @Nested
    @DisplayName("RateLimitExceededException")
    class RateLimitExceededExceptionTest {

        @Test
        @DisplayName("デフォルトコンストラクタ: デフォルトメッセージが設定される")
        void defaultConstructor_setsDefaultMessage() {
            RateLimitExceededException ex = new RateLimitExceededException();

            assertThat(ex.getMessage()).contains("リクエスト回数の上限を超えました");
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }
}
