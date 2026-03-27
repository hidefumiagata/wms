package com.wms.report.service;

import com.wms.generated.model.InboundPlanReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.ProductRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InboundReportRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundPlanReportServiceTest {

    @Mock
    private InboundReportRepository inboundReportRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private InboundPlanReportService service;

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

    private Product createProduct(Long id, int caseQuantity) {
        Product product = new Product();
        setEntityId(product, id);
        product.setCaseQuantity(caseQuantity);
        product.setBallQuantity(1);
        product.setStorageCondition("AMBIENT");
        product.setIsHazardous(false);
        product.setLotManageFlag(false);
        product.setExpiryManageFlag(false);
        product.setShipmentStopFlag(false);
        return product;
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

    private InboundSlipLine createLine(String status, Long productId) {
        InboundSlip slip = InboundSlip.builder()
                .id(1L)
                .slipNumber("INB-20260327-0001")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京第一倉庫")
                .partnerId(10L)
                .partnerName("テスト仕入先A")
                .plannedDate(LocalDate.of(2026, 3, 27))
                .status(status)
                .build();
        InboundSlipLine line = InboundSlipLine.builder()
                .id(1L)
                .lineNo(1)
                .productId(productId)
                .productCode("P-001")
                .productName("商品A")
                .unitType("PCS")
                .plannedQty(100)
                .lineStatus("PENDING")
                .build();
        line.setInboundSlip(slip);
        return line;
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_withValidParams_returnsReportData() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            List<InboundSlipLine> lines = List.of(createLine("PLANNED", 100L));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(lines);
            Product product = createProduct(100L, 10);
            when(productRepository.findAllById(any())).thenReturn(List.of(product));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundPlanReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            InboundPlanReportItem item = response.getBody().getFirst();
            assertThat(item.getSlipNumber()).isEqualTo("INB-20260327-0001");
            assertThat(item.getPlannedQuantityCas()).isEqualTo(10);
            assertThat(item.getPlannedQuantityPcs()).isEqualTo(100);
            assertThat(item.getStatusLabel()).isEqualTo("入荷予定");
        }

        @Test
        @DisplayName("ステータスラベルが正しく変換される")
        void generate_mapsStatusLabelCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(createLine("INSPECTING", 100L)));
            Product product = createProduct(100L, 1);
            when(productRepository.findAllById(any())).thenReturn(List.of(product));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundPlanReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getStatusLabel()).isEqualTo("検品中");
        }

        @Test
        @DisplayName("条件サマリーが正しく構築される")
        void generate_buildsConditionsSummaryCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    "PLANNED", null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .contains("期間: 2026-03-01 ～ 2026-03-31")
                    .contains("ステータス: 入荷予定");
        }

        @Test
        @DisplayName("空データでも正常に処理される")
        void generate_withEmptyData_returnsEmptyList() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundPlanReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("generate - CSV/条件サマリー")
    class GenerateCsvAndConditions {

        @Test
        @DisplayName("CSV形式でcsvRowMapperが動作する")
        void generate_csvFormat_usesRowMapper() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(createLine("PLANNED", 100L)));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(8);
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, null, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
        }

        @Test
        @DisplayName("ステータスのみの条件サマリー")
        void generate_statusOnlyCondition() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, "STORED", null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("ステータス: 入庫完了");
        }

        @Test
        @DisplayName("条件なしの場合は空サマリー")
        void generate_noConditions_emptySummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEmpty();
        }

        @Test
        @DisplayName("ケース入数が0の場合はケース数が0")
        void generate_zeroCaseQuantity_casIsZero() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(createLine("PLANNED", 100L)));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 0)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundPlanReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getPlannedQuantityCas()).isZero();
        }

        @Test
        @DisplayName("商品マスタ未登録の場合はケース入数1")
        void generate_productNotFound_caseQuantityOne() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(createLine("PLANNED", 999L)));
            when(productRepository.findAllById(any())).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundPlanReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getPlannedQuantityCas()).isEqualTo(100);
        }

        @Test
        @DisplayName("期間Fromのみ指定のサマリー")
        void generate_fromOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 1), null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("期間: 2026-03-01 ～ ");
        }

        @Test
        @DisplayName("期間Toのみ指定のサマリー")
        void generate_toOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, LocalDate.of(2026, 3, 31), null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("期間:  ～ 2026-03-31");
        }

        @Test
        @DisplayName("未知のステータスは値そのままで表示される")
        void generate_unknownStatus_usesRawValue() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findPlanReportData(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(createLine("UNKNOWN_STATUS", 100L)));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundPlanReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getStatusLabel()).isEqualTo("UNKNOWN_STATUS");
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("倉庫が存在しない場合は ResourceNotFoundException がスローされる")
        void generate_warehouseNotFound_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, null, null, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }
}
