package com.wms.shared.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

import java.io.IOException;

/**
 * LogstashEncoder用カスタムMessageJsonProvider。
 * ログメッセージをJSON出力する前にPiiMaskerでマスキングを適用する。
 *
 * <p>注意: MessageJsonProvider(logstash-logback-encoder 8.0)のwriteToを完全にオーバーライドしている。
 * ライブラリ更新時はsuper実装の変更を確認すること。</p>
 */
public class PiiMaskingMessageJsonProvider extends MessageJsonProvider {

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        if (event != null && event.getFormattedMessage() != null && getFieldName() != null) {
            String masked = PiiMasker.mask(event.getFormattedMessage());
            generator.writeStringField(getFieldName(), masked);
        }
    }
}
