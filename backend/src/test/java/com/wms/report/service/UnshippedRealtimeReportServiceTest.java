package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnshippedRealtimeReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.OutboundReportRepository;
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
@DisplayName("UnshippedRealtimeReportService - RPT-15 未出荷リスト（リアルタイム）")
class UnshippedRealtimeReportServiceTest {

    @Mock
    private OutboundReportRepository outboundReportRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private BusinessDateProvider businessDateProvider;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private UnshippedRealtimeReportService service;

    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", "password"));
        warehouse = new Warehouse();
        setWarehouseId(warehouse, 1L);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("東京第一倉庫");
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

    /**
     * 未出荷データ行を作成する。
     * [0] slipNumber, [1] partnerName, [2] plannedDate,
     * [3] productCode, [4] productName, [5] orderedQty, [6] status
     */
    private Object[] createDataRow(String slipNumber, String partnerName, LocalDate plannedDate,
                                   String productCode, String productName, Integer orderedQty,
                                   String status) {
        return new Object[]{
                slipNumber,
                partnerName,
                plannedDate != null ? java.sql.Date.valueOf(plannedDate) : null,
                productCode,
                productName,
                orderedQty,
                status
        };
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成され、遅延日数が計算される")
        void generate_success_returnsItems() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] row = createDataRow("OUT-20260312-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 12), "P-001", "商品A", 100, "PENDING");
            when(outboundReportRepository.findUnshippedRealtimeData(eq(1L), any()))
                    .thenReturn(List.<Object[]>of(row));

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnshippedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            UnshippedRealtimeReportItem item = response.getBody().getFirst();
            assertThat(item.getSlipNumber()).isEqualTo("OUT-20260312-0001");
            assertThat(item.getCustomerName()).isEqualTo("テスト出荷先A");
            assertThat(item.getPlannedDate()).isEqualTo(LocalDate.of(2026, 3, 12));
            assertThat(item.getProductCode()).isEqualTo("P-001");
            assertThat(item.getProductName()).isEqualTo("商品A");
            assertThat(item.getOrderedQty()).isEqualTo(100);
            assertThat(item.getStatus()).isEqualTo("PENDING");
            assertThat(item.getStatusLabel()).isEqualTo("受注済");
            assertThat(item.getDelayDays()).isEqualTo(5);
        }

