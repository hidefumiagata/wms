package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.StocktakeResultReportItem;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.StocktakeReportRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StocktakeResultReportService")
class StocktakeResultReportServiceTest {

    @Mock
    private StocktakeReportRepository stocktakeReportRepository;
    @Mock
    private StocktakeHeaderRepository stocktakeHeaderRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private StocktakeResultReportService service;

    private Warehouse warehouse;
    private StocktakeHeader stocktakeHeader;

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
        stocktakeHeader.setStatus("CONFIRMED");
        stocktakeHeader.setStartedAt(OffsetDateTime.of(2026, 3, 15, 9, 0, 0, 0, ZoneOffset.ofHours(9)));
        stocktakeHeader.setConfirmedAt(OffsetDateTime.of(2026, 3, 17, 12, 0, 0, 0, ZoneOffset.ofHours(9)));
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

    private Object[] createResultRow(String locationCode, String productCode,
                                      String productName, String unitType,
                                      String lotNumber,
                                      Integer systemQty, Integer actualQty) {
        return new Object[]{
                locationCode,   // 0
                productCode,    // 1
                productName,    // 2
                unitType,       // 3
                lotNumber,      // 4
                systemQty,      // 5
                actualQty       // 6
        };
    }

    @Nested
    @DisplayName("generate - 正常系")
    class GenerateSuccess {

        @Test
        @DisplayName("差異計算が正しく行われる")
        void generate_calculatesCorrectDiff() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(
                            createResultRow("A-01", "P-001", "商品A", "CAS", "LOT-001", 10, 10),
                            createResultRow("A-01", "P-002", "商品B", "PCS", null, 50, 47),
                            createResultRow("A-02", "P-003", "商品C", "BAL", "LOT-003", 20, 22)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<StocktakeResultReportItem>> response =
                    service.generate(10L, ReportFormat.JSON);

            List<StocktakeResultReportItem> items = response.getBody();
            assertThat(items).hasSize(3);

            // 差異なし
            assertThat(items.get(0).getDiffQuantity()).isEqualTo(0);
            assertThat(items.get(0).getDiffRate()).isEqualTo(0.0);

            // マイナス差異
            assertThat(items.get(1).getDiffQuantity()).isEqualTo(-3);
            assertThat(items.get(1).getDiffRate()).isEqualTo(-6.0);

            // プラス差異
            assertThat(items.get(2).getDiffQuantity()).isEqualTo(2);
            assertThat(items.get(2).getDiffRate()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("systemQuantityが0の場合は差異率がnull")
        void generate_zeroSystemQuantity_nullDiffRate() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(createResultRow("A-01", "P-001", "商品A", "CAS", null, 0, 5)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<StocktakeResultReportItem>> response =
                    service.generate(10L, ReportFormat.JSON);

            StocktakeResultReportItem item = response.getBody().getFirst();
            assertThat(item.getSystemQuantity()).isEqualTo(0);
            assertThat(item.getDiffQuantity()).isEqualTo(5);
            assertThat(item.getDiffRate()).isNull();
        }

        @Test
        @DisplayName("actualQuantityがnullの場合は差異計算しない")
        void generate_nullActualQuantity_noDiffCalculation() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(createResultRow("A-01", "P-001", "商品A", "CAS", null, 10, null)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<StocktakeResultReportItem>> response =
                    service.generate(10L, ReportFormat.JSON);

            StocktakeResultReportItem item = response.getBody().getFirst();
            assertThat(item.getActualQuantity()).isNull();
            assertThat(item.getDiffQuantity()).isNull();
            assertThat(item.getDiffRate()).isNull();
        }

        @Test
        @DisplayName("systemQuantityがnullの場合は0として扱う")
        void generate_nullSystemQuantity_treatedAsZero() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(createResultRow("A-01", "P-001", "商品A", "CAS", null, null, 5)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            ResponseEntity<List<StocktakeResultReportItem>> response =
                    service.generate(10L, ReportFormat.JSON);

            StocktakeResultReportItem item = response.getBody().getFirst();
            assertThat(item.getSystemQuantity()).isEqualTo(0);
            assertThat(item.getDiffQuantity()).isEqualTo(5);
            assertThat(item.getDiffRate()).isNull(); // systemQty=0 → diffRate=null
        }

        @Test
        @DisplayName("条件サマリーに棚卸番号とステータスが含まれる（確定済）")
        void generate_confirmed_conditionsSummary() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L)).thenReturn(listOf());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(10L, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .contains("棚卸番号: ST-2026-00042")
                    .contains("ステータス: 確定済")
                    .contains("開始: 2026-03-15 09:00")
                    .contains("確定: 2026-03-17 12:00");
        }

        @Test
        @DisplayName("棚卸中ステータスの場合は確定日時が条件サマリーに含まれない")
        void generate_started_conditionsSummaryWithoutConfirmedAt() {
            stocktakeHeader.setStatus("STARTED");
            stocktakeHeader.setConfirmedAt(null);
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L)).thenReturn(listOf());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(10L, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .contains("ステータス: 棚卸中")
                    .doesNotContain("確定:");
        }

