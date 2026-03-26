package com.wms.report.service;

import com.lowagie.text.pdf.BaseFont;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;

/**
 * Thymeleaf HTML テンプレートを Flying Saucer で PDF に変換するサービス。
 * Noto Sans JP フォントを埋め込み、日本語レポートの PDF 出力を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfGenerationService {

    private final TemplateEngine templateEngine;

    private static final String FONT_REGULAR = "fonts/NotoSansJP-Regular.ttf";
    private static final String FONT_BOLD = "fonts/NotoSansJP-Bold.ttf";

    /**
     * Thymeleaf テンプレートを HTML に変換し、PDF バイナリを生成する。
     *
     * @param templateName テンプレート名（例: "reports/rpt-07-inventory"）
     * @param variables    テンプレート変数
     * @return PDF バイナリ
     */
    public byte[] generatePdf(String templateName, Map<String, Object> variables) {
        try {
            // 1. Thymeleaf で HTML 生成
            Context context = new Context(Locale.JAPAN);
            context.setVariables(variables);
            String html = templateEngine.process("reports/" + templateName, context);

            // 2. Flying Saucer で HTML → PDF
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ITextRenderer renderer = new ITextRenderer();
                registerFonts(renderer);
                renderer.setDocumentFromString(html);
                renderer.layout();
                renderer.createPDF(baos);
                return baos.toByteArray();
            }
        } catch (PdfGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF generation failed for template={}", templateName, e);
            throw new PdfGenerationException("PDF生成に失敗しました: " + templateName, e);
        }
    }

    private void registerFonts(ITextRenderer renderer) throws Exception {
        registerFont(renderer, FONT_REGULAR);
        registerFont(renderer, FONT_BOLD);
    }

    private void registerFont(ITextRenderer renderer, String classPathLocation) throws Exception {
        ClassPathResource resource = new ClassPathResource(classPathLocation);
        String fontPath = resource.getURL().toExternalForm();
        renderer.getFontResolver().addFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }

    /**
     * PDF 生成時の例外。RuntimeException のサブクラス。
     */
    public static class PdfGenerationException extends RuntimeException {
        public PdfGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
