package com.wms.report.service;

import com.wms.generated.model.InventoryCorrectionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InventoryMovementReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.util.BusinessDateProvider;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
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
@DisplayName("InventoryCorrectionReportService")
class InventoryCorrectionReportServiceTest {

    @Mock
    private InventoryMovementReportRepository movementRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessDateProvider businessDateProvider;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private InventoryCorrectionReportService service;

    private Warehouse warehouse;

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

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

    private InventoryMovement createCorrectionMovement(int quantity, int quantityAfter,
                                                        String reason, Long executedBy) {
        return InventoryMovement.builder()
                .id(1L)
                .warehouseId(1L)
                .locationId(10L)
                .locationCode("A-01-01")
                .productId(100L)
                .productCode("P-001")
                .productName("商品A")
                .unitType("CAS")
                .movementType("CORRECTION")
                .quantity(quantity)
                .quantityAfter(quantityAfter)
                .correctionReason(reason)
                .executedAt(OffsetDateTime.of(2026, 3, 10, 14, 30, 0, 0, JST.getRules().getOffset(java.time.Instant.now())))
                .executedBy(executedBy)
                .build();
    }

    private User createUser(Long id, String fullName) {
        User user = User.builder().fullName(fullName).build();
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_withValidParams_returnsReportData() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(movementRepository.findCorrectionReportData(any(), any(), any()))
                    .thenReturn(List.of(createCorrectionMovement(-1, 9, "棚卸差異調整", 50L)));
            when(userRepository.findAllById(any())).thenReturn(List.of(createUser(50L, "田中 太郎")));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InventoryCorrectionReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            InventoryCorrectionReportItem item = response.getBody().getFirst();
            assertThat(item.getCorrectionDate()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(item.getLocationCode()).isEqualTo("A-01-01");
            assertThat(item.getQuantityBefore()).isEqualTo(10); // 9 - (-1) = 10
            assertThat(item.getQuantityAfter()).isEqualTo(9);
            assertThat(item.getQuantityChange()).isEqualTo(-1);
            assertThat(item.getReason()).isEqualTo("棚卸差異調整");
            assertThat(item.getOperatorName()).isEqualTo("田中 太郎");
        }

        @Test
        @DisplayName("日付未指定時はデフォルト値が使用される")
        void generate_nullDates_usesDefaults() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 17));
            when(movementRepository.findCorrectionReportData(any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .isEqualTo("期間: 2026-03-01 ～ 2026-03-17");
        }

        @Test
        @DisplayName("実施者が見つからない場合はoperatorNameがnull")
        void generate_userNotFound_operatorNameNull() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(movementRepository.findCorrectionReportData(any(), any(), any()))
                    .thenReturn(List.of(createCorrectionMovement(-1, 9, "テスト", 999L)));
            when(userRepository.findAllById(any())).thenReturn(List.of());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<InventoryCorrectionReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getOperatorName()).isNull();
        }

        @Test
        @DisplayName("CSV形式でcsvRowMapperが動作する")
        void generate_csvFormat_usesRowMapper() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(movementRepository.findCorrectionReportData(any(), any(), any()))
                    .thenReturn(List.of(createCorrectionMovement(5, 25, "入荷漏れ補正", 50L)));
            when(userRepository.findAllById(any())).thenReturn(List.of(createUser(50L, "田中 太郎")));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(10);
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), ReportFormat.CSV);
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

            assertThatThrownBy(() -> service.generate(999L, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }
}
