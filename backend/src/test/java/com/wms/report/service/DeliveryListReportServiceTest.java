package com.wms.report.service;

import com.wms.generated.model.DeliveryListLineItem;
import com.wms.generated.model.DeliveryListReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.OutboundReportRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryListReportService - RPT-14 配送リスト")
class DeliveryListReportServiceTest {

    @Mock
    private OutboundReportRepository outboundReportRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private DeliveryListReportService service;

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
     * ヘッダー行データを作成する。
     * [0] id, [1] slipNumber, [2] partnerName, [3] plannedDate,
     * [4] status, [5] carrier, [6] trackingNumber
     */
    private Object[] createHeaderRow(Long id, String slipNumber, String partnerName,
                                     LocalDate plannedDate, String status,
                                     String carrier, String trackingNumber) {
        return new Object[]{
                id,
                slipNumber,
                partnerName,
                plannedDate != null ? java.sql.Date.valueOf(plannedDate) : null,
                status,
                carrier,
                trackingNumber,
                "東京都千代田区1-1-1"
        };
    }

    /**
     * 明細行データを作成する。
     * [0] outboundSlipId, [1] productCode, [2] productName, [3] unitType, [4] orderedQty
     */
    private Object[] createLineRow(Long slipId, String productCode, String productName,
                                   String unitType, Integer orderedQty) {
        return new Object[]{slipId, productCode, productName, unitType, orderedQty};
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成され、各フィールドが正しく設定される")
        void generate_success_returnsItems() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] header = createHeaderRow(10L, "OUT-20260315-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 15), "ALLOCATED", "ヤマト運輸", "1234-5678-9012");
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(header));

            Object[] line1 = createLineRow(10L, "P-001", "商品A", "PCS", 50);
            Object[] line2 = createLineRow(10L, "P-002", "商品B", "CAS", 20);
            when(outboundReportRepository.findDeliveryListLineData(List.of(10L)))
                    .thenReturn(List.<Object[]>of(line1, line2));

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DeliveryListReportItem>> response =
                    service.generate(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                            "ALLOCATED", "ヤマト", ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(1);
            DeliveryListReportItem item = response.getBody().getFirst();
            assertThat(item.getSlipNumber()).isEqualTo("OUT-20260315-0001");
            assertThat(item.getCustomerName()).isEqualTo("テスト出荷先A");
            assertThat(item.getPlannedShipDate()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(item.getStatus()).isEqualTo("ALLOCATED");
            assertThat(item.getStatusLabel()).isEqualTo("引当完了");
            assertThat(item.getCarrier()).isEqualTo("ヤマト運輸");
            assertThat(item.getTrackingNumber()).isEqualTo("1234-5678-9012");
            assertThat(item.getLines()).hasSize(2);
            assertThat(item.getLines().get(0).getProductCode()).isEqualTo("P-001");
            assertThat(item.getLines().get(0).getQuantity()).isEqualTo(50);
            assertThat(item.getLines().get(1).getProductCode()).isEqualTo("P-002");
            assertThat(item.getTotalQuantityPcs()).isEqualTo(70);
        }

        @Test
        @DisplayName("配送業者がnullの場合、carrierにnullが設定される")
        void generate_withNullCarrier_setsNull() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] header = createHeaderRow(10L, "OUT-20260315-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 15), "ALLOCATED", null, null);
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(header));

