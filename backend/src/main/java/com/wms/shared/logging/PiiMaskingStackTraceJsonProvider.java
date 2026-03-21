package com.wms.shared.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider;

import java.io.IOException;

/**
 * LogstashEncoder用カスタムStackTraceJsonProvider。
 * スタックトレースをJSON出力する前にPiiMaskerでマスキングを適用する。
 *
 * <p>例外メッセージに含まれるPII（例: "User not found: john@example.com"）が
 * stack_traceフィールドを通じて漏洩するリスクを防止する。</p>
 *
 * <p>注意: StackTraceJsonProvider(logstash-logback-encoder 8.0)のwriteToをオーバーライドしている。
 * ライブラリ更新時はsuper実装の変更を確認すること。</p>
 */
public class PiiMaskingStackTraceJsonProvider extends StackTraceJsonProvider {

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        if (event != null && event.getThrowableProxy() != null
                && getFieldName() != null && getThrowableConverter() != null) {
            String stackTrace = getThrowableConverter().convert(event);
            if (stackTrace != null && !stackTrace.isEmpty()) {
                String masked = PiiMasker.mask(stackTrace);
                generator.writeStringField(getFieldName(), masked);
            }
        }
    }
}
