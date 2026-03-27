package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.ReturnReason;
import com.wms.generated.model.ReturnType;
import com.wms.generated.model.ReturnsReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.ReturnsReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnsReportService")
class ReturnsReportServiceTest {

    @Mock
    private ReturnsReportRepository returnsReportRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private ReturnsReportService service;

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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    @SafeVarargs
    private static List<Object[]> listOf(Object[]... rows) {
        return Arrays.asList(rows);
    }

    private Object[] createRow(String returnNumber, String returnType, LocalDate returnDate,
                                String productCode, String productName, int quantity,
                                String unitType, String returnReason, String returnReasonNote,
                                String relatedSlipNumber, String partnerName) {
        return new Object[]{
                returnNumber,
                returnType,
                returnDate != null ? Date.valueOf(returnDate) : null,
                productCode,
                productName,
                quantity,
                unitType,
                returnReason,
                returnReasonNote,
                relatedSlipNumber,
                partnerName
        };
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("正常にレポートデータが生成される")
        void generate_success_returnsItems() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(
                    eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(listOf(
                            createRow("RTN-2026-0010", "INBOUND", LocalDate.of(2026, 3, 5),
                                    "P-001", "商品A", 10, "CASE", "QUALITY_DEFECT",
                                    "外箱破損あり", "INB-2026-00120", "テスト仕入先A"),
                            createRow("RTN-2026-0015", "OUTBOUND", LocalDate.of(2026, 3, 10),
                                    "P-002", "商品B", 3, "BALL", "DAMAGED",
                                    null, "OUT-2026-0050", "テスト仕入先B")));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ReturnsReportItem>> response =
                    service.generate(1L, null, null, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).hasSize(2);

            ReturnsReportItem item1 = response.getBody().get(0);
            assertThat(item1.getReturnNumber()).isEqualTo("RTN-2026-0010");
            assertThat(item1.getReturnType()).isEqualTo(ReturnType.INBOUND);
            assertThat(item1.getReturnTypeLabel()).isEqualTo("入荷返品");
            assertThat(item1.getReturnDate()).isEqualTo(LocalDate.of(2026, 3, 5));
            assertThat(item1.getProductCode()).isEqualTo("P-001");
            assertThat(item1.getProductName()).isEqualTo("商品A");
            assertThat(item1.getQuantity()).isEqualTo(10);
            assertThat(item1.getUnitType()).isEqualTo("CAS");
            assertThat(item1.getReturnReason()).isEqualTo(ReturnReason.QUALITY_DEFECT);
            assertThat(item1.getReturnReasonLabel()).isEqualTo("品質不良");
            assertThat(item1.getReturnReasonNote()).isEqualTo("外箱破損あり");
            assertThat(item1.getRelatedSlipNumber()).isEqualTo("INB-2026-00120");
            assertThat(item1.getPartnerName()).isEqualTo("テスト仕入先A");

            ReturnsReportItem item2 = response.getBody().get(1);
            assertThat(item2.getReturnType()).isEqualTo(ReturnType.OUTBOUND);
            assertThat(item2.getReturnTypeLabel()).isEqualTo("出荷返品");
            assertThat(item2.getUnitType()).isEqualTo("BAL");
            assertThat(item2.getReturnReason()).isEqualTo(ReturnReason.DAMAGED);
            assertThat(item2.getReturnReasonLabel()).isEqualTo("破損");
            assertThat(item2.getReturnReasonNote()).isNull();
        }

        @Test
        @DisplayName("データが空の場合は空リストが返される")
        void generate_emptyData_returnsEmptyList() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(
                    eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ReturnsReportItem>> response =
                    service.generate(1L, null, null, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("ReportMetaが正しく設定される")
        void generate_success_reportMetaFields() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(
                    eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            ReportMeta meta = metaCaptor.getValue();
            assertThat(meta.reportTitle()).isEqualTo("返品レポート");
            assertThat(meta.templateName()).isEqualTo("rpt-18-returns");
            assertThat(meta.warehouseName()).isEqualTo("東京第一倉庫 (WH-001)");
        }

        @Test
        @DisplayName("フィルタ条件がパラメータとして正しく渡される")
        void generate_withFilters_passesParametersCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            LocalDate dateFrom = LocalDate.of(2026, 3, 1);
            LocalDate dateTo = LocalDate.of(2026, 3, 31);
            when(returnsReportRepository.findReturnsReportData(
                    eq(1L), eq("INBOUND"), eq(dateFrom), eq(dateTo), eq(100L), eq(200L), eq("QUALITY_DEFECT")))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, ReturnType.INBOUND, dateFrom, dateTo,
                    100L, 200L, ReturnReason.QUALITY_DEFECT, ReportFormat.JSON);

            verify(returnsReportRepository).findReturnsReportData(
                    1L, "INBOUND", dateFrom, dateTo, 100L, 200L, "QUALITY_DEFECT");
        }

        @Test
        @DisplayName("条件サマリーに期間・返品種別・返品理由が含まれる")
        void generate_withAllConditions_conditionsSummaryContainsAll() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, ReturnType.INBOUND,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    null, null, ReturnReason.DAMAGED, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            String conditions = metaCaptor.getValue().conditionsSummary();
            assertThat(conditions).contains("期間: 2026-03-01 ～ 2026-03-31");
            assertThat(conditions).contains("返品種別: 入荷返品");
            assertThat(conditions).contains("返品理由: 破損");
        }

