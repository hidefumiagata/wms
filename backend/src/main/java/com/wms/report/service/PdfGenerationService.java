package com.wms.report.service;

import com.lowagie.text.pdf.BaseFont;
import com.wms.shared.logging.TraceContext;
import jakarta.annotation.PostConstruct;
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
import java.util.regex.Pattern;

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

    /** テンプレート名の許可パターン: 英数字・ハイフン・アンダースコアのみ */
    private static final Pattern TEMPLATE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /** 起動時に解決したフォント URL をキャッシュ */
    private String fontRegularUrl;
    private String fontBoldUrl;

    @PostConstruct
    void initFonts() {
        try {
            fontRegularUrl = new ClassPathResource(FONT_REGULAR).getURL().toExternalForm();
            fontBoldUrl = new ClassPathResource(FONT_BOLD).getURL().toExternalForm();
            log.info("PDF fonts loaded: regular={}, bold={}", FONT_REGULAR, FONT_BOLD);
        } catch (Exception e) {
            log.error("Failed to load PDF fonts", e);
            throw new IllegalStateException("PDF フォントの読み込みに失敗しました", e);
        }
    }

    /**
     * Thymeleaf テンプレートを HTML に変換し、PDF バイナリを生成する。
     *
     * @param templateName テンプレート名（例: "rpt-07-inventory"）。英数字・ハイフン・アンダースコアのみ許可。
     * @param variables    テンプレート変数
     * @return PDF バイナリ
     */
    public byte[] generatePdf(String templateName, Map<String, Object> variables) {
        validateTemplateName(templateName);
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
            log.error("PDF generation failed: template={}, traceId={}",
                    templateName, TraceContext.getCurrentTraceId(), e);
            throw new PdfGenerationException("PDF生成に失敗しました: " + templateName, e);
        }
    }

    private void validateTemplateName(String templateName) {
        if (templateName == null || !TEMPLATE_NAME_PATTERN.matcher(templateName).matches()) {
            throw new IllegalArgumentException("Invalid template name: " + templateName);
        }
    }

    private void registerFonts(ITextRenderer renderer) throws Exception {
        renderer.getFontResolver().addFont(fontRegularUrl, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        renderer.getFontResolver().addFont(fontBoldUrl, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }

    /**
     * PDF 生成時の例外。
     * WmsException は sealed のため直接継承不可。GlobalExceptionHandler のキャッチオールで処理される。
     */
    public static class PdfGenerationException extends RuntimeException {
        public PdfGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
