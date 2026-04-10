package com.wms.report.service;

import com.wms.generated.model.PickingInstructionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.outbound.entity.PickingInstruction;
import com.wms.outbound.repository.PickingInstructionRepository;
import com.wms.report.repository.OutboundReportRepository;
import com.wms.report.repository.projection.PickingInstructionRow;
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
import java.util.List;
import java.util.Optional;

import static com.wms.shared.exception.ResourceNotFoundAssertions.assertResourceNotFound;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PickingInstructionReportService")
class PickingInstructionReportServiceTest {

    @Mock
    private OutboundReportRepository outboundReportRepository;
    @Mock
    private PickingInstructionRepository pickingInstructionRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private PickingInstructionReportService service;

    private Warehouse warehouse;
    private PickingInstruction pickingInstruction;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", "password"));

        warehouse = new Warehouse();
        setWarehouseId(warehouse, 1L);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("東京第一倉庫");

        pickingInstruction = PickingInstruction.builder()
                .id(1L)
                .instructionNumber("PICK-2026-03-27-0001")
                .warehouseId(1L)
                .status("ACTIVE")
                .build();
    }

    private void setWarehouseId(Warehouse wh, Long id) {
        try {
            var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(wh, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static PickingInstructionRow mockRow(String locationCode, String productCode,
                                                  String productName, String unitType,
                                                  Integer qtyToPick, String slipNumber,
                                                  String partnerName, LocalDate plannedDate,
                                                  String lotNumber) {
        PickingInstructionRow row = mock(PickingInstructionRow.class);
        when(row.getLocationCode()).thenReturn(locationCode);
        when(row.getProductCode()).thenReturn(productCode);
        when(row.getProductName()).thenReturn(productName);
        when(row.getUnitType()).thenReturn(unitType);
        when(row.getQtyToPick()).thenReturn(qtyToPick);
        when(row.getSlipNumber()).thenReturn(slipNumber);
        when(row.getPartnerName()).thenReturn(partnerName);
        when(row.getPlannedDate()).thenReturn(plannedDate);
        when(row.getLotNumber()).thenReturn(lotNumber);
        return row;
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_success_returnsItems() {
            when(pickingInstructionRepository.findById(1L)).thenReturn(Optional.of(pickingInstruction));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            var row1 = mockRow("A-01-A-01", "P-001", "商品A", "CAS", 10,
                    "OUT-001", "テスト出荷先A", LocalDate.of(2026, 3, 15), "LOT-001");
            var row2 = mockRow("A-01-A-02", "P-002", "商品B", "PCS", 5,
                    "OUT-002", "テスト出荷先B", LocalDate.of(2026, 3, 16), "LOT-002");
            when(outboundReportRepository.findPickingInstructionReportData(1L))
                    .thenReturn(List.of(row1, row2));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<PickingInstructionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(2);
            PickingInstructionReportItem item1 = response.getBody().get(0);
            assertThat(item1.getLocationCode()).isEqualTo("A-01-A-01");
            assertThat(item1.getProductCode()).isEqualTo("P-001");
            assertThat(item1.getProductName()).isEqualTo("商品A");
            assertThat(item1.getUnitType()).isEqualTo("CAS");
            assertThat(item1.getInstructedQuantity()).isEqualTo(10);
            assertThat(item1.getOutboundSlipNumber()).isEqualTo("OUT-001");
            assertThat(item1.getCustomerName()).isEqualTo("テスト出荷先A");
            assertThat(item1.getPlannedShipDate()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(item1.getLotNumber()).isEqualTo("LOT-001");

            PickingInstructionReportItem item2 = response.getBody().get(1);
            assertThat(item2.getLocationCode()).isEqualTo("A-01-A-02");
            assertThat(item2.getInstructedQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("ReportMetaのフィールドが正しく設定される")
        void generate_success_reportMetaFields() {
            when(pickingInstructionRepository.findById(1L)).thenReturn(Optional.of(pickingInstruction));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findPickingInstructionReportData(1L)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            ReportMeta meta = metaCaptor.getValue();
            assertThat(meta.reportTitle()).isEqualTo("ピッキング指示書");
            assertThat(meta.templateName()).isEqualTo("rpt-12-picking-instruction");
            assertThat(meta.warehouseName()).isEqualTo("東京第一倉庫 (WH-001)");
            assertThat(meta.conditionsSummary()).isEqualTo("指示No: PICK-2026-03-27-0001");
            assertThat(meta.extraTemplateVars()).containsEntry("instructionNumber", "PICK-2026-03-27-0001");
            assertThat(meta.extraTemplateVars()).containsEntry("status", "作業中");
        }

        @Test
        @DisplayName("ロット番号がnullの場合もnullのまま返される")
        void generate_withNullLotNumber_showsDash() {
            when(pickingInstructionRepository.findById(1L)).thenReturn(Optional.of(pickingInstruction));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            var row = mockRow("A-01-A-01", "P-001", "商品A", "CAS", 10,
                    "OUT-001", "テスト出荷先A", LocalDate.of(2026, 3, 15), null);
            when(outboundReportRepository.findPickingInstructionReportData(1L))
                    .thenReturn(List.of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<PickingInstructionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().getFirst().getLotNumber()).isNull();
        }

        @Test
        @DisplayName("データが空の場合は空リストが返される")
        void generate_emptyData_returnsEmptyList() {
            when(pickingInstructionRepository.findById(1L)).thenReturn(Optional.of(pickingInstruction));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findPickingInstructionReportData(1L)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<PickingInstructionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("null QTY_TO_PICK → instructedQuantity=0")
        void generate_nullQtyToPick_defaultsToZero() {
            PickingInstructionRow row = mockRow("A-01-001", "P-001", "商品A", "CAS",
                    null, "OUT-001", "出荷先A", LocalDate.of(2026, 3, 15), "LOT-01");
            when(pickingInstructionRepository.findById(1L))
                .thenReturn(Optional.of(PickingInstruction.builder().id(1L).instructionNumber("PICK-001").warehouseId(1L).status("ACTIVE").build()));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findPickingInstructionReportData(1L)).thenReturn(List.of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            var response = service.generate(1L, ReportFormat.JSON);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getInstructedQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("null PLANNED_DATE → plannedShipDate=null")
        void generate_nullPlannedDate_setsNull() {
            PickingInstructionRow row = mockRow("A-01-001", "P-001", "商品A", "CAS",
                    5, "OUT-001", "出荷先A", null, "LOT-01");
            when(pickingInstructionRepository.findById(1L))
                .thenReturn(Optional.of(PickingInstruction.builder().id(1L).instructionNumber("PICK-001").warehouseId(1L).status("ACTIVE").build()));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findPickingInstructionReportData(1L)).thenReturn(List.of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            var response = service.generate(1L, ReportFormat.JSON);
            assertThat(response.getBody().get(0).getPlannedShipDate()).isNull();
        }
    }

    @Nested
    @DisplayName("generate - CSV出力")
    class GenerateCsv {

        @Test
        @DisplayName("CSVヘッダーが期待通り")
        void generate_csvHeaders_matchExpected() {
            when(pickingInstructionRepository.findById(1L)).thenReturn(Optional.of(pickingInstruction));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findPickingInstructionReportData(1L)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, ReportFormat.CSV);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), metaCaptor.capture());
            assertThat(metaCaptor.getValue().csvHeaders()).containsExactly(
                    "ロケーションコード", "商品コード", "商品名", "荷姿",
                    "指示数量", "出荷伝票番号", "出荷先名", "出荷予定日", "ロット番号");
        }

        @Test
        @DisplayName("csvRowMapperが正しくフォーマットする")
        void generate_csvRowMapper_formatsCorrectly() {
            when(pickingInstructionRepository.findById(1L)).thenReturn(Optional.of(pickingInstruction));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            var row = mockRow("A-01-A-01", "P-001", "商品A", "CAS", 10,
                    "OUT-001", "テスト出荷先A", LocalDate.of(2026, 3, 15), "LOT-001");
            when(outboundReportRepository.findPickingInstructionReportData(1L))
                    .thenReturn(List.of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] csvRow = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(csvRow).hasSize(9);
                        assertThat(csvRow[0]).isEqualTo("A-01-A-01");
                        assertThat(csvRow[1]).isEqualTo("P-001");
                        assertThat(csvRow[2]).isEqualTo("商品A");
                        assertThat(csvRow[3]).isEqualTo("CAS");
                        assertThat(csvRow[5]).isEqualTo("OUT-001");
                        assertThat(csvRow[6]).isEqualTo("テスト出荷先A");
                        assertThat(csvRow[7]).isEqualTo("2026-03-15");
                        assertThat(csvRow[8]).isEqualTo("LOT-001");
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any());
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("ピッキング指示が存在しない場合はResourceNotFoundException")
        void generate_pickingNotFound_throwsException() {
            when(pickingInstructionRepository.findById(999L)).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> service.generate(999L, ReportFormat.JSON));
            assertResourceNotFound(thrown, "PICKING_NOT_FOUND", "ピッキング指示");
        }

        @Test
        @DisplayName("倉庫が存在しない場合はResourceNotFoundException (連鎖 lookup: ピッキング指示 1L 成功 → 倉庫 999L 失敗)")
        void generate_warehouseNotFound_throwsException() {
            PickingInstruction instruction = PickingInstruction.builder()
                    .id(1L)
                    .instructionNumber("PICK-2026-03-27-0001")
                    .warehouseId(999L)
                    .status("ACTIVE")
                    .build();
            when(pickingInstructionRepository.findById(1L)).thenReturn(Optional.of(instruction));
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> service.generate(1L, ReportFormat.JSON));
            assertResourceNotFound(thrown, "WAREHOUSE_NOT_FOUND", "倉庫");
        }
    }
}
