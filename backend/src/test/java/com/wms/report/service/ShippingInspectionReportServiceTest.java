package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.ShippingInspectionReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.report.repository.OutboundReportRepository;
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

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
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
@DisplayName("ShippingInspectionReportService")
class ShippingInspectionReportServiceTest {

    @Mock
    private OutboundReportRepository outboundReportRepository;
    @Mock
    private OutboundSlipRepository outboundSlipRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private ShippingInspectionReportService service;

    private Warehouse warehouse;
    private OutboundSlip outboundSlip;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", "password"));

        warehouse = new Warehouse();
        setWarehouseId(warehouse, 1L);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("東京第一倉庫");

        outboundSlip = OutboundSlip.builder()
                .id(1L)
                .slipNumber("OUT-2026-00050")
                .warehouseId(1L)
                .partnerId(1L)
                .partnerName("テスト出荷先A")
                .plannedDate(LocalDate.of(2026, 3, 15))
                .status("INSPECTING")
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

    @SafeVarargs
    private static List<Object[]> listOf(Object[]... rows) {
        return Arrays.asList(rows);
    }

    private Object[] createRow(String slipNumber, String partnerName, LocalDate plannedDate,
                                String productCode, String productName, String unitType,
                                Integer orderedQty, Integer inspectedQty) {
        return new Object[]{
                slipNumber,                                             // 0: slip_number
                partnerName,                                            // 1: partner_name
                plannedDate != null ? Date.valueOf(plannedDate) : null, // 2: planned_date
                productCode,                                            // 3: product_code
                productName,                                            // 4: product_name
                unitType,                                               // 5: unit_type
                orderedQty,                                             // 6: ordered_qty
                inspectedQty                                            // 7: inspected_qty
        };
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成され差異数量が計算される")
        void generate_success_returnsItems() {
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(outboundSlip));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findShippingInspectionReportData(1L))
                    .thenReturn(listOf(
                            createRow("OUT-2026-00050", "テスト出荷先A", LocalDate.of(2026, 3, 15),
                                    "P-001", "商品A", "CAS", 10, 10),
                            createRow("OUT-2026-00050", "テスト出荷先A", LocalDate.of(2026, 3, 15),
                                    "P-002", "商品B", "PCS", 20, 18)));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ShippingInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(2);
            ShippingInspectionReportItem item1 = response.getBody().get(0);
            assertThat(item1.getSlipNumber()).isEqualTo("OUT-2026-00050");
            assertThat(item1.getCustomerName()).isEqualTo("テスト出荷先A");
            assertThat(item1.getPlannedShipDate()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(item1.getProductCode()).isEqualTo("P-001");
            assertThat(item1.getProductName()).isEqualTo("商品A");
            assertThat(item1.getUnitType()).isEqualTo("CAS");
            assertThat(item1.getPickedQuantity()).isEqualTo(10);
            assertThat(item1.getInspectedQuantity()).isEqualTo(10);
            assertThat(item1.getDiffQuantity()).isEqualTo(0);

            ShippingInspectionReportItem item2 = response.getBody().get(1);
            assertThat(item2.getPickedQuantity()).isEqualTo(20);
            assertThat(item2.getInspectedQuantity()).isEqualTo(18);
            assertThat(item2.getDiffQuantity()).isEqualTo(-2);
        }

