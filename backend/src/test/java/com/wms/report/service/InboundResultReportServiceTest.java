package com.wms.report.service;

import com.wms.generated.model.InboundResultReportItem;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundResultReportServiceTest {

    @Mock
    private InboundReportRepository inboundReportRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private InboundResultReportService service;

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

    private InboundSlipLine createStoredLine(Long productId, int plannedQty, int inspectedQty) {
        InboundSlip slip = InboundSlip.builder()
                .id(1L)
                .slipNumber("INB-20260327-0001")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京第一倉庫")
                .partnerId(10L)
                .partnerName("テスト仕入先A")
                .plannedDate(LocalDate.of(2026, 3, 27))
                .status("STORED")
                .build();
        InboundSlipLine line = InboundSlipLine.builder()
                .id(1L)
                .lineNo(1)
                .productId(productId)
                .productCode("P-001")
                .productName("商品A")
                .unitType("PCS")
                .plannedQty(plannedQty)
                .inspectedQty(inspectedQty)
                .putawayLocationCode("A-01-001")
                .lineStatus("STORED")
                .storedAt(OffsetDateTime.now(ZoneId.of("Asia/Tokyo")))
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
            List<InboundSlipLine> lines = List.of(createStoredLine(100L, 100, 100));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(lines);
            Product product = createProduct(100L, 10);
            when(productRepository.findAllById(any())).thenReturn(List.of(product));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundResultReportItem>> response =
                    service.generate(1L, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            InboundResultReportItem item = response.getBody().getFirst();
            assertThat(item.getSlipNumber()).isEqualTo("INB-20260327-0001");
            assertThat(item.getPlannedQuantityCas()).isEqualTo(10);
            assertThat(item.getInspectedQuantityCas()).isEqualTo(10);
            assertThat(item.getDiffQuantityCas()).isZero();
            assertThat(item.getStoredLocationCode()).isEqualTo("A-01-001");
        }

        @Test
        @DisplayName("差異がある場合の計算が正しい")
        void generate_withDiff_calculatesCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            List<InboundSlipLine> lines = List.of(createStoredLine(100L, 100, 80));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(lines);
            Product product = createProduct(100L, 10);
            when(productRepository.findAllById(any())).thenReturn(List.of(product));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundResultReportItem>> response =
                    service.generate(1L, null, null, null, ReportFormat.JSON);

            InboundResultReportItem item = response.getBody().getFirst();
            assertThat(item.getPlannedQuantityCas()).isEqualTo(10);
            assertThat(item.getInspectedQuantityCas()).isEqualTo(8);
            assertThat(item.getDiffQuantityCas()).isEqualTo(-2);
        }

        @Test
        @DisplayName("日付範囲が正しくOffsetDateTimeに変換される")
        void generate_withDateRange_convertsToOffsetDateTime() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    null, ReportFormat.JSON);

            ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            verify(inboundReportRepository).findResultReportData(
                    any(), fromCaptor.capture(), toCaptor.capture(), any());

            assertThat(fromCaptor.getValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            // storedDateTo は翌日の0時（未満条件）
            assertThat(toCaptor.getValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        }

        @Test
        @DisplayName("条件サマリーが正しく構築される")
        void generate_buildsConditionsSummaryCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .contains("期間: 2026-03-01 ～ 2026-03-31");
        }

        @Test
        @DisplayName("日付未指定時は条件サマリーが空文字")
        void generate_withNoDateRange_emptyConditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEmpty();
        }
    }

    @Nested
    @DisplayName("generate - CSV/追加ケース")
    class GenerateCsvAndEdgeCases {

        @Test
        @DisplayName("CSV形式でcsvRowMapperが動作する")
        void generate_csvFormat_usesRowMapper() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(createStoredLine(100L, 100, 100)));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(10);
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
        }

        @Test
        @DisplayName("inspectedQtyがnullの場合はinspectedCasとdiffCasがnull")
        void generate_nullInspectedQty_returnsNullDerivedFields() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            InboundSlipLine line = createStoredLineWithNullInspected(100L, 100);
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(line));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundResultReportItem>> response =
                    service.generate(1L, null, null, null, ReportFormat.JSON);

            InboundResultReportItem item = response.getBody().getFirst();
            assertThat(item.getInspectedQuantityCas()).isNull();
            assertThat(item.getDiffQuantityCas()).isNull();
        }

        @Test
        @DisplayName("storedAtがnullの場合はstoredDateがnull")
        void generate_nullStoredAt_returnsNullStoredDate() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            InboundSlipLine line = createStoredLineWithNullStoredAt(100L, 100, 100);
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(line));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundResultReportItem>> response =
                    service.generate(1L, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getStoredDate()).isNull();
        }

        @Test
        @DisplayName("ケース入数0の場合はケース数が0")
        void generate_zeroCaseQuantity_casIsZero() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(createStoredLine(100L, 100, 100)));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 0)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundResultReportItem>> response =
                    service.generate(1L, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getPlannedQuantityCas()).isZero();
            assertThat(response.getBody().getFirst().getInspectedQuantityCas()).isNull();
        }

        @Test
        @DisplayName("商品マスタ未登録の場合はケース入数1")
        void generate_productNotFound_caseQuantityOne() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(createStoredLine(999L, 100, 100)));
            when(productRepository.findAllById(any())).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundResultReportItem>> response =
                    service.generate(1L, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getPlannedQuantityCas()).isEqualTo(100);
        }

        @Test
        @DisplayName("toのみ指定の条件サマリー")
        void generate_toOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, LocalDate.of(2026, 3, 31), null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).contains("期間:");
        }

        @Test
        @DisplayName("fromのみ指定した条件サマリー")
        void generate_fromOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findResultReportData(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 1), null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).contains("期間: 2026-03-01 ～ ");
        }
    }

    private InboundSlipLine createStoredLineWithNullInspected(Long productId, int plannedQty) {
        InboundSlip slip = InboundSlip.builder()
                .id(1L).slipNumber("INB-20260327-0001")
                .warehouseId(1L).warehouseCode("WH-001").warehouseName("東京第一倉庫")
                .partnerId(10L).partnerName("テスト仕入先A")
                .plannedDate(LocalDate.of(2026, 3, 27)).status("STORED").build();
        InboundSlipLine line = InboundSlipLine.builder()
                .id(1L).lineNo(1).productId(productId).productCode("P-001").productName("商品A")
                .unitType("PCS").plannedQty(plannedQty).inspectedQty(null)
                .lineStatus("STORED").storedAt(OffsetDateTime.now(ZoneId.of("Asia/Tokyo"))).build();
        line.setInboundSlip(slip);
        return line;
    }

    private InboundSlipLine createStoredLineWithNullStoredAt(Long productId, int plannedQty, int inspectedQty) {
        InboundSlip slip = InboundSlip.builder()
                .id(1L).slipNumber("INB-20260327-0001")
                .warehouseId(1L).warehouseCode("WH-001").warehouseName("東京第一倉庫")
                .partnerId(10L).partnerName("テスト仕入先A")
                .plannedDate(LocalDate.of(2026, 3, 27)).status("STORED").build();
        InboundSlipLine line = InboundSlipLine.builder()
                .id(1L).lineNo(1).productId(productId).productCode("P-001").productName("商品A")
                .unitType("PCS").plannedQty(plannedQty).inspectedQty(inspectedQty)
                .lineStatus("STORED").storedAt(null).build();
        line.setInboundSlip(slip);
        return line;
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("倉庫が存在しない場合は ResourceNotFoundException がスローされる")
        void generate_warehouseNotFound_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, null, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }
}
