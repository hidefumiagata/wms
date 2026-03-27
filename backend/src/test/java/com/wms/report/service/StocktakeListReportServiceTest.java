package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.StocktakeListReportItem;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.master.entity.Building;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.BuildingRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.StocktakeReportRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
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
@DisplayName("StocktakeListReportService")
class StocktakeListReportServiceTest {

    @Mock
    private StocktakeReportRepository stocktakeReportRepository;
    @Mock
    private StocktakeHeaderRepository stocktakeHeaderRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private StocktakeListReportService service;

    private Warehouse warehouse;
    private StocktakeHeader stocktakeHeader;
    private Building building;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", "password"));

        warehouse = new Warehouse();
        setEntityId(warehouse, 1L);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("東京第一倉庫");

        stocktakeHeader = new StocktakeHeader();
        stocktakeHeader.setId(10L);
        stocktakeHeader.setStocktakeNumber("ST-2026-00042");
        stocktakeHeader.setWarehouseId(1L);
        stocktakeHeader.setTargetDescription("A棟 1F全エリア");
        stocktakeHeader.setStatus("STARTED");

        building = new Building();
        setEntityId(building, 5L);
        building.setWarehouseId(1L);
        building.setBuildingCode("BLD-001");
        building.setBuildingName("A棟");
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

    @SafeVarargs
    private static List<Object[]> listOf(Object[]... rows) {
        return Arrays.asList(rows);
    }

    private Object[] createRow(String locationCode, String areaName,
                                String productCode, String productName,
                                String unitType, String lotNumber, LocalDate expiryDate,
                                Integer systemQty, Integer actualQty) {
        return new Object[]{
                locationCode,           // 0: location_code
                areaName,               // 1: area_name
                productCode,            // 2: product_code
                productName,            // 3: product_name
                unitType,               // 4: unit_type
                lotNumber,              // 5: lot_number
                expiryDate != null ? Date.valueOf(expiryDate) : null,  // 6: expiry_date
                systemQty,              // 7: system_quantity
                actualQty               // 8: actual_quantity
        };
    }

