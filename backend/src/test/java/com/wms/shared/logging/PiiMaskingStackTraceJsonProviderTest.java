package com.wms.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

@DisplayName("PiiMaskingStackTraceJsonProvider: スタックトレースPIIマスキング統合テスト")
class PiiMaskingStackTraceJsonProviderTest {

    private PiiMaskingStackTraceJsonProvider provider;
    private JsonGenerator generator;
    private ThrowableHandlingConverter mockConverter;

    @BeforeEach
    void setUp() {
        provider = new PiiMaskingStackTraceJsonProvider();
        provider.setFieldName("stack_trace");
        generator = mock(JsonGenerator.class);

        // ThrowableHandlingConverterをモックに差し替え（スタックトレース文字列を制御するため）
        mockConverter = mock(ThrowableHandlingConverter.class);
        provider.setThrowableConverter(mockConverter);
    }

    @Test
    @DisplayName("例外メッセージ内のメールアドレスがマスキングされる")
    void writeTo_emailInException_isMasked() throws IOException {
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("User not found: john@example.com"));
        String stackTrace = "java.lang.RuntimeException: User not found: john@example.com\n"
                + "\tat com.wms.service.UserService.findByEmail(UserService.java:42)";
        when(mockConverter.convert(event)).thenReturn(stackTrace);

        provider.writeTo(generator, event);

        String expected = "java.lang.RuntimeException: User not found: ***@***.***\n"
                + "\tat com.wms.service.UserService.findByEmail(UserService.java:42)";
        verify(generator).writeStringField("stack_trace", expected);
    }

    @Test
    @DisplayName("例外メッセージ内のJWTトークンがマスキングされる")
    void writeTo_jwtInException_isMasked() throws IOException {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.abc123def456";
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("Invalid token: " + jwt));
        String stackTrace = "java.lang.RuntimeException: Invalid token: " + jwt + "\n"
                + "\tat com.wms.security.JwtValidator.validate(JwtValidator.java:55)";
        when(mockConverter.convert(event)).thenReturn(stackTrace);

        provider.writeTo(generator, event);

        String expected = "java.lang.RuntimeException: Invalid token: [JWT-REDACTED]\n"
                + "\tat com.wms.security.JwtValidator.validate(JwtValidator.java:55)";
        verify(generator).writeStringField("stack_trace", expected);
    }

    @Test
    @DisplayName("例外メッセージ内のパスワードKVがマスキングされる")
    void writeTo_passwordInException_isMasked() throws IOException {
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("Auth failed: password=secret123"));
        String stackTrace = "java.lang.RuntimeException: Auth failed: password=secret123\n"
                + "\tat com.wms.security.AuthService.login(AuthService.java:30)";
        when(mockConverter.convert(event)).thenReturn(stackTrace);

        provider.writeTo(generator, event);

        String expected = "java.lang.RuntimeException: Auth failed: password=*****\n"
                + "\tat com.wms.security.AuthService.login(AuthService.java:30)";
        verify(generator).writeStringField("stack_trace", expected);
    }

    @Test
    @DisplayName("例外メッセージ内の電話番号がマスキングされる")
    void writeTo_phoneInException_isMasked() throws IOException {
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("Contact info: 03-1234-5678"));
        String stackTrace = "java.lang.RuntimeException: Contact info: 03-1234-5678\n"
                + "\tat com.wms.service.UserService.getContact(UserService.java:50)";
        when(mockConverter.convert(event)).thenReturn(stackTrace);

        provider.writeTo(generator, event);

        String expected = "java.lang.RuntimeException: Contact info: ***-****-****\n"
                + "\tat com.wms.service.UserService.getContact(UserService.java:50)";
        verify(generator).writeStringField("stack_trace", expected);
    }

    @Test
    @DisplayName("PII未含有のスタックトレースはそのまま出力される")
    void writeTo_noSensitiveData_passesThrough() throws IOException {
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("Something went wrong"));
        String stackTrace = "java.lang.RuntimeException: Something went wrong\n"
                + "\tat com.wms.service.SomeService.doWork(SomeService.java:10)";
        when(mockConverter.convert(event)).thenReturn(stackTrace);

        provider.writeTo(generator, event);

        verify(generator).writeStringField("stack_trace", stackTrace);
    }

    @Test
    @DisplayName("ネストされた例外のメッセージ内PIIもマスキングされる")
    void writeTo_nestedExceptionWithPii_isMasked() throws IOException {
        RuntimeException cause = new RuntimeException("DB error for user@test.com");
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("Service failed", cause));
        String stackTrace = "java.lang.RuntimeException: Service failed\n"
                + "\tat com.wms.service.SomeService.doWork(SomeService.java:10)\n"
                + "Caused by: java.lang.RuntimeException: DB error for user@test.com\n"
                + "\tat com.wms.repository.UserRepo.find(UserRepo.java:20)";
        when(mockConverter.convert(event)).thenReturn(stackTrace);

        provider.writeTo(generator, event);

        String expected = "java.lang.RuntimeException: Service failed\n"
                + "\tat com.wms.service.SomeService.doWork(SomeService.java:10)\n"
                + "Caused by: java.lang.RuntimeException: DB error for ***@***.***\n"
                + "\tat com.wms.repository.UserRepo.find(UserRepo.java:20)";
        verify(generator).writeStringField("stack_trace", expected);
    }

    @Test
    @DisplayName("nullイベントの場合は何も出力しない")
    void writeTo_nullEvent_noOutput() throws IOException {
        provider.writeTo(generator, null);

        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("例外なしイベントの場合は何も出力しない")
    void writeTo_noThrowable_noOutput() throws IOException {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getThrowableProxy()).thenReturn(null);

        provider.writeTo(generator, event);

        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("fieldNameがnullの場合は何も出力しない")
    void writeTo_nullFieldName_noOutput() throws IOException {
        provider.setFieldName(null);
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("Error: user@example.com"));
        when(mockConverter.convert(event)).thenReturn("stack trace");

        provider.writeTo(generator, event);

        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("throwableConverterがnullの場合は何も出力しない")
    void writeTo_nullConverter_noOutput() throws IOException {
        provider.setThrowableConverter(null);
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("Error: user@example.com"));

        provider.writeTo(generator, event);

        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("コンバーターがnull文字列を返す場合は何も出力しない")
    void writeTo_nullStackTrace_noOutput() throws IOException {
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("error"));
        when(mockConverter.convert(event)).thenReturn(null);

        provider.writeTo(generator, event);

        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("コンバーターが空文字列を返す場合は何も出力しない")
    void writeTo_emptyStackTrace_noOutput() throws IOException {
        ILoggingEvent event = createEventWithThrowable(
                new RuntimeException("error"));
        when(mockConverter.convert(event)).thenReturn("");

        provider.writeTo(generator, event);

        verifyNoInteractions(generator);
    }

    private ILoggingEvent createEventWithThrowable(Throwable throwable) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        IThrowableProxy proxy = new ThrowableProxy(throwable);
        when(event.getThrowableProxy()).thenReturn(proxy);
        when(event.getLevel()).thenReturn(Level.ERROR);
        return event;
    }
}
