package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnreceivedRealtimeReportItem;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.ProductRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InboundReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.util.BusinessDateProvider;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreceivedRealtimeReportServiceTest {

    @Mock
    private InboundReportRepository inboundReportRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BusinessDateProvider businessDateProvider;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private UnreceivedRealtimeReportService service;

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

    private InboundSlipLine createUnreceivedLine(String status, LocalDate plannedDate) {
        InboundSlip slip = InboundSlip.builder()
                .id(1L)
                .slipNumber("INB-20260310-0001")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京第一倉庫")
                .partnerId(10L)
                .partnerName("テスト仕入先A")
                .plannedDate(plannedDate)
                .status(status)
                .build();
        InboundSlipLine line = InboundSlipLine.builder()
                .id(1L)
                .lineNo(1)
                .productId(100L)
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
            List<InboundSlipLine> lines = List.of(
                    createUnreceivedLine("PLANNED", LocalDate.of(2026, 3, 10)));
            when(inboundReportRepository.findUnreceivedRealtimeData(eq(1L), any()))
                    .thenReturn(lines);
            Product product = createProduct(100L, 10);
            when(productRepository.findAllById(any())).thenReturn(List.of(product));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnreceivedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            UnreceivedRealtimeReportItem item = response.getBody().getFirst();
            assertThat(item.getSlipNumber()).isEqualTo("INB-20260310-0001");
            assertThat(item.getDelayDays()).isEqualTo(7);
            assertThat(item.getPlannedQuantityCas()).isEqualTo(10);
            assertThat(item.getStatusLabel()).isEqualTo("入荷予定");
        }

        @Test
        @DisplayName("asOfDate未指定時は営業日が使用される")
        void generate_withNullAsOfDate_usesBusinessDate() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 17));
            when(inboundReportRepository.findUnreceivedRealtimeData(eq(1L), eq(LocalDate.of(2026, 3, 17))))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, ReportFormat.JSON);

            verify(inboundReportRepository).findUnreceivedRealtimeData(1L, LocalDate.of(2026, 3, 17));
        }

        @Test
        @DisplayName("遅延日数が正しく計算される")
        void generate_calculatesDelayDaysCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            // plannedDate=3/10, asOfDate=3/17 → delay=7日
            List<InboundSlipLine> lines = List.of(
                    createUnreceivedLine("CONFIRMED", LocalDate.of(2026, 3, 10)));
            when(inboundReportRepository.findUnreceivedRealtimeData(any(), any()))
                    .thenReturn(lines);
            Product product = createProduct(100L, 10);
            when(productRepository.findAllById(any())).thenReturn(List.of(product));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnreceivedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getDelayDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("条件サマリーに基準日が含まれる")
        void generate_conditionsSummaryContainsAsOfDate() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findUnreceivedRealtimeData(any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .isEqualTo("基準日: 2026-03-17");
        }
    }

    @Nested
    @DisplayName("generate - CSV/追加ケース")
    class GenerateCsvAndEdgeCases {

        @Test
        @DisplayName("CSV形式でcsvRowMapperが動作する")
        void generate_csvFormat_usesRowMapper() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findUnreceivedRealtimeData(any(), any()))
                    .thenReturn(List.of(createUnreceivedLine("PLANNED", LocalDate.of(2026, 3, 10))));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(8);
                        assertThat(row[7]).contains("日");
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
        }

        @Test
        @DisplayName("ケース入数0の場合はケース数が0")
        void generate_zeroCaseQuantity_casIsZero() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findUnreceivedRealtimeData(any(), any()))
                    .thenReturn(List.of(createUnreceivedLine("PLANNED", LocalDate.of(2026, 3, 10))));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 0)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnreceivedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getPlannedQuantityCas()).isZero();
        }

        @Test
        @DisplayName("商品マスタ未登録の場合はケース入数1")
        void generate_productNotFound_caseQuantityOne() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findUnreceivedRealtimeData(any(), any()))
                    .thenReturn(List.of(createUnreceivedLine("PLANNED", LocalDate.of(2026, 3, 10))));
            when(productRepository.findAllById(any())).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnreceivedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getPlannedQuantityCas()).isEqualTo(100);
        }

        @Test
        @DisplayName("csvRowMapperでdelayDaysがnullの場合はemダッシュ")
        void generate_csvRowMapper_nullDelayDays() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findUnreceivedRealtimeData(any(), any()))
                    .thenReturn(List.of(createUnreceivedLine("PLANNED", LocalDate.of(2026, 3, 10))));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        // delayDaysがnullのアイテムを作成してcsvRowMapperを呼ぶ
                        UnreceivedRealtimeReportItem nullDelayItem = new UnreceivedRealtimeReportItem();
                        nullDelayItem.setSlipNumber("INB-001");
                        nullDelayItem.setPlannedDate(LocalDate.of(2026, 3, 10));
                        nullDelayItem.setPlannedQuantityCas(10);
                        nullDelayItem.setDelayDays(null);
                        String[] row = meta.csvRowMapper().apply(nullDelayItem);
                        assertThat(row[7]).isEqualTo("\u2014");
                        return ResponseEntity.ok(inv.getArgument(0));
                    });

            service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.CSV);
        }

        @Test
        @DisplayName("未知のステータスは値そのままで表示される")
        void generate_unknownStatus_usesRawValue() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(inboundReportRepository.findUnreceivedRealtimeData(any(), any()))
                    .thenReturn(List.of(createUnreceivedLine("UNKNOWN", LocalDate.of(2026, 3, 10))));
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnreceivedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getStatusLabel()).isEqualTo("UNKNOWN");
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("倉庫が存在しない場合は ResourceNotFoundException がスローされる")
        void generate_warehouseNotFound_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }
}
