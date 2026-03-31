package com.wms.interfacing.service;

import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderCsvProcessor")
class OrderCsvProcessorTest {

    private final OrderCsvProcessor processor = new OrderCsvProcessor();
    private final LocalDate businessDate = LocalDate.of(2026, 3, 20);

    private Partner customer;
    private Partner supplier;
    private Partner bothPartner;
    private Partner inactivePartner;
    private Product product1;
    private Product product2;
    private Product inactiveProduct;
    private Product shipmentStopProduct;
    private Warehouse warehouse;
    private InboundPlanCsvProcessor.MasterCache masterCache;

    private static void setField(Object obj, String fieldName, Object value) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }

    @BeforeEach
    void setUp() {
        customer = new Partner();
        customer.setPartnerCode("CUS-0001");
        customer.setPartnerName("Customer 1");
        customer.setPartnerType(PartnerType.CUSTOMER);
        setField(customer, "id", 1L);
        setField(customer, "isActive", true);

        supplier = new Partner();
        supplier.setPartnerCode("SUP-0001");
        supplier.setPartnerName("Supplier 1");
        supplier.setPartnerType(PartnerType.SUPPLIER);
        setField(supplier, "id", 2L);
        setField(supplier, "isActive", true);

        bothPartner = new Partner();
        bothPartner.setPartnerCode("BOTH-0001");
        bothPartner.setPartnerName("Both Partner");
        bothPartner.setPartnerType(PartnerType.BOTH);
        setField(bothPartner, "id", 3L);
        setField(bothPartner, "isActive", true);

        inactivePartner = new Partner();
        inactivePartner.setPartnerCode("CUS-INACTIVE");
        inactivePartner.setPartnerName("Inactive Customer");
        inactivePartner.setPartnerType(PartnerType.CUSTOMER);
        setField(inactivePartner, "id", 4L);
        setField(inactivePartner, "isActive", false);

        product1 = new Product();
        product1.setProductCode("PRD-001");
        product1.setProductName("Product 1");
        product1.setLotManageFlag(false);
        product1.setExpiryManageFlag(false);
        setField(product1, "id", 10L);
        setField(product1, "isActive", true);
        setField(product1, "shipmentStopFlag", false);

        product2 = new Product();
        product2.setProductCode("PRD-002");
        product2.setProductName("Product 2");
        product2.setLotManageFlag(false);
        product2.setExpiryManageFlag(false);
        setField(product2, "id", 11L);
        setField(product2, "isActive", true);
        setField(product2, "shipmentStopFlag", false);

        inactiveProduct = new Product();
        inactiveProduct.setProductCode("PRD-INACTIVE");
        inactiveProduct.setProductName("Inactive Product");
        inactiveProduct.setLotManageFlag(false);
        inactiveProduct.setExpiryManageFlag(false);
        setField(inactiveProduct, "id", 12L);
        setField(inactiveProduct, "isActive", false);
        setField(inactiveProduct, "shipmentStopFlag", false);

        shipmentStopProduct = new Product();
        shipmentStopProduct.setProductCode("PRD-STOP");
        shipmentStopProduct.setProductName("Shipment Stop Product");
        shipmentStopProduct.setLotManageFlag(false);
        shipmentStopProduct.setExpiryManageFlag(false);
        setField(shipmentStopProduct, "id", 13L);
        setField(shipmentStopProduct, "isActive", true);
        setField(shipmentStopProduct, "shipmentStopFlag", true);

        warehouse = new Warehouse();
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("Warehouse 1");
        setField(warehouse, "id", 100L);

        masterCache = new InboundPlanCsvProcessor.MasterCache(
                Map.of(
                        "CUS-0001", customer,
                        "SUP-0001", supplier,
                        "BOTH-0001", bothPartner,
                        "CUS-INACTIVE", inactivePartner
                ),
                Map.of(
                        "PRD-001", product1,
                        "PRD-002", product2,
                        "PRD-INACTIVE", inactiveProduct,
                        "PRD-STOP", shipmentStopProduct
                ),
                warehouse
        );
    }

    // --- ヘルパー ---

    private String[] row(String partnerCode, String plannedDate, String productCode,
                         String unitType, String orderedQty, String note) {
        return new String[]{partnerCode, plannedDate, productCode, unitType, orderedQty, note};
    }

    private String[] validRow() {
        return row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", "");
    }

    // ========================
    // validateHeader
    // ========================

    @Nested
    @DisplayName("validateHeader")
    class ValidateHeader {

        @Test
        @DisplayName("正常系 — 正しいヘッダで例外なし")
        void validateHeader_correctHeader_noException() {
            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "ordered_qty", "note"};
            processor.validateHeader(header);
        }

        @Test
        @DisplayName("正常系 — 前後空白・大文字混在でも許容")
        void validateHeader_trimAndCaseInsensitive_noException() {
            String[] header = {" Partner_Code ", " Planned_Date ", " Product_Code ",
                    " Unit_Type ", " Ordered_Qty ", " Note "};
            processor.validateHeader(header);
        }

        @Test
        @DisplayName("異常系 — カラム数不正でWMS-E-IFX-003")
        void validateHeader_wrongColumnCount_throws003() {
            String[] header = {"partner_code", "planned_date"};
            assertThatThrownBy(() -> processor.validateHeader(header))
                    .isInstanceOf(CsvParser.CsvParseException.class)
                    .satisfies(e -> assertThat(((CsvParser.CsvParseException) e).getErrorCode())
                            .isEqualTo("WMS-E-IFX-003"));
        }

        @Test
        @DisplayName("異常系 — カラム名不正でWMS-E-IFX-004")
        void validateHeader_wrongColumnName_throws004() {
            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "wrong_col", "note"};
            assertThatThrownBy(() -> processor.validateHeader(header))
                    .isInstanceOf(CsvParser.CsvParseException.class)
                    .satisfies(e -> assertThat(((CsvParser.CsvParseException) e).getErrorCode())
                            .isEqualTo("WMS-E-IFX-004"));
        }
    }

    // ========================
    // validate
    // ========================

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("正常系 — 全行バリデーション成功")
        void validate_allValid_allSuccess() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", "通常配送"),
                    row("CUS-0001", "2026-03-22", "PRD-002", "PIECE", "200", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.getTotalRows()).isEqualTo(2);
            assertThat(result.getSuccessCount()).isEqualTo(2);
            assertThat(result.getErrorCount()).isEqualTo(0);
            assertThat(result.getRowErrors()).isEmpty();
        }

        @Test
        @DisplayName("正常系 — BOTH種別の取引先で成功")
        void validate_bothPartnerType_success() {
            List<String[]> rows = List.<String[]>of(
                    row("BOTH-0001", "2026-03-22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getSuccessCount()).isEqualTo(1);
        }

        // --- L2: 形式チェック ---

        @Test
        @DisplayName("L2 — partner_code必須エラー")
        void validate_missingPartnerCode_error201() {
            List<String[]> rows = List.<String[]>of(
                    row("", "2026-03-22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getErrorCount()).isEqualTo(1);
            assertErrorCode(result, "WMS-E-IFX-201");
        }

        @Test
        @DisplayName("L2 — partner_code 50文字超過エラー")
        void validate_partnerCodeTooLong_error201() {
            List<String[]> rows = List.<String[]>of(
                    row("A".repeat(51), "2026-03-22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-201");
        }

        @Test
        @DisplayName("L2 — planned_date必須エラー")
        void validate_missingPlannedDate_error202() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-202");
        }

        @Test
        @DisplayName("L2 — planned_date不正形式エラー")
        void validate_invalidPlannedDate_error202() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026/03/22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-202");
        }

        @Test
        @DisplayName("L2 — product_code必須エラー")
        void validate_missingProductCode_error203() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-203");
        }

        @Test
        @DisplayName("L2 — product_code 50文字超過エラー")
        void validate_productCodeTooLong_error203() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "P".repeat(51), "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-203");
        }

        @Test
        @DisplayName("L2 — unit_type必須エラー")
        void validate_missingUnitType_error204() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-204");
        }

        @Test
        @DisplayName("L2 — unit_type不正値エラー")
        void validate_invalidUnitType_error204() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "BOX", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-204");
        }

        @Test
        @DisplayName("L2 — ordered_qty必須エラー")
        void validate_missingOrderedQty_error205() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-205");
        }

        @Test
        @DisplayName("L2 — ordered_qty 0以下エラー")
        void validate_zeroOrderedQty_error205() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "0", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-205");
        }

        @Test
        @DisplayName("L2 — ordered_qty 負数エラー")
        void validate_negativeOrderedQty_error205() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "-1", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-205");
        }

        @Test
        @DisplayName("L2 — ordered_qty 小数エラー")
        void validate_decimalOrderedQty_error205() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "1.5", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-205");
        }

        @Test
        @DisplayName("L2 — ordered_qty 文字列エラー")
        void validate_nonNumericOrderedQty_error205() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "abc", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-205");
        }

        @Test
        @DisplayName("L2 — note 500文字超過エラー")
        void validate_noteTooLong_error206() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", "N".repeat(501))
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-206");
        }

        @Test
        @DisplayName("L2 — note 500文字ちょうどは成功")
        void validate_noteExact500_success() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", "N".repeat(500))
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getSuccessCount()).isEqualTo(1);
        }

        // --- L3: マスタ参照バリデーション ---

        @Test
        @DisplayName("L3 — 取引先コード未登録エラー")
        void validate_unknownPartner_error301() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-9999", "2026-03-22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-301");
        }

        @Test
        @DisplayName("L3 — 取引先無効エラー")
        void validate_inactivePartner_error302() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-INACTIVE", "2026-03-22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-302");
        }

        @Test
        @DisplayName("L3 — 取引先種別不正（SUPPLIERは出荷先でない）エラー")
        void validate_supplierOnlyPartner_error303() {
            List<String[]> rows = List.<String[]>of(
                    row("SUP-0001", "2026-03-22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-303");
        }

        @Test
        @DisplayName("L3 — 商品コード未登録エラー")
        void validate_unknownProduct_error304() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-9999", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-304");
        }

        @Test
        @DisplayName("L3 — 商品無効エラー")
        void validate_inactiveProduct_error305() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-INACTIVE", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-305");
        }

        @Test
        @DisplayName("L3 — 出荷禁止商品エラー")
        void validate_shipmentStopProduct_error306() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-STOP", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-306");
        }

        // --- L5: クロスバリデーション ---

        @Test
        @DisplayName("L5 — 同一伝票内の同一商品重複エラー")
        void validate_duplicateProductInSlip_error502() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-22", "PRD-001", "PIECE", "100", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getErrorCount()).isEqualTo(1);
            assertErrorCode(result, "WMS-E-IFX-502");
        }

        @Test
        @DisplayName("L5 — 異なる伝票の同一商品は重複エラーにならない")
        void validate_sameProductDifferentSlip_noError() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-23", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getSuccessCount()).isEqualTo(2);
        }

        // --- 複合テスト ---

        @Test
        @DisplayName("1行に複数エラーがある場合、全エラーが返却される")
        void validate_multipleErrorsPerRow_allReported() {
            List<String[]> rows = List.<String[]>of(
                    row("", "", "", "", "", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getRowErrors()).hasSize(1);
            // partner_code, planned_date, product_code, unit_type, ordered_qty = 5エラー
            assertThat(result.getRowErrors().get(0).errors()).hasSizeGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("エラー行と成功行が混在する場合のカウント")
        void validate_mixedResults_correctCounts() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-22", "PRD-002", "PIECE", "200", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getTotalRows()).isEqualTo(3);
            assertThat(result.getSuccessCount()).isEqualTo(2);
            assertThat(result.getErrorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("カラム数が少ない行でもnullとして扱われる")
        void validate_shortRow_treatedAsNull() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"CUS-0001", "2026-03-22"}
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getErrorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("L5 — L2-L4エラー行はクロスバリデーション対象外")
        void validate_crossValidationSkipsErrorRows() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "abc", "")  // L2エラー行
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            // 2行目はL2エラー（ordered_qty）のみ。クロスバリデーションはスキップ
            assertThat(result.getErrorCount()).isEqualTo(1);
            assertThat(result.getSuccessCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("L5 — クロスバリデーションで既存エラー行にエラー追加")
        void validate_crossValidationMergesWithExisting() {
            // 3行: 1行目成功、2行目成功（重複）、3行目はL2エラー+重複対象
            // ただし3行目はL2エラーなのでクロスチェックスキップ
            // → 2行目のみクロスエラーで1行目は成功のまま
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "100", ""),
                    row("CUS-0001", "2026-03-22", "PRD-002", "PIECE", "200", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getSuccessCount()).isEqualTo(2);
            assertThat(result.getErrorCount()).isEqualTo(1);
            assertErrorCode(result, "WMS-E-IFX-502");
        }
    }

    // ========================
    // buildSlips
    // ========================

    @Nested
    @DisplayName("buildSlips")
    class BuildSlips {

        @Test
        @DisplayName("正常系 — 1伝票1明細の基本取り込み")
        void buildSlips_singleSlipSingleLine_correct() {
            List<String[]> rows = List.<String[]>of(validRow());
            InboundPlanCsvProcessor.ValidationResult vr =
                    new InboundPlanCsvProcessor.ValidationResult(1, 1, 0, List.of());
            AtomicInteger seq = new AtomicInteger(0);

            List<OutboundSlip> slips = processor.buildSlips(
                    rows, vr, masterCache, 100L,
                    bd -> "OUT-20260320-" + String.format("%04d", seq.incrementAndGet()),
                    businessDate, 1L);

            assertThat(slips).hasSize(1);
            OutboundSlip slip = slips.get(0);
            assertThat(slip.getSlipNumber()).isEqualTo("OUT-20260320-0001");
            assertThat(slip.getSlipType()).isEqualTo("NORMAL");
            assertThat(slip.getWarehouseId()).isEqualTo(100L);
            assertThat(slip.getWarehouseCode()).isEqualTo("WH-001");
            assertThat(slip.getWarehouseName()).isEqualTo("Warehouse 1");
            assertThat(slip.getPartnerId()).isEqualTo(1L);
            assertThat(slip.getPartnerCode()).isEqualTo("CUS-0001");
            assertThat(slip.getPartnerName()).isEqualTo("Customer 1");
            assertThat(slip.getPlannedDate()).isEqualTo(LocalDate.of(2026, 3, 22));
            assertThat(slip.getStatus()).isEqualTo("ORDERED");

            assertThat(slip.getLines()).hasSize(1);
            OutboundSlipLine line = slip.getLines().get(0);
            assertThat(line.getLineNo()).isEqualTo(1);
            assertThat(line.getProductId()).isEqualTo(10L);
            assertThat(line.getProductCode()).isEqualTo("PRD-001");
            assertThat(line.getProductName()).isEqualTo("Product 1");
            assertThat(line.getUnitType()).isEqualTo("CASE");
            assertThat(line.getOrderedQty()).isEqualTo(50);
            assertThat(line.getShippedQty()).isEqualTo(0);
            assertThat(line.getLineStatus()).isEqualTo("ORDERED");
        }

        @Test
        @DisplayName("正常系 — 同一伝票に複数明細がline_no連番で登録される")
        void buildSlips_multipleLines_correctLineNo() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-22", "PRD-002", "PIECE", "200", "")
            );
            InboundPlanCsvProcessor.ValidationResult vr =
                    new InboundPlanCsvProcessor.ValidationResult(2, 2, 0, List.of());

            List<OutboundSlip> slips = processor.buildSlips(
                    rows, vr, masterCache, 100L,
                    bd -> "OUT-20260320-0001",
                    businessDate, 1L);

            assertThat(slips).hasSize(1);
            assertThat(slips.get(0).getLines()).hasSize(2);
            assertThat(slips.get(0).getLines().get(0).getLineNo()).isEqualTo(1);
            assertThat(slips.get(0).getLines().get(1).getLineNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("正常系 — 異なるpartner_code+planned_dateで別伝票に分割される")
        void buildSlips_multipleSlips_correctGrouping() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("BOTH-0001", "2026-03-23", "PRD-002", "BALL", "30", "")
            );
            InboundPlanCsvProcessor.ValidationResult vr =
                    new InboundPlanCsvProcessor.ValidationResult(2, 2, 0, List.of());
            AtomicInteger seq = new AtomicInteger(0);

            List<OutboundSlip> slips = processor.buildSlips(
                    rows, vr, masterCache, 100L,
                    bd -> "OUT-20260320-" + String.format("%04d", seq.incrementAndGet()),
                    businessDate, 1L);

            assertThat(slips).hasSize(2);
            assertThat(slips.get(0).getPartnerCode()).isEqualTo("CUS-0001");
            assertThat(slips.get(1).getPartnerCode()).isEqualTo("BOTH-0001");
        }

        @Test
        @DisplayName("正常系 — エラー行がスキップされる（SUCCESS_ONLY相当）")
        void buildSlips_errorRowsSkipped_onlySuccessRows() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-22", "PRD-002", "CASE", "abc", ""),  // エラー行
                    row("CUS-0001", "2026-03-23", "PRD-002", "PIECE", "200", "")
            );
            // 2行目(rowNumber=2)がエラー
            InboundPlanCsvProcessor.ValidationResult vr =
                    new InboundPlanCsvProcessor.ValidationResult(3, 2, 1,
                            List.of(new InboundPlanCsvProcessor.RowError(2, List.of(
                                    new InboundPlanCsvProcessor.FieldError("ordered_qty",
                                            "WMS-E-IFX-205", "err")))));
            AtomicInteger seq = new AtomicInteger(0);

            List<OutboundSlip> slips = processor.buildSlips(
                    rows, vr, masterCache, 100L,
                    bd -> "OUT-20260320-" + String.format("%04d", seq.incrementAndGet()),
                    businessDate, 1L);

            // 1行目と3行目は異なるplanned_dateなので別伝票
            assertThat(slips).hasSize(2);
            assertThat(slips.get(0).getLines()).hasSize(1);
            assertThat(slips.get(1).getLines()).hasSize(1);
        }

        @Test
        @DisplayName("正常系 — 全行エラー時は空リスト返却")
        void buildSlips_allErrors_emptyList() {
            List<String[]> rows = List.<String[]>of(
                    row("", "2026-03-22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult vr =
                    new InboundPlanCsvProcessor.ValidationResult(1, 0, 1,
                            List.of(new InboundPlanCsvProcessor.RowError(1, List.of(
                                    new InboundPlanCsvProcessor.FieldError("partner_code",
                                            "WMS-E-IFX-201", "err")))));

            List<OutboundSlip> slips = processor.buildSlips(
                    rows, vr, masterCache, 100L,
                    bd -> "OUT-20260320-0001",
                    businessDate, 1L);

            assertThat(slips).isEmpty();
        }
    }

    // ========================
    // Additional coverage tests
    // ========================

    @Nested
    @DisplayName("coverage — edge cases")
    class CoverageEdgeCases {

        @Test
        @DisplayName("L3 — partner_codeがnullの場合はマスタチェックスキップ")
        void validate_nullPartnerCode_skipsMasterCheck() {
            // partner_code is null → L2 error, L3 not reached
            List<String[]> rows = List.<String[]>of(
                    row("", "2026-03-22", "PRD-001", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getErrorCount()).isEqualTo(1);
            assertErrorCode(result, "WMS-E-IFX-201");
        }

        @Test
        @DisplayName("L3 — product_codeがnullの場合はマスタチェックスキップ")
        void validate_nullProductCode_skipsMasterCheck() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "", "CASE", "50", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertErrorCode(result, "WMS-E-IFX-203");
        }

        @Test
        @DisplayName("L5 — validateCrossでnullフィールドの行はスキップ")
        void validate_crossValidation_nullFieldsSkipped() {
            // 全行L2エラー → クロスバリデーションの対象なし
            List<String[]> rows = List.<String[]>of(
                    row("", "", "", "", "", ""),
                    row("", "", "", "", "", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getErrorCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("L5 — クロスバリデーションで既にL2エラーの行にはエラー追加されない")
        void validate_crossValidation_existingL2ErrorRowNotMerged() {
            // 1行目:成功、2行目:L2エラー（ordered_qty=abc）、3行目:成功
            // 1行目と3行目で同一product_code重複 → 3行目にクロスエラー
            // 2行目はスキップされる
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "abc", ""),
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "100", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            // 2行目: L2エラー、3行目: L5エラー（重複）、1行目: 成功
            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getErrorCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("ordered_qty = 1 は境界値として成功")
        void validate_orderedQtyOne_success() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "1", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getSuccessCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("空のデータ行リストでもエラーにならない")
        void validate_emptyDataRows_success() {
            List<String[]> rows = List.of();
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getTotalRows()).isEqualTo(0);
            assertThat(result.getSuccessCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("3つの重複行がある場合、2番目と3番目にエラー付与")
        void validate_tripleDuplicate_twoErrors() {
            List<String[]> rows = List.<String[]>of(
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""),
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "100", ""),
                    row("CUS-0001", "2026-03-22", "PRD-001", "CASE", "200", "")
            );
            InboundPlanCsvProcessor.ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);
            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getErrorCount()).isEqualTo(2);
        }
    }

    // --- アサーションヘルパー ---

    private void assertErrorCode(InboundPlanCsvProcessor.ValidationResult result,
                                  String expectedCode) {
        assertThat(result.getRowErrors()).isNotEmpty();
        boolean found = result.getRowErrors().stream()
                .flatMap(re -> re.errors().stream())
                .anyMatch(e -> e.errorCode().equals(expectedCode));
        assertThat(found).as("Expected error code " + expectedCode).isTrue();
    }
}
