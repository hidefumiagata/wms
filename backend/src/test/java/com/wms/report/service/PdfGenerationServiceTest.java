package com.wms.report.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfGenerationServiceTest {

    @Mock
    private TemplateEngine templateEngine;

    private PdfGenerationService service;

    @BeforeEach
    void setUp() {
        service = new PdfGenerationService(templateEngine);
    }

    @Test
    @DisplayName("正しい HTML から PDF バイナリが生成される")
    void shouldGeneratePdfFromHtml() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"/></head>
                <body>
                <h1>テストレポート</h1>
                <p>日本語テスト</p>
                </body>
                </html>
                """;
        when(templateEngine.process(eq("reports/test-template"), any(Context.class)))
                .thenReturn(html);

        byte[] result = service.generatePdf("test-template", Map.of("reportTitle", "テスト"));

        assertThat(result).isNotEmpty();
        // PDF ファイルは %PDF- で始まる
        assertThat(new String(result, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("テンプレートエンジンが例外を投げた場合 PdfGenerationException がスローされる")
    void shouldThrowPdfGenerationExceptionOnTemplateError() {
        when(templateEngine.process(eq("reports/bad-template"), any(Context.class)))
                .thenThrow(new RuntimeException("Template not found"));

        assertThatThrownBy(() -> service.generatePdf("bad-template", Map.of()))
                .isInstanceOf(PdfGenerationService.PdfGenerationException.class)
                .hasMessageContaining("PDF生成に失敗しました")
                .hasMessageContaining("bad-template")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("不正な HTML の場合 PdfGenerationException がスローされる")
    void shouldThrowPdfGenerationExceptionOnInvalidHtml() {
        // Flying Saucer は不正な HTML で例外をスローする
        when(templateEngine.process(eq("reports/invalid"), any(Context.class)))
                .thenReturn("this is not valid html at all <<<>>>");

        assertThatThrownBy(() -> service.generatePdf("invalid", Map.of()))
                .isInstanceOf(PdfGenerationService.PdfGenerationException.class)
                .hasMessageContaining("PDF生成に失敗しました");
    }
}
