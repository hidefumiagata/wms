package com.wms.report.service;

import com.wms.generated.model.InboundInspectionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.master.entity.Product;
import com.wms.master.repository.ProductRepository;
import com.wms.report.repository.InboundReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundInspectionReportServiceTest {

    @Mock
    private InboundReportRepository inboundReportRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private InboundInspectionReportService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", "password"));
    }

    private InboundSlip createSlip() {
        return InboundSlip.builder()
                .id(1L)
                .slipNumber("INB-20260327-0001")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京第一倉庫")
                .partnerId(10L)
                .partnerName("テスト仕入先A")
                .plannedDate(LocalDate.of(2026, 3, 27))
                .status("INSPECTED")
                .build();
    }

    private InboundSlipLine createLine(InboundSlip slip, int lineNo, Long productId,
                                        int plannedQty, Integer inspectedQty) {
        InboundSlipLine line = InboundSlipLine.builder()
                .id((long) lineNo)
                .lineNo(lineNo)
                .productId(productId)
                .productCode("P-00" + lineNo)
                .productName("商品" + lineNo)
                .unitType("PCS")
                .plannedQty(plannedQty)
                .inspectedQty(inspectedQty)
                .lotNumber(lineNo == 1 ? "LOT-001" : null)
                .expiryDate(lineNo == 1 ? LocalDate.of(2027, 3, 14) : null)
                .lineStatus(inspectedQty != null ? "INSPECTED" : "PENDING")
                .build();
        line.setInboundSlip(slip);
        return line;
    }

    private Product createProduct(Long id, int caseQuantity) {
        Product product = new Product();
        setEntityId(product, id);
        product.setProductCode("P-00" + id);
        product.setProductName("商品" + id);
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

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成され、ReportExportServiceに渡される")
        void generate_withValidSlipId_callsExportService() {
            InboundSlip slip = createSlip();
            List<InboundSlipLine> lines = List.of(
                    createLine(slip, 1, 100L, 50, 50),
                    createLine(slip, 2, 101L, 36, 24)
            );
            when(inboundReportRepository.findInspectionReportData(1L)).thenReturn(lines);

            Product p1 = createProduct(100L, 10);
            Product p2 = createProduct(101L, 12);
            when(productRepository.findAllById(any())).thenReturn(List.of(p1, p2));
            when(reportExportService.export(anyList(), eq(ReportFormat.JSON), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(2);
            verify(reportExportService).export(anyList(), eq(ReportFormat.JSON), any(ReportMeta.class));
        }

        @Test
        @DisplayName("ケース数・バラ数・差異が正しく計算される")
        void generate_calculatesQuantitiesCorrectly() {
            InboundSlip slip = createSlip();
            // plannedQty=50pcs, inspectedQty=50pcs, caseQty=10 -> cas: 5/5/0, pcs: 50/50/0
            List<InboundSlipLine> lines = List.of(createLine(slip, 1, 100L, 50, 50));
            when(inboundReportRepository.findInspectionReportData(1L)).thenReturn(lines);
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            InboundInspectionReportItem item = response.getBody().getFirst();
            assertThat(item.getCaseQuantity()).isEqualTo(10);
            assertThat(item.getPlannedQuantityCas()).isEqualTo(5);
            assertThat(item.getInspectedQuantityCas()).isEqualTo(5);
            assertThat(item.getDiffQuantityCas()).isZero();
            assertThat(item.getPlannedQuantityPcs()).isEqualTo(50);
            assertThat(item.getInspectedQuantityPcs()).isEqualTo(50);
            assertThat(item.getDiffQuantityPcs()).isZero();
        }

        @Test
        @DisplayName("差異がある場合のケース数・バラ数が正しく計算される")
        void generate_calculatesNonZeroDiffCorrectly() {
            InboundSlip slip = createSlip();
            // plannedQty=36pcs, inspectedQty=24pcs, caseQty=12 -> cas: 3/2/-1, pcs: 36/24/-12
            List<InboundSlipLine> lines = List.of(createLine(slip, 1, 101L, 36, 24));
            when(inboundReportRepository.findInspectionReportData(1L)).thenReturn(lines);
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(101L, 12)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            InboundInspectionReportItem item = response.getBody().getFirst();
            assertThat(item.getPlannedQuantityCas()).isEqualTo(3);
            assertThat(item.getInspectedQuantityCas()).isEqualTo(2);
            assertThat(item.getDiffQuantityCas()).isEqualTo(-1);
            assertThat(item.getPlannedQuantityPcs()).isEqualTo(36);
            assertThat(item.getInspectedQuantityPcs()).isEqualTo(24);
            assertThat(item.getDiffQuantityPcs()).isEqualTo(-12);
        }

        @Test
        @DisplayName("未検品明細はinspected系がnullになる")
        void generate_uninspectedLine_hasNullInspectedValues() {
            InboundSlip slip = createSlip();
            List<InboundSlipLine> lines = List.of(createLine(slip, 1, 100L, 50, null));
            when(inboundReportRepository.findInspectionReportData(1L)).thenReturn(lines);
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            InboundInspectionReportItem item = response.getBody().getFirst();
            assertThat(item.getInspectedQuantityCas()).isNull();
            assertThat(item.getDiffQuantityCas()).isNull();
            assertThat(item.getInspectedQuantityPcs()).isNull();
            assertThat(item.getDiffQuantityPcs()).isNull();
        }

        @Test
        @DisplayName("ロット番号・期限日が正しく設定される")
        void generate_setsLotAndExpiryCorrectly() {
            InboundSlip slip = createSlip();
            List<InboundSlipLine> lines = List.of(createLine(slip, 1, 100L, 50, 50));
            when(inboundReportRepository.findInspectionReportData(1L)).thenReturn(lines);
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            InboundInspectionReportItem item = response.getBody().getFirst();
            assertThat(item.getLotNumber()).isEqualTo("LOT-001");
            assertThat(item.getExpiryDate()).isEqualTo(LocalDate.of(2027, 3, 14));
        }

        @Test
        @DisplayName("ケース入数が0の場合はケース数が0になる")
        void generate_zeroCaseQuantity_casQuantityIsZero() {
            InboundSlip slip = createSlip();
            List<InboundSlipLine> lines = List.of(createLine(slip, 1, 100L, 50, 50));
            when(inboundReportRepository.findInspectionReportData(1L)).thenReturn(lines);
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 0)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            InboundInspectionReportItem item = response.getBody().getFirst();
            assertThat(item.getCaseQuantity()).isZero();
            assertThat(item.getPlannedQuantityCas()).isZero();
            assertThat(item.getInspectedQuantityCas()).isNull();
        }

        @Test
        @DisplayName("商品マスタが見つからない場合はケース入数1で計算する")
        void generate_productNotFound_usesCaseQuantityOne() {
            InboundSlip slip = createSlip();
            List<InboundSlipLine> lines = List.of(createLine(slip, 1, 999L, 50, 50));
            when(inboundReportRepository.findInspectionReportData(1L)).thenReturn(lines);
            when(productRepository.findAllById(any())).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InboundInspectionReportItem>> response =
                    service.generate(1L, ReportFormat.JSON);

            InboundInspectionReportItem item = response.getBody().getFirst();
            assertThat(item.getCaseQuantity()).isEqualTo(1);
            assertThat(item.getPlannedQuantityCas()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("generate - CSV形式")
    class GenerateCsv {

        @Test
        @DisplayName("CSV形式でReportMetaのcsvRowMapperが呼び出される")
        void generate_csvFormat_usesRowMapper() {
            InboundSlip slip = createSlip();
            List<InboundSlipLine> lines = List.of(
                    createLine(slip, 1, 100L, 50, 50));
            when(inboundReportRepository.findInspectionReportData(1L)).thenReturn(lines);
            when(productRepository.findAllById(any())).thenReturn(List.of(createProduct(100L, 10)));
            when(reportExportService.export(anyList(), eq(ReportFormat.CSV), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        // csvRowMapperが動作することを確認
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(14);
                        assertThat(row[0]).isEqualTo("INB-20260327-0001");
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, ReportFormat.CSV);

            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any(ReportMeta.class));
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("伝票が存在しない場合は ResourceNotFoundException がスローされる")
        void generate_slipNotFound_throwsResourceNotFoundException() {
            when(inboundReportRepository.findInspectionReportData(999L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.generate(999L, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("INBOUND_SLIP_NOT_FOUND"));
        }
    }
}
