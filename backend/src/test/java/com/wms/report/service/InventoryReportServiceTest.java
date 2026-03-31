package com.wms.report.service;

import com.wms.generated.model.InventoryReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.UnitType;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InventoryReportRepository;
import com.wms.report.repository.projection.InventoryReportRow;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReportService")
class InventoryReportServiceTest {

    @Mock
    private InventoryReportRepository inventoryReportRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private InventoryReportService service;

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

    /** InventoryReportRowプロジェクションのモックを生成するヘルパー */
    private InventoryReportRow mockRow(int quantity, int allocatedQty, String locationCode,
                                       String buildingName, String areaName,
                                       String productCode, String productName,
                                       String unitType, String lotNumber, LocalDate expiryDate) {
        InventoryReportRow row = mock(InventoryReportRow.class);
        when(row.getQuantity()).thenReturn(quantity);
        when(row.getAllocatedQty()).thenReturn(allocatedQty);
        when(row.getLocationCode()).thenReturn(locationCode);
        when(row.getBuildingName()).thenReturn(buildingName);
        when(row.getAreaName()).thenReturn(areaName);
        when(row.getProductCode()).thenReturn(productCode);
        when(row.getProductName()).thenReturn(productName);
        when(row.getUnitType()).thenReturn(unitType);
        when(row.getLotNumber()).thenReturn(lotNumber);
        when(row.getExpiryDate()).thenReturn(expiryDate);
        return row;
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_withValidParams_returnsReportData() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            var row1 = mockRow(100, 20, "A-01-01", "1号棟", "保管A", "P-001", "商品A", "CAS", "LOT-001", LocalDate.of(2027, 3, 14));
            var row2 = mockRow(50, 0, "A-01-02", "1号棟", "保管A", "P-001", "商品A", "PCS", null, null);
            when(inventoryReportRepository.findInventoryReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(row1, row2));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InventoryReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(2);
            InventoryReportItem item1 = response.getBody().get(0);
            assertThat(item1.getLocationCode()).isEqualTo("A-01-01");
            assertThat(item1.getQuantity()).isEqualTo(100);
            assertThat(item1.getAllocatedQty()).isEqualTo(20);
            assertThat(item1.getAvailableQty()).isEqualTo(80);
            assertThat(item1.getLotNumber()).isEqualTo("LOT-001");
            assertThat(item1.getExpiryDate()).isEqualTo(LocalDate.of(2027, 3, 14));
        }

        @Test
        @DisplayName("ロット/期限がnullの場合もエラーにならない")
        void generate_nullLotAndExpiry_noError() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            List<InventoryReportRow> rows = List.of(mockRow(30, 10, "B-01-01", "2号棟", "保管B", "P-002", "商品B", "BAL", null, null));
            when(inventoryReportRepository.findInventoryReportData(any(), any(), any(), any(), any()))
                    .thenReturn(rows);
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InventoryReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            InventoryReportItem item = response.getBody().getFirst();
            assertThat(item.getLotNumber()).isNull();
            assertThat(item.getExpiryDate()).isNull();
        }

        @Test
        @DisplayName("条件サマリーが正しく構築される")
        void generate_buildsConditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inventoryReportRepository.findInventoryReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, "A-01", null, UnitType.CASE, StorageCondition.AMBIENT, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .contains("ロケーション: A-01*")
                    .contains("荷姿: CASE")
                    .contains("保管条件: AMBIENT");
        }

        @Test
        @DisplayName("条件なしの場合は空サマリー")
        void generate_noConditions_emptySummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inventoryReportRepository.findInventoryReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEmpty();
        }

        @Test
        @DisplayName("CSV形式でcsvRowMapperが動作する")
        void generate_csvFormat_usesRowMapper() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            List<InventoryReportRow> rows = List.of(mockRow(100, 20, "A-01-01", "1号棟", "保管A", "P-001", "商品A", "CAS", null, null));
            when(inventoryReportRepository.findInventoryReportData(any(), any(), any(), any(), any()))
                    .thenReturn(rows);
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(9);
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, null, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any());
        }

        @Test
        @DisplayName("保管条件のみの条件サマリー")
        void generate_storageConditionOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inventoryReportRepository.findInventoryReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, StorageCondition.FROZEN, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("保管条件: FROZEN");
        }

        @Test
        @DisplayName("荷姿のみの条件サマリー")
        void generate_unitTypeOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inventoryReportRepository.findInventoryReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, UnitType.PIECE, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("荷姿: PIECE");
        }

        @Test
        @DisplayName("UnitType/StorageConditionのenumがStringに変換される")
        void generate_enumsConvertedToString() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inventoryReportRepository.findInventoryReportData(
                    eq(1L), any(), any(), eq("CASE"), eq("AMBIENT")))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, UnitType.CASE, StorageCondition.AMBIENT, ReportFormat.JSON);

            verify(inventoryReportRepository).findInventoryReportData(1L, null, null, "CASE", "AMBIENT");
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("倉庫が存在しない場合は ResourceNotFoundException")
        void generate_warehouseNotFound_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, null, null, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }
}
