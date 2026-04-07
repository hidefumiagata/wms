package com.wms.report.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.shared.test.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 結合テスト: 在庫・棚卸系レポート (RPT-07, RPT-08, RPT-09, RPT-10, RPT-11)
 *
 * <p>テスト仕様: docs/test-specifications/TST-RPT-reports.md
 */
@DisplayName("結合テスト: レポート（在庫・棚卸系）")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportInventoryIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String VIEWER_CODE = "viewer01";
    private static final String VIEWER_PASSWORD = "Test@1234";

    private HttpHeaders adminHeaders;
    private HttpHeaders viewerHeaders;

    private Long warehouseId;
    private Long buildingAId;
    private Long areaA01Id;
    private Long locA01_01_01_01;
    private Long locA01_01_01_02;
    private Long productAmbId;
    private Long productAmb2Id;
    private Long adminUserId;

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        buildingAId = jdbcTemplate.queryForObject(
                "SELECT id FROM buildings WHERE building_code = 'A' AND warehouse_id = ?",
                Long.class, warehouseId);
        areaA01Id = jdbcTemplate.queryForObject(
                "SELECT id FROM areas WHERE area_code = 'A01' AND building_id = ?",
                Long.class, buildingAId);
        locA01_01_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);
        locA01_01_01_02 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-02' AND warehouse_id = ?",
                Long.class, warehouseId);
        productAmbId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);
        productAmb2Id = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
        adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);
    }

    @BeforeAll
    void initAuth() {
        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
        viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);
    }

    @BeforeEach
    void cleanupTables() {
        jdbcTemplate.update("DELETE FROM inventory_movements");
        jdbcTemplate.update("DELETE FROM stocktake_lines");
        jdbcTemplate.update("DELETE FROM stocktake_headers");
        jdbcTemplate.update("DELETE FROM inventories");
        jdbcTemplate.update("UPDATE locations SET is_stocktaking_locked = false WHERE warehouse_id = ?",
                warehouseId);
    }

    // ========== ヘルパー ==========

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<byte[]> getBytes(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private void insertInventory(Long locationId, Long productId, String unitType,
                                  int quantity, int allocatedQty) {
        jdbcTemplate.update("""
                INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type,
                    lot_number, expiry_date, quantity, allocated_qty, updated_at)
                VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, now())
                """, warehouseId, locationId, productId, unitType, quantity, allocatedQty);
    }

    private void insertInventoryMovement(Long locationId, String locationCode, Long productId,
                                          String productCode, String productName,
                                          String movementType, int quantity, int quantityAfter,
                                          String executedAt, String correctionReason) {
        jdbcTemplate.update("""
                INSERT INTO inventory_movements (warehouse_id, location_id, location_code,
                    product_id, product_code, product_name, unit_type,
                    movement_type, quantity, quantity_after,
                    correction_reason, executed_at, executed_by)
                VALUES (?, ?, ?, ?, ?, ?, 'PIECE',
                    ?, ?, ?,
                    ?, ?::timestamptz, ?)
                """, warehouseId, locationId, locationCode, productId, productCode, productName,
                movementType, quantity, quantityAfter, correctionReason, executedAt, adminUserId);
    }

    /** 棚卸ヘッダーと明細を投入。stocktakeId を返す。 */
    private Long insertStocktake(String number, String status,
                                 boolean confirmed, boolean withDiff) {
        jdbcTemplate.update("""
                INSERT INTO stocktake_headers (stocktake_number, warehouse_id, building_id,
                    target_description, stocktake_date, status,
                    started_at, started_by, confirmed_at, confirmed_by,
                    created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?, ?, ?, now(), now(), ?, ?)
                """, number, warehouseId, buildingAId, "テスト対象",
                LocalDate.of(2026, 3, 20), status,
                adminUserId,
                confirmed ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                confirmed ? adminUserId : null,
                adminUserId, adminUserId);

        Long stocktakeId = jdbcTemplate.queryForObject(
                "SELECT id FROM stocktake_headers WHERE stocktake_number = ?",
                Long.class, number);

        Integer counted = withDiff ? 8 : 10;
        Integer diff = withDiff ? -2 : 0;

        jdbcTemplate.update("""
                INSERT INTO stocktake_lines (stocktake_header_id, location_id, location_code,
                    product_id, product_code, product_name, unit_type, lot_number, expiry_date,
                    quantity_before, quantity_counted, quantity_diff, is_counted,
                    counted_at, counted_by, created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, 'A-01-A01-01-01-01', ?, 'AMB-001', 'ミネラルウォーター',
                    'PIECE', NULL, NULL, 10, ?, ?, true,
                    now(), ?, now(), now(), ?, ?)
                """, stocktakeId, locA01_01_01_01, productAmbId, counted, diff,
                adminUserId, adminUserId, adminUserId);

        return stocktakeId;
    }

    private void assertPdfResponse(ResponseEntity<byte[]> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("application/pdf"));
        byte[] body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(4);
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    // ========================================================
    // RPT-07: 在庫一覧レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/inventory — RPT-07 在庫一覧")
    class InventoryReport {

        @Test
        @DisplayName("正常系: 在庫データが返る（available = quantity - allocated）")
        void getInventory_data_returnsItems() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 100, 30);
            insertInventory(locA01_01_01_02, productAmb2Id, "PIECE", 50, 0);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory?warehouseId=" + warehouseId + "&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(2);
            JsonNode first = body.get(0);
            assertThat(first.get("availableQty").asInt())
                    .isEqualTo(first.get("quantity").asInt() - first.get("allocatedQty").asInt());
        }

        @Test
        @DisplayName("正常系: 商品IDで絞り込み")
        void getInventory_productFilter_returnsFiltered() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 100, 0);
            insertInventory(locA01_01_01_02, productAmb2Id, "PIECE", 50, 0);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory?warehouseId=" + warehouseId
                            + "&productId=" + productAmbId + "&format=json",
                    adminHeaders);

            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("productCode").asText()).isEqualTo("AMB-001");
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getInventory_pdf_returnsBinary() {
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 100, 0);
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inventory?warehouseId=" + warehouseId + "&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("正常系: データ0件で空配列")
        void getInventory_noData_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory?warehouseId=" + warehouseId + "&format=json",
                    adminHeaders);
            assertThat(parseJson(response.getBody())).isEmpty();
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getInventory_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory?warehouseId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能")
        void getInventory_viewer_succeeds() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory?warehouseId=" + warehouseId + "&format=json",
                    viewerHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ========================================================
    // RPT-08: 在庫推移レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/inventory-transition — RPT-08 在庫推移")
    class InventoryTransitionReport {

        @Test
        @DisplayName("正常系: 在庫変動データが返る")
        void getTransition_data_returnsItems() throws Exception {
            insertInventoryMovement(locA01_01_01_01, "A-01-A01-01-01-01", productAmbId,
                    "AMB-001", "ミネラルウォーター", "INBOUND", 50, 50,
                    "2026-03-20T10:00:00+09:00", null);
            insertInventoryMovement(locA01_01_01_01, "A-01-A01-01-01-01", productAmbId,
                    "AMB-001", "ミネラルウォーター", "OUTBOUND", -20, 30,
                    "2026-03-20T15:00:00+09:00", null);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory-transition?warehouseId=" + warehouseId
                            + "&productId=" + productAmbId
                            + "&dateFrom=2026-03-20&dateTo=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(2);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getTransition_pdf_returnsBinary() {
            insertInventoryMovement(locA01_01_01_01, "A-01-A01-01-01-01", productAmbId,
                    "AMB-001", "ミネラルウォーター", "INBOUND", 50, 50,
                    "2026-03-20T10:00:00+09:00", null);

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inventory-transition?warehouseId=" + warehouseId
                            + "&productId=" + productAmbId
                            + "&dateFrom=2026-03-20&dateTo=2026-03-20&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("正常系: データ0件で空配列")
        void getTransition_noData_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory-transition?warehouseId=" + warehouseId
                            + "&productId=" + productAmbId
                            + "&dateFrom=2026-03-20&dateTo=2026-03-20&format=json",
                    adminHeaders);
            assertThat(parseJson(response.getBody())).isEmpty();
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getTransition_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory-transition?warehouseId=99999999"
                            + "&productId=" + productAmbId + "&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("異常系: 存在しない商品IDで404")
        void getTransition_productNotFound_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory-transition?warehouseId=" + warehouseId
                            + "&productId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(parseJson(response.getBody()).get("code").asText())
                    .isEqualTo("PRODUCT_NOT_FOUND");
        }
    }

    // ========================================================
    // RPT-09: 在庫訂正一覧
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/inventory-correction — RPT-09 在庫訂正一覧")
    class InventoryCorrectionReport {

        @Test
        @DisplayName("正常系: 在庫訂正データが返る")
        void getCorrection_data_returnsItems() throws Exception {
            insertInventoryMovement(locA01_01_01_01, "A-01-A01-01-01-01", productAmbId,
                    "AMB-001", "ミネラルウォーター", "CORRECTION", 5, 105,
                    "2026-03-20T10:00:00+09:00", "棚卸差異の補正");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory-correction?warehouseId=" + warehouseId
                            + "&correctionDateFrom=2026-03-20&correctionDateTo=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("reason").asText()).isEqualTo("棚卸差異の補正");
            assertThat(body.get(0).get("quantityChange").asInt()).isEqualTo(5);
        }

        @Test
        @DisplayName("正常系: CORRECTION以外は除外される")
        void getCorrection_otherTypes_excluded() throws Exception {
            insertInventoryMovement(locA01_01_01_01, "A-01-A01-01-01-01", productAmbId,
                    "AMB-001", "ミネラルウォーター", "INBOUND", 50, 50,
                    "2026-03-20T10:00:00+09:00", null);
            insertInventoryMovement(locA01_01_01_01, "A-01-A01-01-01-01", productAmbId,
                    "AMB-001", "ミネラルウォーター", "CORRECTION", 5, 55,
                    "2026-03-20T11:00:00+09:00", "差異補正");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory-correction?warehouseId=" + warehouseId
                            + "&correctionDateFrom=2026-03-20&correctionDateTo=2026-03-20&format=json",
                    adminHeaders);

            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getCorrection_pdf_returnsBinary() {
            insertInventoryMovement(locA01_01_01_01, "A-01-A01-01-01-01", productAmbId,
                    "AMB-001", "ミネラルウォーター", "CORRECTION", 5, 105,
                    "2026-03-20T10:00:00+09:00", "差異補正");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inventory-correction?warehouseId=" + warehouseId
                            + "&correctionDateFrom=2026-03-20&correctionDateTo=2026-03-20&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getCorrection_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inventory-correction?warehouseId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ========================================================
    // RPT-10: 棚卸リスト
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/stocktake-list — RPT-10 棚卸リスト")
    class StocktakeListReport {

        @Test
        @DisplayName("正常系: stocktakeId指定で明細データが返る")
        void getStocktakeList_byStocktakeId_returnsItems() throws Exception {
            Long stocktakeId = insertStocktake("ST-RPT10-001", "STARTED", false, false);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/stocktake-list?stocktakeId=" + stocktakeId
                            + "&hideBookQty=true&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("productCode").asText()).isEqualTo("AMB-001");
            assertThat(body.get(0).get("systemQuantity").asInt()).isEqualTo(10);
        }

        @Test
        @DisplayName("正常系: buildingId指定（プレビュー）でinventoryから取得")
        void getStocktakeList_byBuildingId_returnsInventoryRows() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 25, 0);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/stocktake-list?buildingId=" + buildingAId
                            + "&hideBookQty=false&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getStocktakeList_pdf_returnsBinary() {
            Long stocktakeId = insertStocktake("ST-RPT10-PDF", "STARTED", false, false);

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/stocktake-list?stocktakeId=" + stocktakeId
                            + "&hideBookQty=true&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない棚卸IDで404")
        void getStocktakeList_stocktakeNotFound_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/stocktake-list?stocktakeId=99999999"
                            + "&hideBookQty=true&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(parseJson(response.getBody()).get("code").asText())
                    .isEqualTo("STOCKTAKE_NOT_FOUND");
        }

        @Test
        @DisplayName("異常系: stocktakeId/buildingId 両方未指定で422")
        void getStocktakeList_noKey_returns422() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/stocktake-list?hideBookQty=true&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    // ========================================================
    // RPT-11: 棚卸結果レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/stocktake-result — RPT-11 棚卸結果")
    class StocktakeResultReport {

        @Test
        @DisplayName("正常系: 棚卸結果データが返る（差異あり）")
        void getStocktakeResult_withDiff_returnsItems() throws Exception {
            Long stocktakeId = insertStocktake("ST-RPT11-001", "CONFIRMED", true, true);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/stocktake-result?stocktakeId=" + stocktakeId + "&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("diffQuantity").asInt()).isEqualTo(-2);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getStocktakeResult_pdf_returnsBinary() {
            Long stocktakeId = insertStocktake("ST-RPT11-PDF", "CONFIRMED", true, false);

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/stocktake-result?stocktakeId=" + stocktakeId + "&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない棚卸IDで404")
        void getStocktakeResult_notFound_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/stocktake-result?stocktakeId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(parseJson(response.getBody()).get("code").asText())
                    .isEqualTo("STOCKTAKE_NOT_FOUND");
        }
    }
}
