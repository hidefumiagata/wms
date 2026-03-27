package com.wms.report.service;

import com.wms.generated.model.InventoryTransitionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.ProductRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InventoryMovementReportRepository;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
@DisplayName("InventoryTransitionReportService")
class InventoryTransitionReportServiceTest {

    @Mock
    private InventoryMovementReportRepository movementRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BusinessDateProvider businessDateProvider;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private InventoryTransitionReportService service;

    private Warehouse warehouse;
    private Product product;

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", "password"));
        warehouse = new Warehouse();
        setEntityId(warehouse, 1L);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("東京第一倉庫");

        product = new Product();
        setEntityId(product, 100L);
        product.setProductCode("P-001");
        product.setProductName("商品A");
        product.setCaseQuantity(10);
        product.setBallQuantity(1);
        product.setStorageCondition("AMBIENT");
        product.setIsHazardous(false);
        product.setLotManageFlag(true);
        product.setExpiryManageFlag(false);
        product.setShipmentStopFlag(false);
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

    private InventoryMovement createMovement(String type, int quantity, int quantityAfter,
                                              String locationCode, String lotNumber) {
        return InventoryMovement.builder()
                .id(1L)
                .warehouseId(1L)
                .locationId(10L)
                .locationCode(locationCode)
                .productId(100L)
                .productCode("P-001")
                .productName("商品A")
                .unitType("CAS")
                .lotNumber(lotNumber)
                .movementType(type)
                .quantity(quantity)
                .quantityAfter(quantityAfter)
                .referenceType("INB")
                .referenceId(200L)
                .executedAt(OffsetDateTime.now(JST))
                .executedBy(1L)
                .build();
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_withValidParams_returnsReportData() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(movementRepository.findTransitionReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(createMovement("INBOUND", 10, 15, "A-01-01", "LOT-001")));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InventoryTransitionReportItem>> response =
                    service.generate(1L, 100L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            InventoryTransitionReportItem item = response.getBody().getFirst();
            assertThat(item.getMovementType()).isEqualTo("INBOUND");
            assertThat(item.getMovementTypeLabel()).isEqualTo("入庫");
            assertThat(item.getQuantityBefore()).isEqualTo(5); // 15 - 10
            assertThat(item.getQuantityChange()).isEqualTo(10);
            assertThat(item.getQuantityAfter()).isEqualTo(15);
            assertThat(item.getReferenceNumber()).isEqualTo("INB-200");
        }

        @Test
        @DisplayName("日付未指定時はデフォルト値が使用される")
        void generate_nullDates_usesDefaults() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 17));
            when(movementRepository.findTransitionReportData(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, 100L, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .contains("期間: 2026-03-01 ～ 2026-03-17");
        }

        @Test
        @DisplayName("未知のmovementTypeはそのまま表示される")
        void generate_unknownMovementType_usesRawValue() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(movementRepository.findTransitionReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(createMovement("UNKNOWN_TYPE", 5, 10, "A-01", null)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InventoryTransitionReportItem>> response =
                    service.generate(1L, 100L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getMovementTypeLabel()).isEqualTo("UNKNOWN_TYPE");
        }

        @Test
        @DisplayName("referenceTypeあり・referenceIdがnullの場合はtypeのみ")
        void generate_referenceTypeWithoutId_referenceNumberIsTypeOnly() {
            InventoryMovement m = InventoryMovement.builder()
                    .id(1L).warehouseId(1L).locationId(10L).locationCode("A-01")
                    .productId(100L).productCode("P-001").productName("商品A")
                    .unitType("CAS").movementType("MOVE_OUT").quantity(-3).quantityAfter(12)
                    .referenceType("MOVE").referenceId(null)
                    .executedAt(OffsetDateTime.now(JST)).executedBy(1L).build();
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(movementRepository.findTransitionReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(m));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InventoryTransitionReportItem>> response =
                    service.generate(1L, 100L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getReferenceNumber()).isEqualTo("MOVE");
        }

        @Test
        @DisplayName("referenceTypeがnullの場合はreferenceNumberがnull")
        void generate_nullReferenceType_nullReferenceNumber() {
            InventoryMovement m = InventoryMovement.builder()
                    .id(1L).warehouseId(1L).locationId(10L).locationCode("A-01")
                    .productId(100L).productCode("P-001").productName("商品A")
                    .unitType("CAS").movementType("CORRECTION").quantity(-1).quantityAfter(9)
                    .executedAt(OffsetDateTime.now(JST)).executedBy(1L).build();
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(movementRepository.findTransitionReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(m));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InventoryTransitionReportItem>> response =
                    service.generate(1L, 100L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getReferenceNumber()).isNull();
        }

        @Test
        @DisplayName("CSV形式でcsvRowMapperが動作する")
        void generate_csvFormat_usesRowMapper() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(movementRepository.findTransitionReportData(any(), any(), any(), any()))
                    .thenReturn(List.of(createMovement("OUTBOUND", -5, 7, "A-01", "LOT-001")));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(9);
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, 100L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), ReportFormat.CSV);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any());
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("倉庫が存在しない場合は ResourceNotFoundException")
        void generate_warehouseNotFound_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, 100L, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }

        @Test
        @DisplayName("商品が存在しない場合は ResourceNotFoundException")
        void generate_productNotFound_throwsException() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(1L, 999L, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("PRODUCT_NOT_FOUND"));
        }
    }
}
