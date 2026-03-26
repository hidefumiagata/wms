package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.shared.logging.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * レポートのフォーマット切替オーケストレーター。
 * JSON/CSV/PDF の出力形式に応じて適切な ResponseEntity を生成する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExportService {

    private final CsvGenerationService csvGenerationService;
    private final PdfGenerationService pdfGenerationService;

    static final int MAX_RECORD_COUNT = 10_000;

    /**
     * データリストをフォーマットに応じた ResponseEntity に変換する。
     * <p>
     * JSON: {@code ResponseEntity<List<T>>} をそのまま返却。
     * CSV/PDF: バイナリデータを型消去キャストで返却（Spring の ByteArrayHttpMessageConverter が処理）。
     *
     * @param data   レポートデータ
     * @param format 出力フォーマット
     * @param meta   レポートメタデータ
     * @return フォーマットに応じた ResponseEntity
     */
    @SuppressWarnings("unchecked")
    public <T> ResponseEntity<List<T>> export(
            List<T> data,
            ReportFormat format,
            ReportMeta meta) {

        if (data.size() > MAX_RECORD_COUNT) {
            log.warn("Report record limit exceeded: count={}, report={}, traceId={}",
                    data.size(), meta.reportTitle(), TraceContext.getCurrentTraceId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "レポートの最大件数（10,000件）を超えています。条件を絞り込んでください。");
        }

        return switch (format) {
            case JSON -> ResponseEntity.ok(data);
            case CSV -> {
                byte[] csvBytes = csvGenerationService.generate(data, meta);
                yield (ResponseEntity<List<T>>) (ResponseEntity<?>) ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                buildContentDisposition(meta.fileSlug() + ".csv"))
                        .body(csvBytes);
            }
            case PDF -> {
                byte[] pdfBytes = pdfGenerationService.generatePdf(
                        meta.templateName(), meta.toTemplateVariables(data));
                yield (ResponseEntity<List<T>>) (ResponseEntity<?>) ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                buildContentDisposition(meta.fileSlug() + ".pdf"))
                        .body(pdfBytes);
            }
        };
    }

    /**
     * Content-Disposition ヘッダーを安全に構築する。
     * Spring の ContentDisposition ビルダーを使用してヘッダーインジェクションを防止する。
     */
    static String buildContentDisposition(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