        @Test
        @DisplayName("検品数がnullの場合は差異数量もnull")
        void generate_withNullInspectedQty_diffIsNull() {
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(outboundSlip));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findShippingInspectionReportData(1L))
                    .thenReturn(listOf(
                            createRow("OUT-2026-00050", "テスト出荷先A", LocalDate.of(2026, 3, 15),
                                    "P-001", "商品A", "CAS", 10, null)));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ShippingInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            ShippingInspectionReportItem item = response.getBody().getFirst();
            assertThat(item.getPickedQuantity()).isEqualTo(10);
            assertThat(item.getInspectedQuantity()).isNull();
            assertThat(item.getDiffQuantity()).isNull();
        }

        @Test
        @DisplayName("差異数量が正しく計算される（inspectedQty=8, orderedQty=10 → diff=-2）")
        void generate_withDifference_calculatesCorrectly() {
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(outboundSlip));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findShippingInspectionReportData(1L))
                    .thenReturn(listOf(
                            createRow("OUT-2026-00050", "テスト出荷先A", LocalDate.of(2026, 3, 15),
                                    "P-001", "商品A", "CAS", 10, 8)));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ShippingInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            ShippingInspectionReportItem item = response.getBody().getFirst();
            assertThat(item.getPickedQuantity()).isEqualTo(10);
            assertThat(item.getInspectedQuantity()).isEqualTo(8);
            assertThat(item.getDiffQuantity()).isEqualTo(-2);
        }

        @Test
        @DisplayName("データが空の場合は空リストが返される")
        void generate_emptyData_returnsEmptyList() {
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(outboundSlip));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findShippingInspectionReportData(1L)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ShippingInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("null PLANNED_DATE → plannedShipDate=null")
        void generate_nullPlannedDate_setsNull() {
            Object[] row = createRow("OUT-2026-00050", "テスト出荷先A", null,
                    "P-001", "商品A", "CAS", 10, 10);
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(outboundSlip));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findShippingInspectionReportData(1L)).thenReturn(listOf(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            var response = service.generate(1L, ReportFormat.JSON);
            assertThat(response.getBody().get(0).getPlannedShipDate()).isNull();
        }

        @Test
        @DisplayName("null ORDERED_QTY → pickedQuantity=0")
        void generate_nullOrderedQty_defaultsToZero() {
            Object[] row = createRow("OUT-2026-00050", "テスト出荷先A", LocalDate.of(2026, 3, 15),
                    "P-001", "商品A", "CAS", null, 10);
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(outboundSlip));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findShippingInspectionReportData(1L)).thenReturn(listOf(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            var response = service.generate(1L, ReportFormat.JSON);
            assertThat(response.getBody().get(0).getPickedQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("extraVarsに伝票情報が含まれる")
        void generate_extraVars_containsSlipInfo() {
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(outboundSlip));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findShippingInspectionReportData(1L)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            ReportMeta meta = metaCaptor.getValue();
            assertThat(meta.reportTitle()).isEqualTo("出荷検品レポート");
            assertThat(meta.templateName()).isEqualTo("rpt-13-shipping-inspection");
            assertThat(meta.warehouseName()).isEqualTo("東京第一倉庫 (WH-001)");
            assertThat(meta.conditionsSummary()).isEqualTo("伝票No: OUT-2026-00050");
            assertThat(meta.extraTemplateVars()).containsEntry("slipNumber", "OUT-2026-00050");
            assertThat(meta.extraTemplateVars()).containsEntry("customerName", "テスト出荷先A");
            assertThat(meta.extraTemplateVars()).containsEntry("plannedShipDate", LocalDate.of(2026, 3, 15));
        }
    }

    @Nested
    @DisplayName("generate - CSV出力")
    class GenerateCsv {

        @Test
        @DisplayName("csvRowMapperが正しくフォーマットする")
        void generate_csvRowMapper_formatsCorrectly() {
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(outboundSlip));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findShippingInspectionReportData(1L))
                    .thenReturn(listOf(
                            createRow("OUT-2026-00050", "テスト出荷先A", LocalDate.of(2026, 3, 15),
                                    "P-001", "商品A", "CAS", 10, 8)));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(9);
                        assertThat(row[0]).isEqualTo("OUT-2026-00050");
                        assertThat(row[1]).isEqualTo("テスト出荷先A");
                        assertThat(row[2]).isEqualTo("2026-03-15");
                        assertThat(row[3]).isEqualTo("P-001");
                        assertThat(row[4]).isEqualTo("商品A");
                        assertThat(row[5]).isEqualTo("CAS");
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
        @DisplayName("出荷伝票が存在しない場合はResourceNotFoundException")
        void generate_slipNotFound_throwsException() {
            when(outboundSlipRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("OUTBOUND_SLIP_NOT_FOUND"));
        }

        @Test
        @DisplayName("倉庫が存在しない場合はResourceNotFoundException")
        void generate_warehouseNotFound_throwsException() {
            OutboundSlip slip = OutboundSlip.builder()
                    .id(1L)
                    .slipNumber("OUT-2026-00050")
                    .warehouseId(999L)
                    .status("INSPECTING")
                    .build();
            when(outboundSlipRepository.findById(1L)).thenReturn(Optional.of(slip));
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(1L, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }
}
