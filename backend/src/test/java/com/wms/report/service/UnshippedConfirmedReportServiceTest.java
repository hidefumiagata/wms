package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnshippedConfirmedReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.entity.UnshippedListRecord;
import com.wms.report.repository.UnshippedListRecordRepository;
import com.wms.shared.exception.ResourceNotFoundException;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnshippedConfirmedReportService - RPT-16 未出荷リスト（確定）")
class UnshippedConfirmedReportServiceTest {

    @Mock
    private UnshippedListRecordRepository unshippedListRecordRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private UnshippedConfirmedReportService service;

    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", "password"));
        warehouse = new Warehouse();
        setEntityId(warehouse, 1L);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("東京第一倉庫");
    }

    private void setEntityId(Object entity, Long id) {
        try {
            var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private UnshippedListRecord createRecord(LocalDate batchDate, LocalDate plannedDate, String status) {
        return UnshippedListRecord.builder()
                .id(1L)
                .batchBusinessDate(batchDate)
                .outboundSlipId(100L)
                .slipNumber("OUT-2026-00045")
                .plannedDate(plannedDate)
                .warehouseCode("WH-001")
                .partnerCode("P-001")
                .partnerName("テスト出荷先B")
                .productCode("P-002")
                .productName("商品B")
                .unitType("CAS")
                .orderedQty(3)
                .currentStatus(status)
                .build();
    }

    private UnshippedListRecord createRecordWithPartnerName(
            LocalDate batchDate, LocalDate plannedDate, String status, String partnerName) {
        return UnshippedListRecord.builder()
                .id(1L)
                .batchBusinessDate(batchDate)
                .outboundSlipId(100L)
                .slipNumber("OUT-2026-00045")
                .plannedDate(plannedDate)
                .warehouseCode("WH-001")
                .partnerCode("P-001")
                .partnerName(partnerName)
                .productCode("P-002")
                .productName("商品B")
                .unitType("CAS")
                .orderedQty(3)
                .currentStatus(status)
                .build();
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートアイテムが生成される")
        void generate_success_returnsItems() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            LocalDate batchDate = LocalDate.of(2026, 3, 14);
            List<UnshippedListRecord> records = List.of(
                    createRecord(batchDate, LocalDate.of(2026, 3, 12), "PICKING_COMPLETED"));
            when(unshippedListRecordRepository
                    .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                            batchDate, "WH-001")).thenReturn(records);
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnshippedConfirmedReportItem>> response =
                    service.generate(1L, batchDate, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            UnshippedConfirmedReportItem item = response.getBody().getFirst();
            assertThat(item.getBatchBusinessDate()).isEqualTo(batchDate);
            assertThat(item.getSlipNumber()).isEqualTo("OUT-2026-00045");
            assertThat(item.getCustomerName()).isEqualTo("テスト出荷先B");
            assertThat(item.getPlannedDate()).isEqualTo(LocalDate.of(2026, 3, 12));
            assertThat(item.getProductCode()).isEqualTo("P-002");
            assertThat(item.getProductName()).isEqualTo("商品B");
            assertThat(item.getOrderedQty()).isEqualTo(3);
            assertThat(item.getStatusAtBatch()).isEqualTo("PICKING_COMPLETED");
        }

        @Test
        @DisplayName("空データの場合は空リストが返される")
        void generate_emptyData_returnsEmptyList() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(unshippedListRecordRepository
                    .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                            any(), any())).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnshippedConfirmedReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 14), ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("複数レコードがすべて変換される")
        void generate_multipleRecords_allConverted() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            LocalDate batchDate = LocalDate.of(2026, 3, 14);
            List<UnshippedListRecord> records = List.of(
                    createRecord(batchDate, LocalDate.of(2026, 3, 10), "ALLOCATED"),
                    createRecord(batchDate, LocalDate.of(2026, 3, 11), "PICKING"),
                    createRecord(batchDate, LocalDate.of(2026, 3, 12), "PICKING_COMPLETED"));
            when(unshippedListRecordRepository
                    .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                            batchDate, "WH-001")).thenReturn(records);
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnshippedConfirmedReportItem>> response =
                    service.generate(1L, batchDate, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(3);
        }

        @Test
        @DisplayName("partnerNameがnullの場合customerNameもnullになる")
        void generate_nullPartnerName_handlesGracefully() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            LocalDate batchDate = LocalDate.of(2026, 3, 14);
            List<UnshippedListRecord> records = List.of(
                    createRecordWithPartnerName(batchDate, LocalDate.of(2026, 3, 12),
                            "PICKING_COMPLETED", null));
            when(unshippedListRecordRepository
                    .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                            batchDate, "WH-001")).thenReturn(records);
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnshippedConfirmedReportItem>> response =
                    service.generate(1L, batchDate, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().getFirst().getCustomerName()).isNull();
        }

        @Test
        @DisplayName("条件サマリーにバッチ処理営業日が含まれる")
        void generate_conditionsSummary_includesBatchDate() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(unshippedListRecordRepository
                    .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                            any(), any())).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 14), ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .isEqualTo("バッチ処理営業日: 2026-03-14（日替確定）");
        }
    }

    @Nested
    @DisplayName("generate - CSV")
    class GenerateCsv {

        @Test
        @DisplayName("CSVヘッダーが期待通りに設定される")
        void generate_csvHeaders_matchExpected() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(unshippedListRecordRepository
                    .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                            any(), any())).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 14), ReportFormat.CSV);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().csvHeaders()).containsExactly(
                    "バッチ処理営業日", "伝票番号", "出荷先名", "出荷予定日",
                    "商品コード", "商品名", "数量", "バッチ時点ステータス");
        }

        @Test
        @DisplayName("csvRowMapperが正しくフォーマットされる")
        void generate_csvRowMapper_formatsCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            LocalDate batchDate = LocalDate.of(2026, 3, 14);
            when(unshippedListRecordRepository
                    .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                            batchDate, "WH-001"))
                    .thenReturn(List.of(createRecord(batchDate, LocalDate.of(2026, 3, 12),
                            "PICKING_COMPLETED")));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(8);
                        assertThat(row[0]).isEqualTo("2026-03-14");       // batchBusinessDate
                        assertThat(row[1]).isEqualTo("OUT-2026-00045");   // slipNumber
                        assertThat(row[2]).isEqualTo("テスト出荷先B");      // customerName
                        assertThat(row[3]).isEqualTo("2026-03-12");       // plannedDate
                        assertThat(row[4]).isEqualTo("P-002");            // productCode
                        assertThat(row[5]).isEqualTo("商品B");             // productName
                        assertThat(row[6]).isEqualTo("3");                // orderedQty
                        assertThat(row[7]).isEqualTo("ピッキング完了");     // statusAtBatch mapped
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, batchDate, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
        }

        @Test
        @DisplayName("csvRowMapperでステータスラベルがマッピングされる")
        void generate_csvRowMapper_statusLabel_mapped() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            LocalDate batchDate = LocalDate.of(2026, 3, 14);
            when(unshippedListRecordRepository
                    .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                            batchDate, "WH-001"))
                    .thenReturn(List.of(createRecord(batchDate, LocalDate.of(2026, 3, 12),
                            "ALLOCATED")));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row[7]).isEqualTo("引当完了");
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, batchDate, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("倉庫が存在しない場合は ResourceNotFoundException がスローされる")
        void generate_warehouseNotFound_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, LocalDate.of(2026, 3, 14), ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }
}
