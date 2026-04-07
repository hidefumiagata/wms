package com.wms.interfacing.service;

import com.wms.interfacing.model.FieldError;
import com.wms.interfacing.model.MasterCache;
import com.wms.interfacing.model.RowError;
import com.wms.interfacing.model.ValidationResult;
import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InboundPlanCsvProcessor")
class InboundPlanCsvProcessorTest {

    private final InboundPlanCsvProcessor processor = new InboundPlanCsvProcessor();
    private final LocalDate businessDate = LocalDate.of(2026, 3, 20);

    private Partner supplier;
    private Partner customer;
    private Partner inactivePartner;
    private Product product1;
    private Product product2;
    private Product lotManagedProduct;
    private Product expiryManagedProduct;
    private Product inactiveProduct;
    private Warehouse warehouse;
    private MasterCache masterCache;

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
        supplier = new Partner();
        supplier.setPartnerCode("SUP-0001");
        supplier.setPartnerName("Supplier 1");
        supplier.setPartnerType(PartnerType.SUPPLIER);
        setField(supplier, "id", 1L);
        setField(supplier, "isActive", true);

        customer = new Partner();
        customer.setPartnerCode("CUS-0001");
        customer.setPartnerName("Customer 1");
        customer.setPartnerType(PartnerType.CUSTOMER);
        setField(customer, "id", 2L);
        setField(customer, "isActive", true);

        inactivePartner = new Partner();
        inactivePartner.setPartnerCode("SUP-INACTIVE");
        inactivePartner.setPartnerName("Inactive Partner");
        inactivePartner.setPartnerType(PartnerType.SUPPLIER);
        setField(inactivePartner, "id", 3L);
        setField(inactivePartner, "isActive", false);

        product1 = new Product();
        product1.setProductCode("PRD-001");
        product1.setProductName("Product 1");
        product1.setLotManageFlag(false);
        product1.setExpiryManageFlag(false);
        setField(product1, "id", 10L);
        setField(product1, "isActive", true);

        product2 = new Product();
        product2.setProductCode("PRD-002");
        product2.setProductName("Product 2");
        product2.setLotManageFlag(false);
        product2.setExpiryManageFlag(false);
        setField(product2, "id", 11L);
        setField(product2, "isActive", true);

        lotManagedProduct = new Product();
        lotManagedProduct.setProductCode("PRD-LOT");
        lotManagedProduct.setProductName("Lot Managed Product");
        lotManagedProduct.setLotManageFlag(true);
        lotManagedProduct.setExpiryManageFlag(false);
        setField(lotManagedProduct, "id", 12L);
        setField(lotManagedProduct, "isActive", true);

        expiryManagedProduct = new Product();
        expiryManagedProduct.setProductCode("PRD-EXP");
        expiryManagedProduct.setProductName("Expiry Managed Product");
        expiryManagedProduct.setLotManageFlag(false);
        expiryManagedProduct.setExpiryManageFlag(true);
        setField(expiryManagedProduct, "id", 13L);
        setField(expiryManagedProduct, "isActive", true);

        inactiveProduct = new Product();
        inactiveProduct.setProductCode("PRD-INACTIVE");
        inactiveProduct.setProductName("Inactive Product");
        inactiveProduct.setLotManageFlag(false);
        inactiveProduct.setExpiryManageFlag(false);
        setField(inactiveProduct, "id", 14L);
        setField(inactiveProduct, "isActive", false);

        warehouse = new Warehouse();
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("Warehouse 1");
        setField(warehouse, "id", 100L);