        @Test
        @DisplayName("asOfDate未指定時は営業日が使用される")
        void generate_withNullAsOfDate_usesBusinessDate() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 17));
            when(outboundReportRepository.findUnshippedRealtimeData(eq(1L), eq(LocalDate.of(2026, 3, 17))))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, ReportFormat.JSON);

            verify(outboundReportRepository).findUnshippedRealtimeData(1L, LocalDate.of(2026, 3, 17));
        }

        @Test
        @DisplayName("asOfDateが明示的に指定された場合、その日付が使用される")
        void generate_withExplicitAsOfDate_usesProvidedDate() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findUnshippedRealtimeData(eq(1L), eq(LocalDate.of(2026, 3, 20))))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 20), ReportFormat.JSON);

            verify(outboundReportRepository).findUnshippedRealtimeData(1L, LocalDate.of(2026, 3, 20));
        }

        @Test
        @DisplayName("遅延日数が正しく計算される（plannedDate=3/12, asOfDate=3/17 → 5日）")
        void generate_delayDaysCalculation_correct() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] row = createDataRow("OUT-20260312-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 12), "P-001", "商品A", 50, "ALLOCATED");
            when(outboundReportRepository.findUnshippedRealtimeData(any(), any()))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnshippedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getDelayDays()).isEqualTo(5);
        }

        @Test
        @DisplayName("データが空の場合、空リストが返される")
        void generate_emptyData_returnsEmptyList() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findUnshippedRealtimeData(eq(1L), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnshippedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("null PLANNED_DATE → delayDays=0")
        void generate_nullPlannedDate_delayDaysZero() {
            Object[] row = createDataRow("OUT-050", "出荷先A", null, "P-001", "商品A", 5, "PENDING");
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findUnshippedRealtimeData(eq(1L), any()))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            var response = service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);
            assertThat(response.getBody().get(0).getPlannedDate()).isNull();
            assertThat(response.getBody().get(0).getDelayDays()).isEqualTo(0);
        }

        @Test
        @DisplayName("null ORDERED_QTY → orderedQty=0")
        void generate_nullOrderedQty_defaultsToZero() {
            Object[] row = createDataRow("OUT-050", "出荷先A", LocalDate.of(2026, 3, 12), "P-001", "商品A", null, "PENDING");
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findUnshippedRealtimeData(eq(1L), any()))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            var response = service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);
            assertThat(response.getBody().get(0).getOrderedQty()).isEqualTo(0);
        }

        @Test
        @DisplayName("ステータスラベルが正しくマッピングされる")
        void generate_statusLabel_mappedCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] row = createDataRow("OUT-001", "出荷先", LocalDate.of(2026, 3, 15),
                    "P-001", "商品A", 10, "ALLOCATED");
            when(outboundReportRepository.findUnshippedRealtimeData(any(), any()))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<UnshippedRealtimeReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getStatusLabel()).isEqualTo("引当完了");
        }

        @Test
        @DisplayName("条件サマリーに基準日が含まれる")
        void generate_conditionsSummary_includesAsOfDate() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findUnshippedRealtimeData(any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .isEqualTo("基準日: 2026-03-17");
        }
    }

    @Nested
    @DisplayName("generate - CSV")
    class GenerateCsv {

        @Test
        @DisplayName("csvRowMapperが遅延日数を「N日」形式でフォーマットする")
        void generate_csvRowMapper_formatsCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] row = createDataRow("OUT-20260312-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 12), "P-001", "商品A", 100, "PENDING");
            when(outboundReportRepository.findUnshippedRealtimeData(any(), any()))
                    .thenReturn(List.<Object[]>of(row));

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] csvRow = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(csvRow).hasSize(8);
                        assertThat(csvRow[0]).isEqualTo("OUT-20260312-0001");  // 伝票番号
                        assertThat(csvRow[1]).isEqualTo("テスト出荷先A");        // 出荷先名
                        assertThat(csvRow[2]).isEqualTo("2026-03-12");          // 出荷予定日
                        assertThat(csvRow[3]).isEqualTo("P-001");              // 商品コード
                        assertThat(csvRow[4]).isEqualTo("商品A");               // 商品名
                        assertThat(csvRow[5]).isEqualTo("100");                // 数量
                        assertThat(csvRow[6]).isEqualTo("受注済");              // ステータス
                        assertThat(csvRow[7]).isEqualTo("5日");                // 遅延日数
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
        }

        @Test
        @DisplayName("delayDays=null → CSV出力はemダッシュ")
        void generate_csvRowMapper_nullDelayDays_showsDash() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            when(outboundReportRepository.findUnshippedRealtimeData(any(), any()))
                    .thenReturn(List.<Object[]>of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        // delayDaysがnullのアイテムを手動構築してcsvRowMapperをテスト
                        var item = new com.wms.generated.model.UnshippedRealtimeReportItem();
                        item.setSlipNumber("OUT-001");
                        item.setPlannedDate(LocalDate.of(2026, 3, 12));
                        item.setProductCode("P-001");
                        item.setProductName("商品A");
                        item.setOrderedQty(5);
                        item.setStatusLabel("受注済");
                        item.setDelayDays(null); // null
                        String[] csvRow = meta.csvRowMapper().apply(item);
                        assertThat(csvRow[7]).isEqualTo("\u2014"); // emダッシュ
                        return ResponseEntity.ok(inv.getArgument(0));
                    });

            service.generate(1L, LocalDate.of(2026, 3, 17), ReportFormat.JSON);
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