        @Test
        @DisplayName("startedAtがnullの場合も正常動作")
        void generate_nullStartedAt_noError() {
            stocktakeHeader.setStartedAt(null);
            stocktakeHeader.setConfirmedAt(null);
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L)).thenReturn(listOf());
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(10L, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            assertThat(metaCaptor.getValue().conditionsSummary())
                    .doesNotContain("開始:");
        }

        @Test
        @DisplayName("extraTemplateVarsに差異サマリーが含まれる")
        void generate_extraVarsContainDiffSummary() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(
                            createResultRow("A-01", "P-001", "商品A", "CAS", null, 10, 10),
                            createResultRow("A-01", "P-002", "商品B", "PCS", null, 50, 47),
                            createResultRow("A-02", "P-003", "商品C", "BAL", null, 20, 22)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(10L, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            Map<String, Object> extraVars = metaCaptor.getValue().extraTemplateVars();
            assertThat(extraVars.get("surplusTotal")).isEqualTo(2);
            assertThat(extraVars.get("shortageTotal")).isEqualTo(-3);
            assertThat(extraVars.get("diffCount")).isEqualTo(2);
            assertThat(extraVars.get("totalCount")).isEqualTo(3);
            assertThat(extraVars.get("stocktakeNumber")).isEqualTo("ST-2026-00042");
            assertThat(extraVars.get("status")).isEqualTo("確定済");
            assertThat(extraVars.get("startedAt")).isEqualTo("2026-03-15 09:00");
            assertThat(extraVars.get("confirmedAt")).isEqualTo("2026-03-17 12:00");
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
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(createResultRow("A-01", "P-001", "商品A", "CAS", "LOT-001", 50, 47)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row).hasSize(9);
                        assertThat(row[0]).isEqualTo("A-01");
                        assertThat(row[6]).isEqualTo("-3"); // diffQuantity=-3
                        assertThat(row[7]).isEqualTo("-6.0%"); // diffRate
                        return ResponseEntity.ok(data);
                    });

            service.generate(10L, ReportFormat.CSV);
            verify(reportExportService).export(anyList(), eq(ReportFormat.CSV), any());
        }

        @Test
        @DisplayName("プラス差異は+符号付きで出力される")
        void generate_csvPositiveDiff_hasPlusSign() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(createResultRow("A-01", "P-001", "商品A", "CAS", null, 20, 22)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row[6]).isEqualTo("+2");
                        return ResponseEntity.ok(data);
                    });

            service.generate(10L, ReportFormat.CSV);
        }

        @Test
        @DisplayName("diffQuantityがnullの場合はemダッシュ")
        void generate_csvNullDiff_emDash() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(createResultRow("A-01", "P-001", "商品A", "CAS", null, 10, null)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row[6]).isEqualTo("\u2014"); // em dash
                        assertThat(row[7]).isEqualTo("\u2014"); // diffRate null → em dash
                        return ResponseEntity.ok(data);
                    });

            service.generate(10L, ReportFormat.CSV);
        }

        @Test
        @DisplayName("差異0の場合は符号なし0")
        void generate_csvZeroDiff_noSign() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(createResultRow("A-01", "P-001", "商品A", "CAS", null, 10, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> {
                        ReportMeta meta = inv.getArgument(2);
                        List<?> data = inv.getArgument(0);
                        String[] row = meta.csvRowMapper().apply(data.getFirst());
                        assertThat(row[6]).isEqualTo("0"); // diff=0, no sign
                        return ResponseEntity.ok(data);
                    });

            service.generate(10L, ReportFormat.CSV);
        }
    }

    @Nested
    @DisplayName("generate - 異常系")
    class GenerateError {

        @Test
        @DisplayName("棚卸が存在しない場合はResourceNotFoundException")
        void generate_stocktakeNotFound_throwsException() {
            when(stocktakeHeaderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(999L, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("STOCKTAKE_NOT_FOUND"));
        }

        @Test
        @DisplayName("倉庫が存在しない場合はResourceNotFoundException")
        void generate_warehouseNotFound_throwsException() {
            stocktakeHeader.setWarehouseId(999L);
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(10L, ReportFormat.JSON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo("WAREHOUSE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("generate - 差異サマリー")
    class DiffSummary {

        @Test
        @DisplayName("差異なしの場合のサマリー")
        void generate_noDiff_zeroSummary() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(
                            createResultRow("A-01", "P-001", "商品A", "CAS", null, 10, 10)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(10L, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            Map<String, Object> extraVars = metaCaptor.getValue().extraTemplateVars();
            assertThat(extraVars.get("surplusTotal")).isEqualTo(0);
            assertThat(extraVars.get("shortageTotal")).isEqualTo(0);
            assertThat(extraVars.get("diffCount")).isEqualTo(0);
        }

        @Test
        @DisplayName("未入力行は差異カウントに含まれない")
        void generate_uncountedLine_notCountedInDiff() {
            when(stocktakeHeaderRepository.findById(10L)).thenReturn(Optional.of(stocktakeHeader));
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
            when(stocktakeReportRepository.findStocktakeResultByStocktakeId(10L))
                    .thenReturn(listOf(
                            createResultRow("A-01", "P-001", "商品A", "CAS", null, 10, null)));
            when(reportExportService.export(anyList(), any(), any()))
                    .thenAnswer(inv -> ResponseEntity.ok(inv.getArgument(0)));

            service.generate(10L, ReportFormat.JSON);

            ArgumentCaptor<ReportMeta> metaCaptor = ArgumentCaptor.forClass(ReportMeta.class);
            verify(reportExportService).export(anyList(), any(), metaCaptor.capture());
            Map<String, Object> extraVars = metaCaptor.getValue().extraTemplateVars();
            assertThat(extraVars.get("diffCount")).isEqualTo(0);
            assertThat(extraVars.get("totalCount")).isEqualTo(1);
        }
    }
}
