package com.wms.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMaskingPatternLayoutEncoder: devプロファイル用PIIマスキングエンコーダー")
class PiiMaskingPatternLayoutEncoderTest {

    private PiiMaskingPatternLayoutEncoder encoder;
    private LoggerContext context;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        encoder = new PiiMaskingPatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%msg%n");
        encoder.start();
    }

    @Test
    @DisplayName("メールアドレスを含むログメッセージがマスキングされる")
    void encode_emailInMessage_isMasked() {
        ILoggingEvent event = createEvent("Login by user@example.com");

        byte[] encoded = encoder.encode(event);
        String result = new String(encoded);

        assertThat(result).doesNotContain("user@example.com");
        assertThat(result).contains("***@***.***");
    }

    @Test
    @DisplayName("PII未含有メッセージはそのまま出力される")
    void encode_noSensitiveData_passesThrough() {
        ILoggingEvent event = createEvent("Normal message");

        byte[] encoded = encoder.encode(event);
        String result = new String(encoded);

        assertThat(result).contains("Normal message");
    }

    @Test
    @DisplayName("JWTトークンがマスキングされる")
    void encode_jwtInMessage_isMasked() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.abc123def456";
        ILoggingEvent event = createEvent("Token: " + jwt);

        byte[] encoded = encoder.encode(event);
        String result = new String(encoded);

        assertThat(result).doesNotContain("eyJ");
        assertThat(result).contains("[JWT-REDACTED]");
    }

    private ILoggingEvent createEvent(String message) {
        Logger logger = context.getLogger("test");
        LoggingEvent event = new LoggingEvent(
                "com.wms.Test", logger, Level.INFO, message, null, null);
        return event;
    }
}
