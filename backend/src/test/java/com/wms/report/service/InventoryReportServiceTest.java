package com.wms.report.service;

import com.wms.generated.model.InventoryReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.UnitType;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InventoryReportRepository;
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

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
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

    /** Object[]のリストを型安全に生成するヘルパー */
    @SafeVarargs
    private static List<Object[]> listOf(Object[]... rows) {
        return java.util.Arrays.asList(rows);
    }

    /** ネイティブクエリ結果のObject[]を生成するヘルパー */
    private Object[] createRow(int quantity, int allocatedQty, String locationCode,
                                String buildingName, String areaName,
                                String productCode, String productName,
                                String unitType, String lotNumber, LocalDate expiryDate) {
        return new Object[]{
                BigInteger.valueOf(1L),  // 0: id
                BigInteger.valueOf(1L),  // 1: warehouse_id
                BigInteger.valueOf(1L),  // 2: location_id
                BigInteger.valueOf(1L),  // 3: product_id
                unitType,                // 4: unit_type
                lotNumber,               // 5: lot_number
                expiryDate != null ? Date.valueOf(expiryDate) : null,  // 6: expiry_date
                quantity,                // 7: quantity
                allocatedQty,            // 8: allocated_qty
                BigInteger.valueOf(0L),  // 9: version
                Timestamp.valueOf("2026-03-27 10:00:00"),  // 10: updated_at
                locationCode,            // 11: location_code
                buildingName,            // 12: building_name
                areaName,                // 13: area_name
                productCode,             // 14: product_code
                productName              // 15: product_name
        };
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_withValidParams_returnsReportData() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inventoryReportRepository.findInventoryReportData(any(), any(), any(), any(), any()))
                    .thenReturn(listOf(
                            createRow(100, 20, "A-01-01", "1号棟", "保管A", "P-001", "商品A", "CAS", "LOT-001", LocalDate.of(2027, 3, 14)),
                            createRow(50, 0, "A-01-02", "1号棟", "保管A", "P-001", "商品A", "PCS", null, null)));
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
            List<Object[]> rows = listOf(createRow(30, 10, "B-01-01", "2号棟", "保管B", "P-002", "商品B", "BAL", null, null));
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
            List<Object[]> rows = listOf(createRow(100, 20, "A-01-01", "1号棟", "保管A", "P-001", "商品A", "CAS", null, null));
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
