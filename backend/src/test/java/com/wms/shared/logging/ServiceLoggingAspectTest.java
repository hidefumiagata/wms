package com.wms.shared.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceLoggingAspect: サービス層AOP計時ログ")
class ServiceLoggingAspectTest {

    private ServiceLoggingAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    @BeforeEach
    void setUp() {
        aspect = new ServiceLoggingAspect();
        MDC.clear();
    }

    static class FakeService {
    }

    @Test
    @DisplayName("正常系: 戻り値を返しMDCがクリーンアップされる")
    void logServiceMethod_success_returnsResultAndCleansMdc() throws Throwable {
        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("doSomething");
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.logServiceMethod(joinPoint);

        assertThat(result).isEqualTo("result");
        assertThat(MDC.get("module")).isNull();
    }

    @Test
    @DisplayName("異常系: Exception発生時に再スローしMDCがクリーンアップされる")
    void logServiceMethod_exception_rethrowsAndCleansMdc() throws Throwable {
        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("failMethod");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test error"));

        assertThatThrownBy(() -> aspect.logServiceMethod(joinPoint))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("test error");

        assertThat(MDC.get("module")).isNull();
    }

    @Test
    @DisplayName("異常系: Throwable(Error)発生時にも再スローしMDCがクリーンアップされる")
    void logServiceMethod_throwable_rethrowsAndCleansMdc() throws Throwable {
        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("errorMethod");
        when(joinPoint.proceed()).thenThrow(new OutOfMemoryError("heap full"));

        assertThatThrownBy(() -> aspect.logServiceMethod(joinPoint))
            .isInstanceOf(OutOfMemoryError.class)
            .hasMessage("heap full");

        assertThat(MDC.get("module")).isNull();
    }

    @Test
    @DisplayName("正常系: null戻り値でも正常に処理される")
    void logServiceMethod_nullReturn_returnsNull() throws Throwable {
        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("voidMethod");
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.logServiceMethod(joinPoint);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("MDCネスト: 外側のmodule値がネスト呼び出し後に復元される")
    void logServiceMethod_nestedCall_restoresPreviousModule() throws Throwable {
        MDC.put("module", "outer");

        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("innerMethod");
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.logServiceMethod(joinPoint);

        assertThat(MDC.get("module")).isEqualTo("outer");
    }

    @Test
    @DisplayName("MDCネスト: 外側にmoduleがない場合は呼び出し後にnullに戻る")
    void logServiceMethod_noOuterModule_removesModule() throws Throwable {
        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("method");
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.logServiceMethod(joinPoint);

        assertThat(MDC.get("module")).isNull();
    }

    @Test
    @DisplayName("extractModule: 標準パッケージからモジュール名を抽出する")
    void extractModule_standardPackage_returnsModuleName() {
        assertThat(aspect.extractModule("com.wms.inbound.service"))
            .isEqualTo("inbound");
    }

    @Test
    @DisplayName("extractModule: sharedパッケージ")
    void extractModule_sharedPackage_returnsShared() {
        assertThat(aspect.extractModule("com.wms.shared.service"))
            .isEqualTo("shared");
    }

    @Test
    @DisplayName("extractModule: 短いパッケージ（2セグメント）はunknown")
    void extractModule_shortPackage_returnsUnknown() {
        assertThat(aspect.extractModule("com.wms"))
            .isEqualTo("unknown");
    }

    @Test
    @DisplayName("extractModule: 1セグメントのパッケージはunknown")
    void extractModule_singleSegment_returnsUnknown() {
        assertThat(aspect.extractModule("com"))
            .isEqualTo("unknown");
    }

    @Test
    @DisplayName("extractModule: 深いパッケージでも3番目を返す")
    void extractModule_deepPackage_returnsThirdSegment() {
        assertThat(aspect.extractModule("com.wms.system.service.auth"))
            .isEqualTo("system");
    }
}
