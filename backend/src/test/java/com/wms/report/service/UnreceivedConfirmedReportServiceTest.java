package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnreceivedConfirmedReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.entity.UnreceivedListRecord;
import com.wms.report.repository.UnreceivedListRecordRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreceivedConfirmedReportServiceTest {

    @Mock
    private UnreceivedListRecordRepository unreceivedListRecordRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private UnreceivedConfirmedReportService service;

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

    private UnreceivedListRecord createRecord(LocalDate batchDate, LocalDate plannedDate, String status) {
        return UnreceivedListRecord.builder()
                .id(1L)
                .batchBusinessDate(batchDate)
                .inboundSlipId(100L)
                .slipNumber("INB-20260310-0001")
                .plannedDate(plannedDate)
                .warehouseCode("WH-001")
                .partnerName("テスト仕入先A")
                .productCode("P-001")
                .productName("商品A")
                .unitType("PCS")
                .plannedQty(10)
                .currentStatus(status)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_withValidParams_returnsReportData() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            LocalDate batchDate = LocalDate.of(2026, 3, 14);
            List<UnreceivedListRecord> records = List.of(
                    createRecord(batchDate, LocalDate.of(2026, 3, 10), "PLANNED"));
            when(unreceivedListRecordRepository.findByBatchBusinessDateAndWarehouseCode(
                    batchDate, "WH-001")).thenReturn(records);
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnreceivedConfirmedReportItem>> response =
                    service.generate(1L, batchDate, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            UnreceivedConfirmedReportItem item = response.getBody().getFirst();
            assertThat(item.getBatchBusinessDate()).isEqualTo(batchDate);
            assertThat(item.getSlipNumber()).isEqualTo("INB-20260310-0001");
            assertThat(item.getSupplierName()).isEqualTo("テスト仕入先A");
            assertThat(item.getPlannedQuantityCas()).isEqualTo(10);
            assertThat(item.getStatusAtBatch()).isEqualTo("PLANNED");
        }

        @Test
        @DisplayName("日替処理が未実行の場合は空配列が返される")
        void generate_noBatchData_returnsEmptyList() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(unreceivedListRecordRepository.findByBatchBusinessDateAndWarehouseCode(any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnreceivedConfirmedReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 14), ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("warehouseCodeでフィルタリングされる")
        void generate_filtersbyWarehouseCode() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(unreceivedListRecordRepository.findByBatchBusinessDateAndWarehouseCode(any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 14), ReportFormat.JSON);

            verify(unreceivedListRecordRepository)
                    .findByBatchBusinessDateAndWarehouseCode(LocalDate.of(2026, 3, 14), "WH-001");
        }

        @Test
        @DisplayName("条件サマリーに営業日基準日が含まれる")
        void generate_conditionsSummaryContainsBatchDate() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(unreceivedListRecordRepository.findByBatchBusinessDateAndWarehouseCode(any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 14), ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .isEqualTo("営業日基準日: 2026-03-14（日替確定）");
        }
    }

    @Nested
    @DisplayName("generate - CSV")
    class GenerateCsv {

        @Test
        @DisplayName("CSV形式でcsvRowMapperが動作する")
        void generate_csvFormat_usesRowMapper() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(unreceivedListRecordRepository.findByBatchBusinessDateAndWarehouseCode(any(), any()))
                    .thenReturn(List.of(createRecord(LocalDate.of(2026, 3, 14),
                            LocalDate.of(2026, 3, 10), "PLANNED")));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(8);
                        assertThat(row[0]).isEqualTo("2026-03-14");
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, LocalDate.of(2026, 3, 14), ReportFormat.CSV);
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
