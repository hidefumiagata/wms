package com.wms.shared.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionClassesTest {

    @Nested
    @DisplayName("WmsException (tested via subclass)")
    class WmsExceptionTest {

        @Test
        @DisplayName("errorCodeとmessageが保持される")
        void constructor_withCodeAndMessage_setsFields() {
            ResourceNotFoundException ex = new ResourceNotFoundException("NOT_FOUND", "not found");

            assertThat(ex.getErrorCode()).isEqualTo("NOT_FOUND");
            assertThat(ex.getMessage()).isEqualTo("not found");
        }

        @Test
        @DisplayName("causeコンストラクタでcauseが保持される")
        void constructor_withCause_setsCauseAndFields() {
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
        void constructor_withCodeAndMessage_setsFields() {
            ResourceNotFoundException ex = new ResourceNotFoundException("ITEM_NOT_FOUND", "商品が見つかりません");

            assertThat(ex.getErrorCode()).isEqualTo("ITEM_NOT_FOUND");
            assertThat(ex.getMessage()).isEqualTo("商品が見つかりません");
        }

        @Test
        @DisplayName("of(): リソース名のみのメッセージが生成される (内部IDは埋め込まない: OWASP A09)")
        void of_withResourceNameAndLongId_createsFormattedMessageWithoutId() {
            ResourceNotFoundException ex = ResourceNotFoundException.of("WAREHOUSE_NOT_FOUND", "倉庫", 42L);

            assertThat(ex.getErrorCode()).isEqualTo("WAREHOUSE_NOT_FOUND");
            assertThat(ex.getMessage()).isEqualTo("倉庫 が見つかりません");
            assertThat(ex.getMessage()).doesNotContain("id=");
            assertThat(ex.getMessage()).doesNotContain("42");
        }

        @Test
        @DisplayName("of(): 文字列IDでもメッセージに埋め込まれない (OWASP A09)")
        void of_withStringId_createsFormattedMessageWithoutId() {
            ResourceNotFoundException ex = ResourceNotFoundException.of("USER_NOT_FOUND", "ユーザー", "admin");

            assertThat(ex.getMessage()).isEqualTo("ユーザー が見つかりません");
            assertThat(ex.getMessage()).doesNotContain("admin");
            assertThat(ex.getMessage()).doesNotContain("id=");
        }

        @Nested
        @DisplayName("of() 副作用ログ検証 (仕様: ファクトリ内部で log.info を出力)")
        class OfSideEffectLogTest {

            private Logger factoryLogger;
            private ListAppender<ILoggingEvent> appender;

            @BeforeEach
            void setUp() {
                factoryLogger = (Logger) LoggerFactory.getLogger(ResourceNotFoundException.class);
                appender = new ListAppender<>();
                appender.start();
                factoryLogger.addAppender(appender);
            }

            @AfterEach
            void tearDown() {
                factoryLogger.detachAppender(appender);
                appender.stop();
            }

            @Test
            @DisplayName("of(): INFO レベルで 'resourceName not found: id=<id>, errorCode=<code>' が出力される")
            void of_emitsInfoLogWithIdAndErrorCode() {
                ResourceNotFoundException.of("WAREHOUSE_NOT_FOUND", "倉庫", 42L);

                assertThat(appender.list)
                        .as("ファクトリ呼び出しで 1 件の INFO ログが出力される")
                        .hasSize(1);
                ILoggingEvent event = appender.list.get(0);
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                // SLF4J のパラメータ付きメッセージはフォーマット済み文字列を getFormattedMessage() で取得
                assertThat(event.getFormattedMessage())
                        .isEqualTo("倉庫 not found: id=42, errorCode=WAREHOUSE_NOT_FOUND");
            }

            @Test
            @DisplayName("of(): 文字列IDでもログには id が出力される (メッセージには埋め込まれない)")
            void of_withStringId_emitsInfoLogWithStringId() {
                ResourceNotFoundException.of("USER_NOT_FOUND", "ユーザー", "admin");

                assertThat(appender.list).hasSize(1);
                assertThat(appender.list.get(0).getFormattedMessage())
                        .isEqualTo("ユーザー not found: id=admin, errorCode=USER_NOT_FOUND");
            }
        }
    }

    @Nested
    @DisplayName("InvalidStateTransitionException")
    class InvalidStateTransitionExceptionTest {

        @Test
        @DisplayName("コンストラクタ: errorCodeとmessageが設定される")
        void constructor_withCodeAndMessage_setsFields() {
            InvalidStateTransitionException ex =
                    new InvalidStateTransitionException("INVALID_TRANSITION", "遷移エラー");

            assertThat(ex.getErrorCode()).isEqualTo("INVALID_TRANSITION");
            assertThat(ex.getMessage()).isEqualTo("遷移エラー");
        }

        @Test
        @DisplayName("of(): ステータス遷移メッセージが生成される")
        void of_withFromAndToStatus_createsFormattedMessage() {
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
        @DisplayName("パラメータ付きコンストラクタ (@Deprecated): カスタムのcodeとmessageが設定される")
        @SuppressWarnings("deprecation")
        void constructor_withCustomValues_setsFields() {
            OptimisticLockConflictException ex =
                    new OptimisticLockConflictException("CUSTOM_LOCK", "カスタムメッセージ");

            assertThat(ex.getErrorCode()).isEqualTo("CUSTOM_LOCK");
            assertThat(ex.getMessage()).isEqualTo("カスタムメッセージ");
        }

        @Test
        @DisplayName("standard(): 全Service共通のcode/messageが設定される (内部IDは含まない: OWASP A09)")
        void standard_createsUnifiedException() {
            OptimisticLockConflictException ex = OptimisticLockConflictException.standard();

            assertThat(ex.getErrorCode()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
            assertThat(ex.getMessage()).isEqualTo("他のユーザーによる更新が先行しました");
            assertThat(ex.getMessage()).doesNotContain("id=");
        }
    }

    @Nested
    @DisplayName("DuplicateResourceException")
    class DuplicateResourceExceptionTest {

        @Test
        @DisplayName("コンストラクタ: errorCodeとmessageが設定される")
        void constructor_withCodeAndMessage_setsFields() {
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
        void constructor_withCodeAndMessage_setsFields() {
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
        void constructor_default_setsDefaultMessage() {
            RateLimitExceededException ex = new RateLimitExceededException();

            assertThat(ex.getMessage()).contains("リクエスト回数の上限を超えました");
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }
}