    @Nested
    @DisplayName("generate - 棚卸ID指定")
    class GenerateByStocktakeId {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_withStocktakeId_returnsReportData() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeListByStocktakeId(10L))
                    .thenReturn(listOf(
                            createRow("A-01-A-01", "保管A", "P-001", "商品A", "CAS", "LOT-001",
                                    LocalDate.of(2026, 12, 31), 10, null),
                            createRow("A-01-A-01", "保管A", "P-002", "商品B", "PCS", null, null, 50, 47)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<StocktakeListReportItem>> response =
                    service.generate(10L, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(2);
            StocktakeListReportItem item1 = response.getBody().get(0);
            assertThat(item1.getLocationCode()).isEqualTo("A-01-A-01");
            assertThat(item1.getAreaName()).isEqualTo("保管A");
            assertThat(item1.getProductCode()).isEqualTo("P-001");
            assertThat(item1.getUnitType()).isEqualTo("CAS");
            assertThat(item1.getSystemQuantity()).isEqualTo(10);
            assertThat(item1.getActualQuantity()).isNull();
            assertThat(item1.getLotNumber()).isEqualTo("LOT-001");
            assertThat(item1.getExpiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));

            StocktakeListReportItem item2 = response.getBody().get(1);
            assertThat(item2.getActualQuantity()).isEqualTo(47);
            assertThat(item2.getLotNumber()).isNull();
            assertThat(item2.getExpiryDate()).isNull();
        }

        @Test
        @DisplayName("条件サマリーに棚卸番号と対象範囲が含まれる")
        void generate_withStocktakeId_conditionsSummary() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeListByStocktakeId(10L)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(10L, null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .contains("棚卸番号: ST-2026-00042")
                    .contains("対象: A棟 1F全エリア");
        }

        @Test
        @DisplayName("targetDescriptionがnullの場合も正常動作")
        void generate_nullTargetDescription_noError() {
            stocktakeHeader.setTargetDescription(null);
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeListByStocktakeId(10L)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(10L, null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .isEqualTo("棚卸番号: ST-2026-00042");
        }
    }

    @Nested
    @DisplayName("generate - 棟ID指定（プレビュー）")
    class GenerateByBuildingId {

        @Test
        @DisplayName("棟ID指定でプレビューデータが生成される")
        void generate_withBuildingId_returnsPreviewData() {
            when(buildingRepository.findById(5L)).thenReturn(Optional.of(building));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeListByBuildingId(5L, null))
                    .thenReturn(listOf(
                            createRow("A-01-A-01", "保管A", "P-001", "商品A", "CAS", null, null, 100, null)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<StocktakeListReportItem>> response =
                    service.generate(null, 5L, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().getFirst().getActualQuantity()).isNull();
        }

        @Test
        @DisplayName("areaId指定付きのプレビュー")
        void generate_withBuildingIdAndAreaId_passesAreaId() {
            when(buildingRepository.findById(5L)).thenReturn(Optional.of(building));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeListByBuildingId(5L, 3L)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(null, 5L, 3L, null, ReportFormat.JSON);

            verify(stocktakeReportRepository).findStocktakeListByBuildingId(5L, 3L);
        }

        @Test
        @DisplayName("条件サマリーにプレビュー表示が含まれる")
        void generate_withBuildingId_conditionsSummaryContainsPreview() {
            when(buildingRepository.findById(5L)).thenReturn(Optional.of(building));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeListByBuildingId(5L, null)).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(null, 5L, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .contains("棟: A棟")
                    .contains("プレビュー");
        }
    }

    @Nested
    @DisplayName("generate - CSV出力")
    class GenerateCsv {

        @Test
        @DisplayName("CSV形式でcsvRowMapperが正しく動作する")
        void generate_csvFormat_usesRowMapper() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeListByStocktakeId(10L))
                    .thenReturn(listOf(createRow("A-01", "保管A", "P-001", "商品A", "CAS", "LOT-001",
                            LocalDate.of(2027, 3, 14), 10, null)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(9);
                        assertThat(row[0]).isEqualTo("A-01");
                        assertThat(row[6]).isEqualTo("\u2014"); // actualQuantity=null → em dash
                        return ResponseEntity.ok(data);
                    });

            service.generate(10L, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any());
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("両方未指定の場合はBusinessRuleViolationException")
        void generate_bothNull_throwsException() {
            assertThatThrownBy(() -> service.generate(null, null, null, null, ReportFormat.JSON))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .satisfies(ex -> assertThat(((BusinessRuleViolationException) ex).getErrorCode())
                            .isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("棚卸が存在しない場合はResourceNotFoundException")
        void generate_stocktakeNotFound_throwsException() {
            when(stocktakeHeaderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, null, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("STOCKTAKE_NOT_FOUND"));
        }

        @Test
        @DisplayName("棟が存在しない場合はResourceNotFoundException")
        void generate_buildingNotFound_throwsException() {
            when(buildingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(null, 999L, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("BUILDING_NOT_FOUND"));
        }

        @Test
        @DisplayName("棚卸の倉庫が存在しない場合はResourceNotFoundException")
        void generate_stocktakeWarehouseNotFound_throwsException() {
            stocktakeHeader.setWarehouseId(999L);
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(10L, null, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }

        @Test
        @DisplayName("棟の倉庫が存在しない場合はResourceNotFoundException")
        void generate_buildingWarehouseNotFound_throwsException() {
            building.setWarehouseId(999L);
            when(buildingRepository.findById(5L)).thenReturn(Optional.of(building));
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(null, 5L, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("generate - データ変換")
    class DataConversion {

        @Test
        @DisplayName("systemQuantityがnullの場合もエラーにならない")
        void generate_nullSystemQuantity_noError() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeListByStocktakeId(10L))
                    .thenReturn(listOf(
                            createRow("A-01", "保管A", "P-001", "商品A", "CAS", null, null, null, null)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<StocktakeListReportItem>> response =
                    service.generate(10L, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getSystemQuantity()).isNull();
        }
    }
}