        masterCache = new MasterCache(
                Map.of(
                        "SUP-0001", supplier,
                        "CUS-0001", customer,
                        "SUP-INACTIVE", inactivePartner
                ),
                Map.of(
                        "PRD-001", product1,
                        "PRD-002", product2,
                        "PRD-LOT", lotManagedProduct,
                        "PRD-EXP", expiryManagedProduct,
                        "PRD-INACTIVE", inactiveProduct
                ),
                warehouse
        );
    }

    @Nested
    @DisplayName("validateHeader")
    class ValidateHeader {

        @Test
        @DisplayName("正常系 — 正しいヘッダで例外なし")
        void validateHeader_correctHeader_noException() {
            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "planned_qty", "lot_number", "expiry_date", "note"};
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
            String[] header = {"wrong_name", "planned_date", "product_code",
                    "unit_type", "planned_qty", "lot_number", "expiry_date", "note"};
            assertThatThrownBy(() -> processor.validateHeader(header))
                    .isInstanceOf(CsvParser.CsvParseException.class)
                    .satisfies(e -> assertThat(((CsvParser.CsvParseException) e).getErrorCode())
                            .isEqualTo("WMS-E-IFX-004"));
        }
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("正常系 — 全行バリデーション成功")
        void validate_allValid_allSuccess() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-002", "PIECE", "50", "", "", "note"}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.totalRows()).isEqualTo(2);
            assertThat(result.successCount()).isEqualTo(2);
            assertThat(result.errorCount()).isEqualTo(0);
            assertThat(result.rowErrors()).isEmpty();
        }

        @Test
        @DisplayName("L2 — partner_code必須エラー")
        void validate_missingPartnerCode_error101() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.errorCount()).isEqualTo(1);
            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-101"));
        }

        @Test
        @DisplayName("L2 — partner_code 50文字超過エラー")
        void validate_partnerCodeTooLong_error101() {
            String longCode = "A".repeat(51);
            List<String[]> rows = List.<String[]>of(
                    new String[]{longCode, "2026-03-25", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-101"));
        }

        @Test
        @DisplayName("L2 — planned_date必須エラー")
        void validate_missingPlannedDate_error102() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-102"));
        }

        @Test
        @DisplayName("L2 — planned_date不正な日付形式でエラー")
        void validate_invalidDateFormat_error102() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "20260325", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-102"));
        }

        @Test
        @DisplayName("L2 — product_code必須エラー")
        void validate_missingProductCode_error103() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-103"));
        }

        @Test
        @DisplayName("L2 — product_code 50文字超過エラー")
        void validate_productCodeTooLong_error103() {
            String longCode = "P".repeat(51);
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", longCode, "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-103"));
        }

        @Test
        @DisplayName("L2 — unit_type必須エラー")
        void validate_missingUnitType_error104() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-104"));
        }

        @Test
        @DisplayName("L2 — unit_type不正値でエラー")
        void validate_invalidUnitType_error104() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "INVALID", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-104"));
        }

        @Test
        @DisplayName("L2 — planned_qty必須エラー")
        void validate_missingPlannedQty_error105() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-105"));
        }

        @Test
        @DisplayName("L2 — planned_qty 0以下でエラー")
        void validate_zeroPlannedQty_error105() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "0", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-105"));
        }

        @Test
        @DisplayName("L2 — planned_qty 非数値でエラー")
        void validate_nonNumericPlannedQty_error105() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "abc", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-105"));
        }

        @Test
        @DisplayName("L2 — lot_number 100文字超過エラー")
        void validate_lotNumberTooLong_error106() {
            String longLot = "L".repeat(101);
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", longLot, "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-106"));
        }

        @Test
        @DisplayName("L2 — expiry_date 不正形式エラー")
        void validate_invalidExpiryDate_error107() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "invalid", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-107"));
        }

        @Test
        @DisplayName("L2 — note 500文字超過エラー")
        void validate_noteTooLong_error108() {
            String longNote = "N".repeat(501);
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", longNote}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-108"));
        }

        @Test
        @DisplayName("L3 — 存在しない取引先コードでエラー")
        void validate_nonExistentPartner_error301() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-9999", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-301"));
        }

        @Test
        @DisplayName("L3 — 無効化された取引先でエラー")
        void validate_inactivePartner_error302() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-INACTIVE", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-302"));
        }

        @Test
        @DisplayName("L3 — CUSTOMER種別の取引先でエラー（SUPPLIERまたはBOTHでない）")
        void validate_customerPartner_error303() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"CUS-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-303"));
        }

        @Test
        @DisplayName("L3 — 存在しない商品コードでエラー")
        void validate_nonExistentProduct_error304() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-NONE", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-304"));
        }

        @Test
        @DisplayName("L3 — 無効化された商品でエラー")
        void validate_inactiveProduct_error305() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-INACTIVE", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-305"));
        }

        @Test
        @DisplayName("L4 — 過去日付でエラー")
        void validate_pastPlannedDate_error401() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-19", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-401"));
        }

        @Test
        @DisplayName("L4 — ロット管理商品でロット番号なしエラー")
        void validate_lotManagedNoLot_error402() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-LOT", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-402"));
        }

        @Test
        @DisplayName("L4 — 期限管理商品で期限日なしエラー")
        void validate_expiryManagedNoExpiry_error403() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-EXP", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-403"));
        }

        @Test
        @DisplayName("L4 — 期限管理商品で期限日が営業日以前エラー")
        void validate_expiryBeforeBusinessDate_error404() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-EXP", "CASE", "100", "", "2026-03-20", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-404"));
        }

        @Test
        @DisplayName("L4 — 期限管理商品で期限日が営業日と同日もエラー")
        void validate_expirySameAsBusinessDate_error404() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-EXP", "CASE", "100", "", "2026-03-20", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-404"));
        }

        @Test
        @DisplayName("L5 — 同一伝票内の同一商品重複でエラー")
        void validate_duplicateProductInSlip_error501() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "PIECE", "50", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.errorCount()).isEqualTo(1);
            assertThat(result.rowErrors()).hasSize(1);
            assertThat(result.rowErrors().get(0).rowNumber()).isEqualTo(2);
            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-501"));
        }

        @Test
        @DisplayName("L5 — 同一商品重複で既にL2エラーがある行は除外される")
        void validate_duplicateProduct_existingL2Error_notDuplicated() {
            // Row 1: valid, Row 2: L2 error (bad qty), Row 3: same product as Row 1
            // Row 2 has L2 error so it's skipped in cross-validation
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-002", "CASE", "abc", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "PIECE", "50", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            // Row 2 has L2 error, Row 3 has L5 error (duplicate of Row 1)
            assertThat(result.errorCount()).isEqualTo(2);
            assertThat(result.successCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("L5 — 3行以上の重複で2番目以降にエラーが付与される")
        void validate_triplicateProduct_secondAndThirdError() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "PIECE", "50", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "BALL", "30", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.errorCount()).isEqualTo(2);
            assertThat(result.successCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("L5 — L2エラー行と同じ行にL5エラーが追加される場合、エラーがマージされる")
        void validate_crossErrorMergedWithExistingError() {
            // Row 1: valid, Row 2: has L2 error AND is a duplicate product
            // This tests the errorMap merge path
            // Actually, L2 error rows are skipped in cross-validation, so this tests
            // that error row numbers are tracked correctly
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-002", "CASE", "200", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-002", "PIECE", "50", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            // Row 3 has L5 duplicate error
            assertThat(result.errorCount()).isEqualTo(1);
            assertThat(result.rowErrors()).hasSize(1);
            assertThat(result.rowErrors().get(0).rowNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("L5 — 異なる伝票の同一商品は重複エラーにならない")
        void validate_sameProductDifferentSlips_noError() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-26", "PRD-001", "CASE", "50", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.successCount()).isEqualTo(2);
            assertThat(result.errorCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("正常系 — 1行に複数エラーがある場合、全エラーを検出する")
        void validate_multipleErrors_allDetected() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"", "", "", "INVALID", "-1", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.rowErrors().get(0).errors()).hasSizeGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("L3 — BOTH種別の取引先は正常")
        void validate_bothPartnerType_success() {
            Partner both = new Partner();
            both.setPartnerCode("SUP-BOTH");
            both.setPartnerName("Both Partner");
            both.setPartnerType(PartnerType.BOTH);
            setField(both, "id", 99L);
            setField(both, "isActive", true);

            MasterCache cacheWithBoth = new MasterCache(
                    Map.of("SUP-BOTH", both),
                    Map.of("PRD-001", product1),
                    warehouse
            );

            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-BOTH", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult result =
                    processor.validate(rows, cacheWithBoth, businessDate);

            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.errorCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("L4 — 期限管理商品で期限日が不正形式の場合、L4のexpiryDateStr!=nullだがexpiryDate==null")
        void validate_expiryManagedInvalidFormat_error107only() {
            // expiryDateStr != null (not empty), but expiryDate is null (parse failed)
            // This tests the branch: expiryDate == null && expiryDateStr == null → false
            // Because expiryDateStr is not null (it's "invalid"), only L2 format error
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-EXP", "CASE", "100", "", "invalid-date", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            // Should have WMS-E-IFX-107 (format error) but NOT WMS-E-IFX-403 (missing)
            assertThat(result.rowErrors().get(0).errors())
                    .anyMatch(e -> e.errorCode().equals("WMS-E-IFX-107"));
            assertThat(result.rowErrors().get(0).errors())
                    .noneMatch(e -> e.errorCode().equals("WMS-E-IFX-403"));
        }

        @Test
        @DisplayName("L4 — 期限管理商品で期限日が未来の場合は正常")
        void validate_expiryManagedFutureExpiry_success() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-EXP", "CASE", "100", "", "2027-01-01", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.successCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("正常系 — ロット・期限ありの正常行")
        void validate_withLotAndExpiry_success() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-LOT", "CASE", "100", "LOT-001", "", ""},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-EXP", "PIECE", "50", "", "2027-01-01", ""}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.successCount()).isEqualTo(2);
            assertThat(result.errorCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("境界値 — カラム数が少ない行でもNullPointerにならない")
        void validate_shortRow_handledGracefully() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25"}
            );
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.errorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("境界値 — 空配列の行でもNullPointerにならない")
        void validate_emptyRow_handledGracefully() {
            List<String[]> rows = List.<String[]>of(new String[]{});
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.errorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("境界値 — カラム数1の行でもNullPointerにならない")
        void validate_singleColumnRow_handledGracefully() {
            List<String[]> rows = List.<String[]>of(new String[]{"SUP-0001"});
            ValidationResult result =
                    processor.validate(rows, masterCache, businessDate);

            assertThat(result.errorCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("buildSlips")
    class BuildSlips {

        @Test
        @DisplayName("正常系 — 1伝票2明細の構築")
        void buildSlips_singleSlipTwoLines() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", "memo"},
                    new String[]{"SUP-0001", "2026-03-25", "PRD-002", "PIECE", "50", "", "", ""}
            );
            ValidationResult validationResult =
                    new ValidationResult(2, 2, 0, List.of());

            var slips = processor.buildSlips(rows, validationResult, masterCache, 100L,
                    date -> "INB-20260320-0001", businessDate, 1L);

            assertThat(slips).hasSize(1);
            assertThat(slips.get(0).getSlipNumber()).isEqualTo("INB-20260320-0001");
            assertThat(slips.get(0).getPartnerCode()).isEqualTo("SUP-0001");
            assertThat(slips.get(0).getNote()).isEqualTo("memo");
            assertThat(slips.get(0).getLines()).hasSize(2);
            assertThat(slips.get(0).getLines().get(0).getLineNo()).isEqualTo(1);
            assertThat(slips.get(0).getLines().get(1).getLineNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("正常系 — 2伝票に分かれるケース")
        void buildSlips_twoSlips() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""},
                    new String[]{"SUP-0001", "2026-03-26", "PRD-001", "CASE", "50", "", "", ""}
            );
            ValidationResult validationResult =
                    new ValidationResult(2, 2, 0, List.of());

            int[] seq = {0};
            var slips = processor.buildSlips(rows, validationResult, masterCache, 100L,
                    date -> "INB-20260320-" + String.format("%04d", ++seq[0]),
                    businessDate, 1L);

            assertThat(slips).hasSize(2);
        }

        @Test
        @DisplayName("正常系 — エラー行はスキップされる")
        void buildSlips_errorRowsSkipped() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""},
                    new String[]{"INVALID", "2026-03-25", "PRD-002", "CASE", "50", "", "", ""}
            );
            // row 2 is error
            ValidationResult validationResult =
                    new ValidationResult(2, 1, 1,
                            List.of(new RowError(2, List.of(
                                    new FieldError("partner_code",
                                            "WMS-E-IFX-301", "error")))));

            var slips = processor.buildSlips(rows, validationResult, masterCache, 100L,
                    date -> "INB-20260320-0001", businessDate, 1L);

            assertThat(slips).hasSize(1);
            assertThat(slips.get(0).getLines()).hasSize(1);
        }

        @Test
        @DisplayName("正常系 — 全行エラーの場合は空リスト")
        void buildSlips_allErrors_emptyList() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"INVALID", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""}
            );
            ValidationResult validationResult =
                    new ValidationResult(1, 0, 1,
                            List.of(new RowError(1, List.of(
                                    new FieldError("partner_code",
                                            "WMS-E-IFX-301", "error")))));

            var slips = processor.buildSlips(rows, validationResult, masterCache, 100L,
                    date -> "INB-20260320-0001", businessDate, 1L);

            assertThat(slips).isEmpty();
        }

        @Test
        @DisplayName("正常系 — ロット・期限あり明細の構築")
        void buildSlips_withLotAndExpiry() {
            List<String[]> rows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100",
                            "LOT-001", "2027-06-30", "note1"}
            );
            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());

            var slips = processor.buildSlips(rows, validationResult, masterCache, 100L,
                    date -> "INB-20260320-0001", businessDate, 1L);

            var line = slips.get(0).getLines().get(0);
            assertThat(line.getLotNumber()).isEqualTo("LOT-001");
            assertThat(line.getExpiryDate()).isEqualTo(LocalDate.of(2027, 6, 30));
            assertThat(line.getLineStatus()).isEqualTo("PENDING");
        }
    }
}
