package com.wms.shared.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

import java.io.IOException;

/**
 * LogstashEncoder用カスタムMessageJsonProvider。
 * ログメッセージをJSON出力する前にPiiMaskerでマスキングを適用する。
 */
public class PiiMaskingMessageJsonProvider extends MessageJsonProvider {

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        if (event != null && event.getFormattedMessage() != null) {
            String masked = PiiMasker.mask(event.getFormattedMessage());
            writeStringField(generator, getFieldName(), masked);
        }
    }

    private void writeStringField(JsonGenerator generator, String fieldName, String value)
            throws IOException {
        if (fieldName != null) {
            generator.writeStringField(fieldName, value);
        }
    }
}
