package com.wms.shared.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    /** テスト用のダミーサービスクラス（パッケージ名からモジュール抽出に使用） */
    static class FakeService {
    }

    @Test
    void logServiceMethod_success() throws Throwable {
        // Arrange
        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("doSomething");
        when(joinPoint.proceed()).thenReturn("result");

        // Act
        Object result = aspect.logServiceMethod(joinPoint);

        // Assert
        assertThat(result).isEqualTo("result");
        // MDC module should be cleaned up
        assertThat(MDC.get("module")).isNull();
    }

    @Test
    void logServiceMethod_exception() throws Throwable {
        // Arrange
        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("failMethod");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test error"));

        // Act & Assert
        assertThatThrownBy(() -> aspect.logServiceMethod(joinPoint))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("test error");

        // MDC module should be cleaned up even on exception
        assertThat(MDC.get("module")).isNull();
    }

    @Test
    void logServiceMethod_returnsNull() throws Throwable {
        // Arrange
        FakeService target = new FakeService();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("voidMethod");
        when(joinPoint.proceed()).thenReturn(null);

        // Act
        Object result = aspect.logServiceMethod(joinPoint);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void extractModule_standardPackage() {
        // com.wms.inbound.service -> inbound
        assertThat(aspect.extractModule("com.wms.inbound.service"))
            .isEqualTo("inbound");
    }

    @Test
    void extractModule_sharedPackage() {
        // com.wms.shared.service -> shared
        assertThat(aspect.extractModule("com.wms.shared.service"))
            .isEqualTo("shared");
    }

    @Test
    void extractModule_shortPackage() {
        // com.wms -> length=2, < 3 -> unknown
        assertThat(aspect.extractModule("com.wms"))
            .isEqualTo("unknown");
    }

    @Test
    void extractModule_veryShortPackage() {
        // com -> unknown (length < 3)
        assertThat(aspect.extractModule("com"))
            .isEqualTo("unknown");
    }

    @Test
    void extractModule_deepPackage() {
        // com.wms.system.service.auth -> system
        assertThat(aspect.extractModule("com.wms.system.service.auth"))
            .isEqualTo("system");
    }
}
