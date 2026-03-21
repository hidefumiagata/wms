package com.wms.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

@DisplayName("PiiMaskingMessageJsonProvider: Logbackパイプライン統合テスト")
class PiiMaskingMessageJsonProviderTest {

    private PiiMaskingMessageJsonProvider provider;
    private JsonGenerator generator;

    @BeforeEach
    void setUp() {
        provider = new PiiMaskingMessageJsonProvider();
        provider.setFieldName("message");
        generator = mock(JsonGenerator.class);
    }

    @Test
    @DisplayName("メールアドレスを含むログメッセージがマスキングされてJSON出力される")
    void writeTo_emailInMessage_isMasked() throws IOException {
        ILoggingEvent event = createEvent("Login attempt by user@example.com");

        provider.writeTo(generator, event);

        verify(generator).writeStringField("message", "Login attempt by ***@***.***");
    }

    @Test
    @DisplayName("電話番号を含むログメッセージがマスキングされてJSON出力される")
    void writeTo_phoneInMessage_isMasked() throws IOException {
        ILoggingEvent event = createEvent("Contact: 03-1234-5678");

        provider.writeTo(generator, event);

        verify(generator).writeStringField("message", "Contact: ***-****-****");
    }

    @Test
    @DisplayName("JWTトークンを含むログメッセージがマスキングされてJSON出力される")
    void writeTo_jwtInMessage_isMasked() throws IOException {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.abc123def456";
        ILoggingEvent event = createEvent("Token: " + jwt);

        provider.writeTo(generator, event);

        verify(generator).writeStringField("message", "Token: [JWT-REDACTED]");
    }

    @Test
    @DisplayName("パスワードKVを含むログメッセージがマスキングされてJSON出力される")
    void writeTo_passwordInMessage_isMasked() throws IOException {
        ILoggingEvent event = createEvent("Params: password=secret123");

        provider.writeTo(generator, event);

        verify(generator).writeStringField("message", "Params: password=*****");
    }

    @Test
    @DisplayName("PII未含有メッセージはそのまま出力される")
    void writeTo_noSensitiveData_passesThrough() throws IOException {
        ILoggingEvent event = createEvent("Normal log message");

        provider.writeTo(generator, event);

        verify(generator).writeStringField("message", "Normal log message");
    }

    @Test
    @DisplayName("nullイベントの場合は何も出力しない")
    void writeTo_nullEvent_noOutput() throws IOException {
        provider.writeTo(generator, null);

        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("nullメッセージのイベントの場合は何も出力しない")
    void writeTo_nullMessage_noOutput() throws IOException {
        ILoggingEvent event = createEvent(null);

        provider.writeTo(generator, event);

        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("fieldNameがnullの場合は何も出力しない")
    void writeTo_nullFieldName_noOutput() throws IOException {
        provider.setFieldName(null);
        ILoggingEvent event = createEvent("test message");

        provider.writeTo(generator, event);

        verifyNoInteractions(generator);
    }

    private ILoggingEvent createEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage(message);
        return event;
    }
}
