package com.wms.shared.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wms.system.service.SystemParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import org.springframework.aop.support.AopUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ServiceLoggingAspect の AOP 結合テスト。
 *
 * <p>{@code @SpringBootTest} で Spring AOP プロキシが有効な状態で、
 * 実際の Service クラスを呼び出し、ポイントカット式
 * {@code execution(* com.wms..service.*Service.*(..))} が
 * 正しくインターセプトされることを検証する。</p>
 *
 * <h3>ポイントカットのスコープ</h3>
 * <ul>
 *   <li>{@code com.wms.master.service.*Service} — マスタ系</li>
 *   <li>{@code com.wms.system.service.*Service} — システム系</li>
 *   <li>{@code com.wms.inbound.service.*Service} — 入荷系</li>
 *   <li>{@code com.wms.shared.service.*Service} — 共通基盤（意図的に含む）</li>
 *   <li>その他 {@code com.wms} 配下の全モジュールの {@code service} パッケージ</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ServiceLoggingAspect: AOP結合テスト")
class ServiceLoggingAspectIntegrationTest {

    @Autowired
    private SystemParameterService systemParameterService;

    private MdcCapturingAppender listAppender;
    private Logger aspectLogger;

    @BeforeEach
    void setUp() {
        aspectLogger = (Logger) LoggerFactory.getLogger(ServiceLoggingAspect.class);
        listAppender = new MdcCapturingAppender();
        listAppender.start();
        aspectLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    @DisplayName("実際のServiceクラス呼び出しでSTART/ENDログが出力される")
    void realServiceCall_producesStartAndEndLogs() {
        systemParameterService.findAll();

        List<String> messages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(messages)
                .anyMatch(m -> m.contains("START") && m.contains("SystemParameterService") && m.contains("findAll"))
                .anyMatch(m -> m.contains("END") && m.contains("SystemParameterService") && m.contains("findAll"));
    }

    @Test
    @DisplayName("AOP経由でMDCにmodule名が設定される")
    void realServiceCall_setsMdcModule() {
        systemParameterService.findAll();

        List<ILoggingEvent> startEvents = listAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("START"))
                .toList();

        assertThat(startEvents).isNotEmpty();
        assertThat(startEvents.get(0).getMDCPropertyMap())
                .containsEntry("module", "system");
    }

    @Test
    @DisplayName("AOPプロキシが適用されている")
    void serviceBean_isAopProxy() {
        assertThat(AopUtils.isAopProxy(systemParameterService)).isTrue();
    }

    /**
     * MDC を即座にキャプチャする ListAppender。
     * デフォルトの ListAppender では MDC の遅延コピーにより、
     * finally ブロックで MDC がクリアされた後に空マップが返される問題を回避する。
     */
    private static class MdcCapturingAppender extends ListAppender<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    }
}