            when(outboundReportRepository.findDeliveryListLineData(List.of(10L)))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DeliveryListReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            DeliveryListReportItem item = response.getBody().getFirst();
            assertThat(item.getCarrier()).isNull();
            assertThat(item.getTrackingNumber()).isNull();
        }

        @Test
        @DisplayName("null PLANNED_DATE → plannedShipDate=null")
        void generate_nullPlannedDate_setsNull() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] header = createHeaderRow(10L, "OUT-20260315-0001", "テスト出荷先A",
                    null, "ALLOCATED", "ヤマト運輸", "1234-5678-9012");
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(header));

            when(outboundReportRepository.findDeliveryListLineData(List.of(10L)))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DeliveryListReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getPlannedShipDate()).isNull();
        }

        @Test
        @DisplayName("null ORDERED_QTY → quantity=0")
        void generate_nullOrderedQty_defaultsToZero() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] header = createHeaderRow(10L, "OUT-20260315-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 15), "ALLOCATED", null, null);
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(header));

            Object[] line = createLineRow(10L, "P-001", "商品A", "PCS", null);
            when(outboundReportRepository.findDeliveryListLineData(List.of(10L)))
                    .thenReturn(List.<Object[]>of(line));

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DeliveryListReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().getFirst().getLines().get(0).getQuantity()).isEqualTo(0);
            assertThat(response.getBody().getFirst().getTotalQuantityPcs()).isEqualTo(0);
        }

        @Test
        @DisplayName("条件がすべてnullの場合、conditionsSummaryは空文字")
        void generate_allNullConditions_emptySummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEmpty();
        }

        @Test
        @DisplayName("ステータスのみ指定時、conditionsSummaryにステータスのみ含まれる")
        void generate_statusOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, "ALLOCATED", null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("ステータス: 引当完了");
        }

        @Test
        @DisplayName("配送業者のみ指定時、conditionsSummaryに配送業者のみ含まれる")
        void generate_carrierOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, "ヤマト", ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("配送業者: ヤマト");
        }

        @Test
        @DisplayName("dateFromのみ指定時、dateToは—で表示")
        void generate_dateFromOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 1), null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("期間: 2026-03-01 〜 —");
        }

        @Test
        @DisplayName("dateToのみ指定時、dateFromは—で表示")
        void generate_dateToOnly_conditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, LocalDate.of(2026, 3, 31), null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("期間: — 〜 2026-03-31");
        }

        @Test
        @DisplayName("ヘッダーデータが空の場合、空リストが返される")
        void generate_emptyData_returnsEmptyList() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<DeliveryListReportItem>> response =
                    service.generate(1L, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("配送業者が指定された場合、LIKE用に%で囲まれる")
        void generate_carrierPartialMatch_usesLikePattern() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), eq("%ヤマト%")))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, "ヤマト", ReportFormat.JSON);

            verify(outboundReportRepository).findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), eq("%ヤマト%"));
        }

        @Test
        @DisplayName("条件サマリーに期間・ステータス・配送業者が正しく含まれる")
        void generate_conditionsSummary_formatsCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    "ALLOCATED", "ヤマト", ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            String summary = metaCaptor.getValue().conditionsSummary();
            assertThat(summary).contains("期間: 2026-03-01 〜 2026-03-31");
            assertThat(summary).contains("ステータス: 引当完了");
            assertThat(summary).contains("配送業者: ヤマト");
        }
    }

    @Nested
    @DisplayName("generate - CSV")
    class GenerateCsv {

        @Test
        @DisplayName("csvRowMapperが明細行を含む行を正しくフォーマットする")
        void generate_csvRowMapper_formatsCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] header = createHeaderRow(10L, "OUT-20260315-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 15), "ALLOCATED", "ヤマト運輸", "1234-5678-9012");
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(header));

            Object[] line = createLineRow(10L, "P-001", "商品A", "PCS", 50);
            when(outboundReportRepository.findDeliveryListLineData(List.of(10L)))
                    .thenReturn(List.<Object[]>of(line));

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(11);
                        assertThat(row[0]).isEqualTo("OUT-20260315-0001");   // 伝票番号
                        assertThat(row[1]).isEqualTo("テスト出荷先A");         // 出荷先名
                        assertThat(row[3]).isEqualTo("2026-03-15");           // 出荷予定日
                        assertThat(row[4]).isEqualTo("引当完了");              // ステータス
                        assertThat(row[5]).isEqualTo("ヤマト運輸");            // 配送業者
                        assertThat(row[6]).isEqualTo("1234-5678-9012");      // 送り状番号
                        assertThat(row[7]).isEqualTo("P-001");               // 商品コード
                        assertThat(row[8]).isEqualTo("商品A");                // 商品名
                        assertThat(row[9]).isEqualTo("PCS");                 // 荷姿
                        assertThat(row[10]).isEqualTo("50");                 // 数量
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, null, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
        }

        @Test
        @DisplayName("明細行が空の場合、CSV後半カラムが空文字になる")
        void generate_csvRowMapper_emptyLines_returnsEmptyColumns() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] header = createHeaderRow(10L, "OUT-20260315-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 15), "ALLOCATED", null, null);
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(header));

            when(outboundReportRepository.findDeliveryListLineData(List.of(10L)))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(11);
                        // 明細行がないため後半4カラムは空文字
                        assertThat(row[7]).isEmpty();  // 商品コード
                        assertThat(row[8]).isEmpty();  // 商品名
                        assertThat(row[9]).isEmpty();  // 荷姿
                        assertThat(row[10]).isEmpty(); // 数量
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, null, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
        }

        @Test
        @DisplayName("lines=nullの場合、CSV後半カラムが空文字になる")
        void generate_csvRowMapper_nullLines_returnsEmptyColumns() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] header = createHeaderRow(10L, "OUT-20260315-0001", "テスト出荷先A",
                    LocalDate.of(2026, 3, 15), "ALLOCATED", null, null);
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(header));

            when(outboundReportRepository.findDeliveryListLineData(List.of(10L)))
                    .thenReturn(List.of());

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        // Create an item with null lines to cover the lines != null branch
                        DeliveryListReportItem nullLinesItem = new DeliveryListReportItem();
                        nullLinesItem.setSlipNumber("OUT-NULL");
                        nullLinesItem.setLines(null);
                        String[] row = meta.csvRowMapper().apply(nullLinesItem);
                        assertThat(row).hasSize(11);
                        assertThat(row[0]).isEqualTo("OUT-NULL");
                        assertThat(row[7]).isEmpty();
                        assertThat(row[8]).isEmpty();
                        assertThat(row[9]).isEmpty();
                        assertThat(row[10]).isEmpty();
                        return ResponseEntity.ok(inv.getArgument(0));
                    });

            service.generate(1L, null, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), any(), any());
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

    @Nested
    @DisplayName("flattenForCsv")
    class FlattenForCsvTest {

        @Test
        @DisplayName("複数明細行を持つ伝票がフラット展開される")
        void flattenForCsv_multipleLines_expandsToMultipleRows() {
            DeliveryListReportItem item = new DeliveryListReportItem();
            item.setSlipNumber("OUT-001");
            item.setCustomerName("出荷先A");

            DeliveryListLineItem line1 = new DeliveryListLineItem();
            line1.setProductCode("P-001");
            line1.setQuantity(3);
            DeliveryListLineItem line2 = new DeliveryListLineItem();
            line2.setProductCode("P-002");
            line2.setQuantity(5);
            item.setLines(List.of(line1, line2));

            List<DeliveryListReportItem> result = DeliveryListReportService.flattenForCsv(List.of(item));

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSlipNumber()).isEqualTo("OUT-001");
            assertThat(result.get(0).getLines().get(0).getProductCode()).isEqualTo("P-001");
            assertThat(result.get(1).getSlipNumber()).isEqualTo("OUT-001");
            assertThat(result.get(1).getLines().get(0).getProductCode()).isEqualTo("P-002");
        }

        @Test
        @DisplayName("明細0件の伝票はそのまま1行で出力")
        void flattenForCsv_emptyLines_keepsSingleRow() {
            DeliveryListReportItem item = new DeliveryListReportItem();
            item.setSlipNumber("OUT-002");
            item.setLines(List.of());

            List<DeliveryListReportItem> result = DeliveryListReportService.flattenForCsv(List.of(item));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSlipNumber()).isEqualTo("OUT-002");
        }

        @Test
        @DisplayName("lines=nullの伝票はそのまま1行で出力")
        void flattenForCsv_nullLines_keepsSingleRow() {
            DeliveryListReportItem item = new DeliveryListReportItem();
            item.setSlipNumber("OUT-003");
            item.setLines(null);

            List<DeliveryListReportItem> result = DeliveryListReportService.flattenForCsv(List.of(item));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("CSV出力時にflattenForCsvが適用される")
        void generate_csvFormat_flattensItems() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

            Object[] header = createHeaderRow(10L, "OUT-001", "出荷先A",
                    LocalDate.of(2026, 3, 15), "ALLOCATED", null, null);
            when(outboundReportRepository.findDeliveryListHeaderData(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(header));

            Object[] line1 = createLineRow(10L, "P-001", "商品A", "CAS", 3);
            Object[] line2 = createLineRow(10L, "P-002", "商品B", "PCS", 5);
            when(outboundReportRepository.findDeliveryListLineData(anyList()))
                    .thenReturn(List.<Object[]>of(line1, line2));

            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        List<?> data = inv.getArgument(0);
                        // CSV出力時は2明細→2行にフラット展開される
                        assertThat(data).hasSize(2);
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, null, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any());
        }
    }
}
