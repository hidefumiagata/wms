package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {

    @Mock
    private CsvGenerationService csvGenerationService;

    @Mock
    private PdfGenerationService pdfGenerationService;

    private ReportExportService service;

    private ReportMeta testMeta;

    @BeforeEach
    void setUp() {
        service = new ReportExportService(csvGenerationService, pdfGenerationService);
        testMeta = new ReportMeta(
                "テストレポート", "test-template", "test_20260327",
                "テスト倉庫", "admin", "条件なし",
                new String[]{"ID", "名前"},
                obj -> new String[]{obj.toString(), "value"}
        );
    }

    @Nested
    @DisplayName("JSON フォーマット")
    class JsonFormat {

        @Test
        @DisplayName("データリストがそのまま ResponseEntity で返却される")
        void shouldReturnDataListDirectly() {
            List<String> data = List.of("item1", "item2");

            ResponseEntity<List<String>> response = service.export(data, ReportFormat.JSON, testMeta);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(data);
        }

        @Test
        @DisplayName("空リストも正常に返却される")
        void shouldReturnEmptyList() {
            ResponseEntity<List<String>> response = service.export(List.of(), ReportFormat.JSON, testMeta);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("CSV フォーマット")
    class CsvFormat {

        @Test
        @DisplayName("CSV バイナリが Content-Type: text/csv で返却される")
        void shouldReturnCsvWithCorrectContentType() {
            byte[] csvBytes = "test,csv".getBytes();
            when(csvGenerationService.generate(anyList(), eq(testMeta))).thenReturn(csvBytes);

            ResponseEntity<List<String>> response = service.export(
                    List.of("item"), ReportFormat.CSV, testMeta);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType().toString())
                    .contains("text/csv");
        }

        @Test
        @DisplayName("Content-Disposition ヘッダーに正しいファイル名が設定される")
        void shouldSetContentDispositionWithFilename() {
            when(csvGenerationService.generate(anyList(), eq(testMeta))).thenReturn(new byte[0]);

            ResponseEntity<List<String>> response = service.export(
                    List.of("item"), ReportFormat.CSV, testMeta);

            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .contains("attachment")
                    .contains("test_20260327.csv");
        }
    }

    @Nested
    @DisplayName("PDF フォーマット")
    class PdfFormat {

        @Test
        @DisplayName("PDF バイナリが Content-Type: application/pdf で返却される")
        void shouldReturnPdfWithCorrectContentType() {
            byte[] pdfBytes = "%PDF-test".getBytes();
            when(pdfGenerationService.generatePdf(eq("test-template"), any(Map.class)))
                    .thenReturn(pdfBytes);

            ResponseEntity<List<String>> response = service.export(
                    List.of("item"), ReportFormat.PDF, testMeta);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_PDF);
        }

        @Test
        @DisplayName("Content-Disposition ヘッダーに正しいファイル名が設定される")
        void shouldSetContentDispositionWithFilename() {
            when(pdfGenerationService.generatePdf(eq("test-template"), any(Map.class)))
                    .thenReturn(new byte[0]);

            ResponseEntity<List<String>> response = service.export(
                    List.of("item"), ReportFormat.PDF, testMeta);

            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .contains("attachment")
                    .contains("test_20260327.pdf");
        }
    }

    @Nested
    @DisplayName("レコード件数制限")
    class RecordLimit {

        @Test
        @DisplayName("10,000 件以下の場合は正常に処理される")
        void shouldAllowUpToMaxRecords() {
            List<String> data = new ArrayList<>(
                    IntStream.range(0, 10_000).mapToObj(String::valueOf).toList());

            ResponseEntity<List<String>> response = service.export(data, ReportFormat.JSON, testMeta);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        @DisplayName("10,000 件超過の場合は BusinessRuleViolationException がスローされる")
        void shouldThrowExceptionWhenExceedingMaxRecords() {
            List<String> data = new ArrayList<>(
                    IntStream.range(0, 10_001).mapToObj(String::valueOf).toList());

            assertThatThrownBy(() -> service.export(data, ReportFormat.JSON, testMeta))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("10,000件");
        }
    }
}
