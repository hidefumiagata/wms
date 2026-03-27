package com.wms.report.service;

import com.wms.generated.model.DailySummaryReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.report.repository.BatchExecutionLogRepository;
import com.wms.report.repository.DailySummaryRecordRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailySummaryReportService")
class DailySummaryReportServiceTest {

    @Mock
    private BatchExecutionLogRepository batchExecutionLogRepository;
    @Mock
    private DailySummaryRecordRepository dailySummaryRecordRepository;
    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private DailySummaryReportService service;

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 3, 14);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", "password"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @SafeVarargs
    private static List<Object[]> listOf(Object[]... rows) {
        return Arrays.asList(rows);
    }

    private Object[] createRow(LocalDate businessDate, Long warehouseId, String warehouseName,
                                int inboundCount, int inboundLineCount, long inboundQtyTotal,
                                int outboundCount, int outboundLineCount, long outboundQtyTotal,
                                int returnCount, int returnQtyTotal, long inventoryQtyTotal,
                                int unreceivedCount, int unshippedCount) {
        return new Object[]{
                businessDate != null ? Date.valueOf(businessDate) : null,
                warehouseId,
                warehouseName,
                inboundCount,
                inboundLineCount,
                inboundQtyTotal,
                outboundCount,
                outboundLineCount,
                outboundQtyTotal,
                returnCount,
                returnQtyTotal,
                inventoryQtyTotal,
                unreceivedCount,
                unshippedCount
        };
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される（複数倉庫）")
        void generate_success_returnsItemsForMultipleWarehouses() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(true);
            when(dailySummaryRecordRepository.findDailySummaryData(TARGET_DATE))
                    .thenReturn(listOf(
                            createRow(TARGET_DATE, 1L, "東京DC", 12, 45, 1230, 8, 30, 870, 2, 150, 5600, 3, 1),
                            createRow(TARGET_DATE, 2L, "大阪DC", 5, 18, 520, 3, 12, 340, 1, 60, 3200, 0, 2)));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DailySummaryReportItem>> response =
                    service.generate(TARGET_DATE, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(2);

            DailySummaryReportItem item1 = response.getBody().get(0);
            assertThat(item1.getBusinessDate()).isEqualTo(TARGET_DATE);
            assertThat(item1.getWarehouseId()).isEqualTo(1L);
            assertThat(item1.getWarehouseName()).isEqualTo("東京DC");
            assertThat(item1.getInboundCount()).isEqualTo(12);
            assertThat(item1.getInboundLineCount()).isEqualTo(45);
            assertThat(item1.getInboundQuantityTotal()).isEqualTo(1230);
            assertThat(item1.getOutboundCount()).isEqualTo(8);
            assertThat(item1.getOutboundLineCount()).isEqualTo(30);
            assertThat(item1.getOutboundQuantityTotal()).isEqualTo(870);
            assertThat(item1.getReturnCount()).isEqualTo(2);
            assertThat(item1.getReturnQuantityTotal()).isEqualTo(150);
            assertThat(item1.getInventoryQuantityTotal()).isEqualTo(5600);
            assertThat(item1.getUnreceivedCount()).isEqualTo(3);
            assertThat(item1.getUnshippedCount()).isEqualTo(1);

            DailySummaryReportItem item2 = response.getBody().get(1);
            assertThat(item2.getWarehouseId()).isEqualTo(2L);
            assertThat(item2.getWarehouseName()).isEqualTo("大阪DC");
        }

        @Test
        @DisplayName("データが空の場合は空リストが返される")
        void generate_emptyData_returnsEmptyList() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(true);
            when(dailySummaryRecordRepository.findDailySummaryData(TARGET_DATE))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DailySummaryReportItem>> response =
                    service.generate(TARGET_DATE, ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("ReportMetaが正しく設定される")
        void generate_success_reportMetaFields() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(true);
            when(dailySummaryRecordRepository.findDailySummaryData(TARGET_DATE))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(TARGET_DATE, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            ReportMeta meta = metaCaptor.getValue();
            assertThat(meta.reportTitle()).isEqualTo("日次集計レポート");
            assertThat(meta.templateName()).isEqualTo("rpt-17-daily-summary");
            assertThat(meta.warehouseName()).isEqualTo("全倉庫");
            assertThat(meta.conditionsSummary()).isEqualTo("対象営業日: 2026-03-14");
        }

        @Test
        @DisplayName("null値のフィールドは0に変換される")
        void generate_nullValues_defaultToZero() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(true);
            Object[] row = new Object[]{
                    Date.valueOf(TARGET_DATE), 1L, "東京DC",
                    null, null, null, null, null, null, null, null, null, null, null
            };
            when(dailySummaryRecordRepository.findDailySummaryData(TARGET_DATE))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DailySummaryReportItem>> response =
                    service.generate(TARGET_DATE, ReportFormat.JSON);

            DailySummaryReportItem item = response.getBody().get(0);
            assertThat(item.getInboundCount()).isEqualTo(0);
            assertThat(item.getInboundLineCount()).isEqualTo(0);
            assertThat(item.getInboundQuantityTotal()).isEqualTo(0);
            assertThat(item.getOutboundCount()).isEqualTo(0);
            assertThat(item.getOutboundLineCount()).isEqualTo(0);
            assertThat(item.getOutboundQuantityTotal()).isEqualTo(0);
            assertThat(item.getReturnCount()).isEqualTo(0);
            assertThat(item.getReturnQuantityTotal()).isEqualTo(0);
            assertThat(item.getInventoryQuantityTotal()).isEqualTo(0);
            assertThat(item.getUnreceivedCount()).isEqualTo(0);
            assertThat(item.getUnshippedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("businessDateがnullの行はnullのまま返される")
        void generate_nullBusinessDate_setsNull() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(true);
            Object[] row = new Object[]{
                    null, 1L, "東京DC",
                    0, 0, 0L, 0, 0, 0L, 0, 0, 0L, 0, 0
            };
            when(dailySummaryRecordRepository.findDailySummaryData(TARGET_DATE))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DailySummaryReportItem>> response =
                    service.generate(TARGET_DATE, ReportFormat.JSON);

            assertThat(response.getBody().get(0).getBusinessDate()).isNull();
        }

        @Test
        @DisplayName("warehouseIdがnullの行はnullのまま返される")
        void generate_nullWarehouseId_setsNull() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(true);
            Object[] row = new Object[]{
                    Date.valueOf(TARGET_DATE), null, "東京DC",
                    0, 0, 0L, 0, 0, 0L, 0, 0, 0L, 0, 0
            };
            when(dailySummaryRecordRepository.findDailySummaryData(TARGET_DATE))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DailySummaryReportItem>> response =
                    service.generate(TARGET_DATE, ReportFormat.JSON);

            assertThat(response.getBody().get(0).getWarehouseId()).isNull();
        }
    }

    @Nested
    @DisplayName("generate - CSV出力")
    class GenerateCsv {

        @Test
        @DisplayName("CSVヘッダーが期待通り")
        void generate_csvHeaders_matchExpected() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(true);
            when(dailySummaryRecordRepository.findDailySummaryData(TARGET_DATE))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(TARGET_DATE, ReportFormat.CSV);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), metaCaptor.capture());
            assertThat(metaCaptor.getValue().csvHeaders()).containsExactly(
                    "対象営業日", "倉庫ID", "倉庫名",
                    "入荷件数", "入荷明細行数", "入荷数量合計",
                    "出荷件数", "出荷明細行数", "出荷数量合計",
                    "返品件数", "返品数量合計",
                    "在庫数量合計", "未入荷件数", "未出荷件数");
        }

        @Test
        @DisplayName("csvRowMapperが正しくフォーマットする")
        void generate_csvRowMapper_formatsCorrectly() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(true);
            when(dailySummaryRecordRepository.findDailySummaryData(TARGET_DATE))
                    .thenReturn(listOf(
                            createRow(TARGET_DATE, 1L, "東京DC", 12, 45, 1230, 8, 30, 870, 2, 150, 5600, 3, 1)));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(14);
                        assertThat(row[0]).isEqualTo("2026-03-14");
                        assertThat(row[1]).isEqualTo("1");
                        assertThat(row[2]).isEqualTo("東京DC");
                        return ResponseEntity.ok(data);
                    });

            service.generate(TARGET_DATE, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any());
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("日替処理が完了していない場合はResourceNotFoundException")
        void generate_batchNotCompleted_throwsException() {
            when(batchExecutionLogRepository.existsByTargetBusinessDateAndStatus(TARGET_DATE, DailySummaryReportService.BATCH_STATUS_SUCCESS))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.generate(TARGET_DATE, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("BATCH_EXECUTION_NOT_FOUND"));
        }
    }
}