        @Test
        @DisplayName("条件なしの場合はconditionsSummaryが空文字列")
        void generate_noConditions_emptyConditionsSummary() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEmpty();
        }

        @Test
        @DisplayName("dateFromのみ指定の場合のconditionsSummary")
        void generate_dateFromOnly_conditionsSummaryShowsDash() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, LocalDate.of(2026, 3, 1), null,
                    null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).contains("期間: 2026-03-01 ～ —");
        }

        @Test
        @DisplayName("returnTypeのみ指定の場合はsbが空から始まる")
        void generate_returnTypeOnly_conditionsSummaryStartsWithType() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, ReturnType.OUTBOUND, null, null,
                    null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("返品種別: 出荷返品");
        }

        @Test
        @DisplayName("returnReasonのみ指定の場合はsbが空から始まる")
        void generate_returnReasonOnly_conditionsSummaryStartsWithReason() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null,
                    null, null, ReturnReason.OTHER, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).isEqualTo("返品理由: その他");
        }

        @Test
        @DisplayName("dateToのみ指定の場合のconditionsSummary")
        void generate_dateToOnly_conditionsSummaryShowsDash() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, LocalDate.of(2026, 3, 31),
                    null, null, null, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary()).contains("期間: — ～ 2026-03-31");
        }

        @Test
        @DisplayName("nullの返品種別/理由のデータが正しく処理される")
        void generate_nullReturnTypeAndReason_handledGracefully() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            Object[] row = new Object[]{
                    "RTN-001", null, Date.valueOf("2026-03-05"),
                    "P-001", "商品A", 10, "PIECE", null, null, null, null
            };
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ReturnsReportItem>> response =
                    service.generate(1L, null, null, null, null, null, null, ReportFormat.JSON);

            ReturnsReportItem item = response.getBody().get(0);
            assertThat(item.getReturnType()).isNull();
            assertThat(item.getReturnTypeLabel()).isNull();
            assertThat(item.getReturnReason()).isNull();
            assertThat(item.getReturnReasonLabel()).isNull();
            assertThat(item.getUnitType()).isEqualTo("PCS");
        }

        @Test
        @DisplayName("nullの返品日が正しく処理される")
        void generate_nullReturnDate_setsNull() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            Object[] row = new Object[]{
                    "RTN-001", "INBOUND", null,
                    "P-001", "商品A", 10, "CASE", "QUALITY_DEFECT", null, null, null
            };
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ReturnsReportItem>> response =
                    service.generate(1L, null, null, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().get(0).getReturnDate()).isNull();
        }

        @Test
        @DisplayName("null数量は0に変換される")
        void generate_nullQuantity_defaultsToZero() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            Object[] row = new Object[]{
                    "RTN-001", "INBOUND", Date.valueOf("2026-03-05"),
                    "P-001", "商品A", null, "CASE", "QUALITY_DEFECT", null, null, null
            };
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<ReturnsReportItem>> response =
                    service.generate(1L, null, null, null, null, null, null, ReportFormat.JSON);

            assertThat(response.getBody().get(0).getQuantity()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("generate - CSV出力")
    class GenerateCsv {

        @Test
        @DisplayName("CSVヘッダーが期待通り")
        void generate_csvHeaders_matchExpected() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(1L, null, null, null, null, null, null, ReportFormat.CSV);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), metaCaptor.capture());
            assertThat(metaCaptor.getValue().csvHeaders()).containsExactly(
                    "返品伝票番号", "返品種別", "返品種別名", "返品日",
                    "商品コード", "商品名", "数量", "荷姿",
                    "返品理由", "返品理由名", "返品理由備考",
                    "関連伝票番号", "仕入先名");
        }

        @Test
        @DisplayName("csvRowMapperが正しくフォーマットする")
        void generate_csvRowMapper_formatsCorrectly() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(listOf(
                            createRow("RTN-2026-0010", "INBOUND", LocalDate.of(2026, 3, 5),
                                    "P-001", "商品A", 10, "CASE", "QUALITY_DEFECT",
                                    "外箱破損", "INB-001", "仕入先A")));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(13);
                        assertThat(row[0]).isEqualTo("RTN-2026-0010");
                        assertThat(row[1]).isEqualTo("INBOUND");
                        assertThat(row[2]).isEqualTo("入荷返品");
                        assertThat(row[3]).isEqualTo("2026-03-05");
                        assertThat(row[4]).isEqualTo("P-001");
                        assertThat(row[5]).isEqualTo("商品A");
                        assertThat(row[8]).isEqualTo("QUALITY_DEFECT");
                        assertThat(row[9]).isEqualTo("品質不良");
                        assertThat(row[10]).isEqualTo("外箱破損");
                        assertThat(row[11]).isEqualTo("INB-001");
                        assertThat(row[12]).isEqualTo("仕入先A");
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, null, null, null, null, null, null, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any());
        }

        @Test
        @DisplayName("csvRowMapperでnull値がダッシュに変換される")
        void generate_csvRowMapper_nullValuesFormattedAsDash() {
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            Object[] row = new Object[]{
                    "RTN-001", null, null,
                    "P-001", "商品A", 10, null, null, null, null, null
            };
            when(returnsReportRepository.findReturnsReportData(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(row));
            when(reportExportService.export(anyList(), any(ReportFormat.class), any(ReportMeta.class)))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] csvRow = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(csvRow[1]).isEqualTo("—");  // null returnType
                        assertThat(csvRow[2]).isEqualTo("—");  // null returnTypeLabel
                        assertThat(csvRow[3]).isEqualTo("—");  // null returnDate
                        assertThat(csvRow[8]).isEqualTo("—");  // null returnReason
                        assertThat(csvRow[9]).isEqualTo("—");  // null returnReasonLabel
                        assertThat(csvRow[10]).isEqualTo("—"); // null returnReasonNote
                        assertThat(csvRow[11]).isEqualTo("—"); // null relatedSlipNumber
                        assertThat(csvRow[12]).isEqualTo("—"); // null partnerName
                        return ResponseEntity.ok(data);
                    });

            service.generate(1L, null, null, null, null, null, null, ReportFormat.CSV);
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("倉庫が存在しない場合はResourceNotFoundException")
        void generate_warehouseNotFound_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(
                    999L, null, null, null, null, null, null, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("toUnitTypeLabel")
    class ToUnitTypeLabel {

        @ParameterizedTest
        @CsvSource({"CASE,CAS", "BALL,BAL", "PIECE,PCS"})
        @DisplayName("既知の荷姿コードが正しくラベル変換される")
        void toUnitTypeLabel_knownTypes_convertedCorrectly(String input, String expected) {
            assertThat(ReturnsReportService.toUnitTypeLabel(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("不明な荷姿コードはそのまま返される")
        void toUnitTypeLabel_unknownType_returnedAsIs() {
            assertThat(ReturnsReportService.toUnitTypeLabel("UNKNOWN")).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("nullはnullが返される")
        void toUnitTypeLabel_null_returnsNull() {
            assertThat(ReturnsReportService.toUnitTypeLabel(null)).isNull();
        }
    }
}
