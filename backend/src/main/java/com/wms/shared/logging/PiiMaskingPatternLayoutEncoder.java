package com.wms.shared.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.PatternLayoutEncoderBase;

/**
 * devプロファイル用のテキスト形式PIIマスキングエンコーダー。
 * PatternLayoutEncoderの出力にPiiMaskerを適用する。
 */
public class PiiMaskingPatternLayoutEncoder extends PatternLayoutEncoderBase<ILoggingEvent> {

    @Override
    public void start() {
        PatternLayout patternLayout = new PatternLayout();
        patternLayout.setContext(context);
        patternLayout.setPattern(getPattern());
        patternLayout.setOutputPatternAsHeader(outputPatternAsHeader);
        patternLayout.start();
        this.layout = new PiiMaskingLayout(patternLayout);
        super.start();
    }

    private static class PiiMaskingLayout extends ch.qos.logback.core.LayoutBase<ILoggingEvent> {
        private final PatternLayout delegate;

        PiiMaskingLayout(PatternLayout delegate) {
            this.delegate = delegate;
        }

        @Override
        public String doLayout(ILoggingEvent event) {
            return PiiMasker.mask(delegate.doLayout(event));
        }
    }
}
