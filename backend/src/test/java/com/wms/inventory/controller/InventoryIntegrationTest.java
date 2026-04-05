package com.wms.inventory.controller;

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

@DisplayName("結合テスト: 在庫管理")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryIntegrationTest extends IntegrationTestBase {

    private static final String INVENTORY_URL = "/api/v1/inventory";
    private static final String MOVE_URL = "/api/v1/inventory/move";
    private static final String BREAKDOWN_URL = "/api/v1/inventory/breakdown";
    private static final String CORRECTION_URL = "/api/v1/inventory/correction";
    private static final String CORRECTION_HISTORY_URL = "/api/v1/inventory/correction-history";
    private static final String STOCKTAKES_URL = "/api/v1/inventory/stocktakes";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";

    private HttpHeaders adminHeaders;
    private Long warehouseId;
    private Long buildingAId;    // A棟（常温）
    private Long buildingBId;    // B棟（冷蔵・冷凍）
    private Long areaA01Id;      // A01エリア
    private Long areaB01Id;      // B01エリア（冷蔵）
    // テスト用ロケーション ID
    private Long locA01_01_01_01; // A-01-A01-01-01-01
    private Long locA01_01_01_02; // A-01-A01-01-01-02
    private Long locA01_02_01_01; // A-01-A01-02-01-01 (空ロケーション用)
    private Long locA02_01_01_01; // A-01-A02-01-01-01
    private Long locB01_01_01_01; // B-01-B01-01-01-01 (冷蔵)
    // テスト用商品 ID
    private Long productAmbId;    // AMB-001 (常温, ロットなし, 期限なし)
    private Long productAmb2Id;   // AMB-002 (常温, ロットなし, 期限なし)
    private Long productRefId;    // REF-001 (冷蔵, ロットあり, 期限あり)

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        buildingAId = jdbcTemplate.queryForObject(
                "SELECT id FROM buildings WHERE building_code = 'A' AND warehouse_id = ?",
                Long.class, warehouseId);
        buildingBId = jdbcTemplate.queryForObject(
                "SELECT id FROM buildings WHERE building_code = 'B' AND warehouse_id = ?",
                Long.class, warehouseId);
        areaA01Id = jdbcTemplate.queryForObject(
                "SELECT id FROM areas WHERE area_code = 'A01' AND building_id = ?",
                Long.class, buildingAId);
        areaB01Id = jdbcTemplate.queryForObject(
                "SELECT id FROM areas WHERE area_code = 'B01' AND building_id = ?",
                Long.class, buildingBId);

        locA01_01_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);
        locA01_01_01_02 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-02' AND warehouse_id = ?",
                Long.class, warehouseId);
        locA01_02_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-02-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);
        locA02_01_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A02-01-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);
        locB01_01_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'B-01-B01-01-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);

        productAmbId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);
        productAmb2Id = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
        productRefId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'REF-001'", Long.class);
    }

    @BeforeEach
    void setUp() {
        // テスト間の独立性のためにデータクリーンアップ
        jdbcTemplate.update("DELETE FROM inventory_movements");
        jdbcTemplate.update("DELETE FROM stocktake_lines");
        jdbcTemplate.update("DELETE FROM stocktake_headers");
        jdbcTemplate.update("DELETE FROM inventories");
        // 棚卸ロック解除
        jdbcTemplate.update("UPDATE locations SET is_stocktaking_locked = false WHERE warehouse_id = ?", warehouseId);

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    // ========== ヘルパーメソッド ==========

    private void insertInventory(Long locationId, Long productId, String unitType,
                                  int quantity, int allocatedQty) {
        insertInventory(locationId, productId, unitType, quantity, allocatedQty, null, null);
    }

    private void insertInventory(Long locationId, Long productId, String unitType,
                                  int quantity, int allocatedQty,
                                  String lotNumber, LocalDate expiryDate) {
        jdbcTemplate.update(
                "INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type, " +
                        "lot_number, expiry_date, quantity, allocated_qty, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())",
                warehouseId, locationId, productId, unitType,
                lotNumber, expiryDate, quantity, allocatedQty);
    }

    private Long getInventoryId(Long locationId, Long productId, String unitType) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = ?",
                Long.class, locationId, productId, unitType);
    }

    private int getInventoryQty(Long locationId, Long productId, String unitType) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = ?",
                Integer.class, locationId, productId, unitType);
    }

    private int countInventoryMovements(String movementType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE movement_type = ?",
                Integer.class, movementType);
    }

    private int countInventoryMovementsByLocation(Long locationId, String movementType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE location_id = ? AND movement_type = ?",
                Integer.class, locationId, movementType);
    }

    private void lockLocationForStocktake(Long locationId) {
        jdbcTemplate.update("UPDATE locations SET is_stocktaking_locked = true WHERE id = ?", locationId);
    }

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> put(String url, String body, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    // ========================================================
    // GET /api/v1/inventory — 在庫一覧照会
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/inventory — 在庫一覧照会")
    class ListInventory {

        @Test
        @DisplayName("SC-INV-001: 在庫一覧をロケーション別で照会する")
        void listByLocation_returnsLocationItems() throws Exception {
            // テストデータ投入
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 0);
            insertInventory(locA01_01_01_02, productAmb2Id, "CASE", 5, 0);

            String url = INVENTORY_URL + "?warehouseId=" + warehouseId
                    + "&page=0&size=20&sort=locationCode,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("content")).isTrue();
            assertThat(json.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);

            // content配列に期待するフィールドが含まれている
            JsonNode firstItem = json.get("content").get(0);
            assertThat(firstItem.has("locationCode")).isTrue();
            assertThat(firstItem.has("productCode")).isTrue();
            assertThat(firstItem.has("unitType")).isTrue();
            assertThat(firstItem.has("quantity")).isTrue();
            assertThat(firstItem.has("allocatedQty")).isTrue();
            assertThat(firstItem.has("availableQty")).isTrue();
        }

        @Test
        @DisplayName("SC-INV-002: 在庫一覧を商品合計で照会する")
        void listByProductSummary_returnsAggregated() throws Exception {
            // 同一商品を複数ロケーションに配置
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 0);
            insertInventory(locA01_01_01_02, productAmbId, "PIECE", 20, 0);

            String url = INVENTORY_URL + "?warehouseId=" + warehouseId
                    + "&viewType=PRODUCT_SUMMARY&page=0&size=20&sort=productCode,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("content")).isTrue();

            // AMB-001の集計結果を検証
            JsonNode content = json.get("content");
            boolean foundAmb001 = false;
            for (JsonNode item : content) {
                if ("AMB-001".equals(item.get("productCode").asText())) {
                    foundAmb001 = true;
                    assertThat(item.get("caseQuantity").asInt()).isEqualTo(10);
                    assertThat(item.get("pieceQuantity").asInt()).isEqualTo(20);
                    break;
                }
            }
            assertThat(foundAmb001).isTrue();
        }

        @Test
        @DisplayName("SC-INV-003: 在庫一覧をロケーションプレフィックスで絞り込む")
        void listByLocation_filterByPrefix() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 0);
            insertInventory(locA02_01_01_01, productAmb2Id, "CASE", 5, 0);

            String url = INVENTORY_URL + "?warehouseId=" + warehouseId
                    + "&locationCodePrefix=A-01-A01&page=0&size=20&sort=locationCode,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            // A01エリアのみ返る
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("locationCode").asText()).startsWith("A-01-A01");
            }
        }

        @Test
        @DisplayName("SC-INV-003: 在庫一覧を荷姿でフィルタする")
        void listByLocation_filterByUnitType() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 0);
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 5, 0);

            String url = INVENTORY_URL + "?warehouseId=" + warehouseId
                    + "&unitType=CASE&page=0&size=20&sort=locationCode,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("unitType").asText()).isEqualTo("CASE");
            }
        }

        @Test
        @DisplayName("SC-INV-005: 引当済み在庫の有効在庫数が正しく計算される")
        void listByLocation_allocatedInventory_showsAvailable() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 3);

            String url = INVENTORY_URL + "?warehouseId=" + warehouseId
                    + "&page=0&size=20&sort=locationCode,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            JsonNode item = json.get("content").get(0);
            assertThat(item.get("quantity").asInt()).isEqualTo(10);
            assertThat(item.get("allocatedQty").asInt()).isEqualTo(3);
            assertThat(item.get("availableQty").asInt()).isEqualTo(7);
        }

        @Test
        @DisplayName("SC-INV-004: 倉庫IDを変えると異なる在庫が返る")
        void listByLocation_differentWarehouse_returnsDifferentData() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 0);

            // WH001の在庫は返る
            String url = INVENTORY_URL + "?warehouseId=" + warehouseId
                    + "&page=0&size=20&sort=locationCode,asc";
            ResponseEntity<String> response = get(url, adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parseJson(response.getBody()).get("totalElements").asInt()).isGreaterThanOrEqualTo(1);

            // 存在しない倉庫IDでは0件
            String url2 = INVENTORY_URL + "?warehouseId=999999"
                    + "&page=0&size=20&sort=locationCode,asc";
            ResponseEntity<String> response2 = get(url2, adminHeaders);
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parseJson(response2.getBody()).get("totalElements").asInt()).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-INV-004: 保管条件でフィルタする")
        void listByLocation_filterByStorageCondition() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 0);
            insertInventory(locB01_01_01_01, productRefId, "CASE", 5, 0,
                    "LOT-TEST", LocalDate.of(2026, 6, 30));

            String url = INVENTORY_URL + "?warehouseId=" + warehouseId
                    + "&storageCondition=REFRIGERATED&page=0&size=20&sort=locationCode,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
            // 冷蔵ロケーション（B-01-B01系）のみ返る
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("locationCode").asText()).startsWith("B-01-B01");
            }
        }
    }

    // ========================================================
    // POST /api/v1/inventory/move — 在庫移動
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/inventory/move — 在庫移動")
    class MoveInventory {

        @Test
        @DisplayName("SC-INV-010: 在庫移動が成功する")
        void move_success() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 1
                    }""", locA01_01_01_01, productAmbId, locA01_02_01_01);

            ResponseEntity<String> response = postJson(MOVE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("movedQty").asInt()).isEqualTo(1);
            assertThat(json.get("fromQuantityAfter").asInt()).isEqualTo(4);
            assertThat(json.get("toQuantityAfter").asInt()).isEqualTo(1);

            // DB検証
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(4);
            assertThat(getInventoryQty(locA01_02_01_01, productAmbId, "CASE")).isEqualTo(1);
            assertThat(countInventoryMovements("MOVE_OUT")).isEqualTo(1);
            assertThat(countInventoryMovements("MOVE_IN")).isEqualTo(1);
        }

        @Test
        @DisplayName("SC-INV-011: 有効在庫数を超える移動はエラー")
        void move_insufficientStock_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 2, 0);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 3
                    }""", locA01_01_01_01, productAmbId, locA01_02_01_01);

            ResponseEntity<String> response = postJson(MOVE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVENTORY_INSUFFICIENT");

            // DB: 在庫は変化なし
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(2);
            assertThat(countInventoryMovements("MOVE_OUT")).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-INV-012: 棚卸ロック中のロケーションからの移動はエラー")
        void move_fromStocktakeLocked_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);
            lockLocationForStocktake(locA01_01_01_01);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 1
                    }""", locA01_01_01_01, productAmbId, locA01_02_01_01);

            ResponseEntity<String> response = postJson(MOVE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }

        @Test
        @DisplayName("SC-INV-012: 棚卸ロック中のロケーションへの移動もエラー")
        void move_toStocktakeLocked_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);
            lockLocationForStocktake(locA01_02_01_01);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 1
                    }""", locA01_01_01_01, productAmbId, locA01_02_01_01);

            ResponseEntity<String> response = postJson(MOVE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }

        @Test
        @DisplayName("SC-INV-013: 移動先ロケーション収容上限超過はエラー")
        void move_capacityExceeded_returns422() throws Exception {
            // LOCATION_CAPACITY_CASE = 1（システムパラメータ初期値）
            // 移動先に既にCASE 1個 → 上限到達
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);
            insertInventory(locA01_02_01_01, productAmbId, "CASE", 1, 0);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 1
                    }""", locA01_01_01_01, productAmbId, locA01_02_01_01);

            ResponseEntity<String> response = postJson(MOVE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVENTORY_CAPACITY_EXCEEDED");
        }

        @Test
        @DisplayName("SC-INV-014: 引当済み在庫の移動は有効在庫数まで制限される")
        void move_allocatedInventory_limitedToAvailable() throws Exception {
            // qty=5, allocated=3 → 有効在庫=2
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 3);

            // 3個移動（有効在庫2を超える）→ エラー
            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 3
                    }""", locA01_01_01_01, productAmbId, locA01_02_01_01);

            ResponseEntity<String> response = postJson(MOVE_URL, body, adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

            // 2個移動（有効在庫ちょうど）→ 成功
            String body2 = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 2
                    }""", locA01_01_01_01, productAmbId, locA01_02_01_01);

            ResponseEntity<String> response2 = postJson(MOVE_URL, body2, adminHeaders);
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);

            // DB検証: 引当数は変化なし
            int allocatedQty = jdbcTemplate.queryForObject(
                    "SELECT allocated_qty FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = 'CASE'",
                    Integer.class, locA01_01_01_01, productAmbId);
            assertThat(allocatedQty).isEqualTo(3);
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(3);
            assertThat(getInventoryQty(locA01_02_01_01, productAmbId, "CASE")).isEqualTo(2);
        }

        @Test
        @DisplayName("SC-INV-015: 移動先に別商品が存在する場合はエラー")
        void move_differentProductAtDestination_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);
            insertInventory(locA01_02_01_01, productAmb2Id, "CASE", 3, 0);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 1
                    }""", locA01_01_01_01, productAmbId, locA01_02_01_01);

            ResponseEntity<String> response = postJson(MOVE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("LOCATION_PRODUCT_MISMATCH");
        }
    }

    // ========================================================
    // POST /api/v1/inventory/breakdown — ばらし
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/inventory/breakdown — ばらし")
    class BreakdownInventory {

        @Test
        @DisplayName("SC-INV-020: ケースからボールへのばらしが成功する")
        void breakdown_caseToball_success() throws Exception {
            // AMB-001: case_quantity=6 (1ケース=6ボール)
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 0);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "fromUnitType": "CASE",
                        "breakdownQty": 1,
                        "toUnitType": "BALL",
                        "toLocationId": %d
                    }""", locA01_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> response = postJson(BREAKDOWN_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("breakdownQty").asInt()).isEqualTo(1);
            assertThat(json.get("convertedQty").asInt()).isEqualTo(6);
            assertThat(json.get("fromQuantityAfter").asInt()).isEqualTo(2);
            assertThat(json.get("toQuantityAfter").asInt()).isEqualTo(6);

            // DB検証
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(2);
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "BALL")).isEqualTo(6);
            assertThat(countInventoryMovements("BREAKDOWN_OUT")).isEqualTo(1);
            assertThat(countInventoryMovements("BREAKDOWN_IN")).isEqualTo(1);
        }

        @Test
        @DisplayName("SC-INV-021: ボールからバラへのばらしが成功する")
        void breakdown_ballToPiece_success() throws Exception {
            // AMB-001: ball_quantity=4 (1ボール=4バラ)
            insertInventory(locA01_01_01_01, productAmbId, "BALL", 4, 0);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "fromUnitType": "BALL",
                        "breakdownQty": 2,
                        "toUnitType": "PIECE",
                        "toLocationId": %d
                    }""", locA01_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> response = postJson(BREAKDOWN_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("breakdownQty").asInt()).isEqualTo(2);
            assertThat(json.get("convertedQty").asInt()).isEqualTo(8); // 2 * 4 = 8
            assertThat(json.get("fromQuantityAfter").asInt()).isEqualTo(2);

            // DB検証
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "BALL")).isEqualTo(2);
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "PIECE")).isEqualTo(8);
        }

        @Test
        @DisplayName("SC-INV-022: ケースからバラへのばらしが成功する")
        void breakdown_caseToPiece_success() throws Exception {
            // AMB-001: case_quantity=6, ball_quantity=4 → 1ケース = 6*4 = 24バラ
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 2, 0);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "fromUnitType": "CASE",
                        "breakdownQty": 1,
                        "toUnitType": "PIECE",
                        "toLocationId": %d
                    }""", locA01_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> response = postJson(BREAKDOWN_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("breakdownQty").asInt()).isEqualTo(1);
            assertThat(json.get("convertedQty").asInt()).isEqualTo(24); // 6 * 4 = 24

            // DB検証
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(1);
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "PIECE")).isEqualTo(24);
        }

        @Test
        @DisplayName("SC-INV-023: ばらし元の有効在庫数不足はエラー")
        void breakdown_insufficientStock_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 1, 0);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "fromUnitType": "CASE",
                        "breakdownQty": 2,
                        "toUnitType": "BALL",
                        "toLocationId": %d
                    }""", locA01_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> response = postJson(BREAKDOWN_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("SC-INV-024: 引当済み在庫のばらしは有効在庫数まで制限される")
        void breakdown_allocatedInventory_limitedToAvailable() throws Exception {
            // qty=3, allocated=2 → 有効在庫=1
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 2);

            // 2個ばらし → エラー
            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "fromUnitType": "CASE",
                        "breakdownQty": 2,
                        "toUnitType": "BALL",
                        "toLocationId": %d
                    }""", locA01_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> response = postJson(BREAKDOWN_URL, body, adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

            // 1個ばらし → 成功
            String body2 = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "fromUnitType": "CASE",
                        "breakdownQty": 1,
                        "toUnitType": "BALL",
                        "toLocationId": %d
                    }""", locA01_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> response2 = postJson(BREAKDOWN_URL, body2, adminHeaders);
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("SC-INV-025: 棚卸ロック中のロケーションでのばらしはエラー")
        void breakdown_stocktakeLocked_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 0);
            lockLocationForStocktake(locA01_01_01_01);

            String body = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "fromUnitType": "CASE",
                        "breakdownQty": 1,
                        "toUnitType": "BALL",
                        "toLocationId": %d
                    }""", locA01_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> response = postJson(BREAKDOWN_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }
    }

    // ========================================================
    // POST /api/v1/inventory/correction — 在庫訂正
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/inventory/correction — 在庫訂正")
    class CorrectionInventory {

        @Test
        @DisplayName("SC-INV-030: 在庫訂正（増加）が成功する")
        void correction_increase_success() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 0);

            String body = String.format("""
                    {
                        "locationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "newQty": 5,
                        "reason": "入庫漏れの補正"
                    }""", locA01_01_01_01, productAmbId);

            ResponseEntity<String> response = postJson(CORRECTION_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("quantityBefore").asInt()).isEqualTo(3);
            assertThat(json.get("quantityAfter").asInt()).isEqualTo(5);
            assertThat(json.get("reason").asText()).isEqualTo("入庫漏れの補正");

            // DB検証
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(5);
            assertThat(countInventoryMovements("CORRECTION")).isEqualTo(1);

            // movement詳細の検証
            int movementQty = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM inventory_movements WHERE movement_type = 'CORRECTION'",
                    Integer.class);
            assertThat(movementQty).isEqualTo(2); // +2
        }

        @Test
        @DisplayName("SC-INV-031: 在庫訂正（減少）が成功する")
        void correction_decrease_success() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);

            String body = String.format("""
                    {
                        "locationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "newQty": 2,
                        "reason": "破損品の廃棄"
                    }""", locA01_01_01_01, productAmbId);

            ResponseEntity<String> response = postJson(CORRECTION_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("quantityBefore").asInt()).isEqualTo(5);
            assertThat(json.get("quantityAfter").asInt()).isEqualTo(2);

            // DB検証
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(2);
            int movementQty = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM inventory_movements WHERE movement_type = 'CORRECTION'",
                    Integer.class);
            assertThat(movementQty).isEqualTo(-3); // -3
        }

        @Test
        @DisplayName("SC-INV-032: 在庫訂正でCORRECTION記録が作成される（差分0でも）")
        void correction_zeroDiff_createsMovement() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 0);

            String body = String.format("""
                    {
                        "locationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "newQty": 3,
                        "reason": "確認のための訂正記録"
                    }""", locA01_01_01_01, productAmbId);

            ResponseEntity<String> response = postJson(CORRECTION_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // DB検証: 数量変化なしでもCORRECTION記録が作成される
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(3);
            assertThat(countInventoryMovements("CORRECTION")).isEqualTo(1);

            String reason = jdbcTemplate.queryForObject(
                    "SELECT correction_reason FROM inventory_movements WHERE movement_type = 'CORRECTION'",
                    String.class);
            assertThat(reason).isEqualTo("確認のための訂正記録");
        }

        @Test
        @DisplayName("SC-INV-033: 棚卸ロック中の在庫訂正はエラー")
        void correction_stocktakeLocked_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 0);
            lockLocationForStocktake(locA01_01_01_01);

            String body = String.format("""
                    {
                        "locationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "newQty": 10,
                        "reason": "テスト"
                    }""", locA01_01_01_01, productAmbId);

            ResponseEntity<String> response = postJson(CORRECTION_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(3);
        }

        @Test
        @DisplayName("SC-INV-034: 訂正後数量が引当数を下回る場合はエラー")
        void correction_belowAllocated_returns422() throws Exception {
            // qty=5, allocated=3
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 3);

            // 訂正後 2 (< 引当数 3) → エラー
            String body = String.format("""
                    {
                        "locationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "newQty": 2,
                        "reason": "テスト"
                    }""", locA01_01_01_01, productAmbId);

            ResponseEntity<String> response = postJson(CORRECTION_URL, body, adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode errJson = parseJson(response.getBody());
            assertThat(errJson.get("code").asText()).isEqualTo("CORRECTION_BELOW_ALLOCATED");

            // 引当数と同値（3）は成功
            String body2 = String.format("""
                    {
                        "locationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "newQty": 3,
                        "reason": "引当数ちょうどに訂正"
                    }""", locA01_01_01_01, productAmbId);

            ResponseEntity<String> response2 = postJson(CORRECTION_URL, body2, adminHeaders);
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getInventoryQty(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(3);
        }

        @Test
        @DisplayName("在庫訂正後に訂正履歴が照会できる")
        void correctionHistory_afterCorrection_returnsRecords() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 0);

            // 訂正実行
            String body = String.format("""
                    {
                        "locationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "newQty": 5,
                        "reason": "入庫漏れ補正"
                    }""", locA01_01_01_01, productAmbId);
            postJson(CORRECTION_URL, body, adminHeaders);

            // 訂正履歴照会
            String url = CORRECTION_HISTORY_URL
                    + "?warehouseId=" + warehouseId
                    + "&locationId=" + locA01_01_01_01
                    + "&productId=" + productAmbId
                    + "&unitType=CASE";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.isArray()).isTrue();
            assertThat(json.size()).isGreaterThanOrEqualTo(1);
            assertThat(json.get(0).get("reason").asText()).isEqualTo("入庫漏れ補正");
            assertThat(json.get(0).get("quantityBefore").asInt()).isEqualTo(3);
            assertThat(json.get(0).get("quantityAfter").asInt()).isEqualTo(5);
        }
    }

    // ========================================================
    // 棚卸 API — 一気通貫テスト
    // ========================================================

    @Nested
    @DisplayName("棚卸 API — 一気通貫テスト")
    class StocktakeLifecycle {

        @Test
        @DisplayName("SC-INV-050/051/060/061/070/071/072: 棚卸の一気通貫テスト（開始→実数入力→確定→差異反映・ロック解除）")
        void stocktake_fullLifecycle() throws Exception {
            // テストデータ: A01エリアに在庫を投入
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);
            insertInventory(locA01_01_01_02, productAmb2Id, "CASE", 3, 0);

            // ---- Step 1: 棚卸開始 (SC-INV-050) ----
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s",
                        "note": "結合テスト用棚卸"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());

            ResponseEntity<String> startResponse = postJson(STOCKTAKES_URL, startBody, adminHeaders);

            assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode startJson = parseJson(startResponse.getBody());
            Long stocktakeId = startJson.get("id").asLong();
            assertThat(startJson.get("stocktakeNumber").asText()).startsWith("ST-");
            assertThat(startJson.get("status").asText()).isEqualTo("STARTED");
            int totalLines = startJson.get("totalLines").asInt();
            assertThat(totalLines).isGreaterThanOrEqualTo(2);

            // DB検証: 棚卸ヘッダが作成された
            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM stocktake_headers WHERE id = ?", String.class, stocktakeId);
            assertThat(dbStatus).isEqualTo("STARTED");

            // DB検証 (SC-INV-051): 棚卸対象ロケーションがロックされている
            Boolean isLocked = jdbcTemplate.queryForObject(
                    "SELECT is_stocktaking_locked FROM locations WHERE id = ?",
                    Boolean.class, locA01_01_01_01);
            assertThat(isLocked).isTrue();

            // ---- Step 2: 棚卸明細取得 ----
            ResponseEntity<String> detailResponse = get(
                    STOCKTAKES_URL + "/" + stocktakeId + "?page=0&size=100", adminHeaders);
            assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode detailJson = parseJson(detailResponse.getBody());
            assertThat(detailJson.get("status").asText()).isEqualTo("STARTED");

            JsonNode lines = detailJson.get("lines").get("content");
            assertThat(lines.size()).isGreaterThanOrEqualTo(2);

            // ---- Step 3: 実数入力 (SC-INV-060) ----
            // 全明細に実数を入力（AMB-001: 棚卸前5→実数4、AMB-002: 棚卸前3→実数3）
            StringBuilder linesBody = new StringBuilder("[");
            for (int i = 0; i < lines.size(); i++) {
                JsonNode line = lines.get(i);
                long lineId = line.get("lineId").asLong();
                int qtyBefore = line.get("quantityBefore").asInt();
                // 最初の1件だけ差異を作る（-1）
                int actualQty = (i == 0) ? qtyBefore - 1 : qtyBefore;
                if (i > 0) linesBody.append(",");
                linesBody.append(String.format("{\"lineId\": %d, \"actualQty\": %d}", lineId, actualQty));
            }
            linesBody.append("]");

            String saveBody = "{\"lines\": " + linesBody + "}";
            ResponseEntity<String> saveResponse = put(
                    STOCKTAKES_URL + "/" + stocktakeId + "/lines", saveBody, adminHeaders);

            assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode saveJson = parseJson(saveResponse.getBody());
            assertThat(saveJson.get("updatedCount").asInt()).isEqualTo(lines.size());
            assertThat(saveJson.get("countedLines").asInt()).isEqualTo(lines.size());

            // DB検証: 明細に実数がセットされている
            int countedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM stocktake_lines WHERE stocktake_header_id = ? AND is_counted = true",
                    Integer.class, stocktakeId);
            assertThat(countedCount).isEqualTo(lines.size());

            // ---- Step 4: 棚卸確定 (SC-INV-070) ----
            ResponseEntity<String> confirmResponse = postNoBody(
                    STOCKTAKES_URL + "/" + stocktakeId + "/confirm", adminHeaders);

            assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode confirmJson = parseJson(confirmResponse.getBody());
            assertThat(confirmJson.get("status").asText()).isEqualTo("CONFIRMED");
            assertThat(confirmJson.get("adjustedLines").asInt()).isEqualTo(1); // 差異ありは1件

            // DB検証 (SC-INV-070): 在庫数が実数に更新されている
            // 最初のline（差異あり）の在庫を検証
            JsonNode firstLine = lines.get(0);
            Long firstLocId = firstLine.get("locationId").asLong();
            Long firstProdId = firstLine.get("productId").asLong();
            String firstUnit = firstLine.get("unitType").asText();
            int expectedQty = firstLine.get("quantityBefore").asInt() - 1;
            int actualDbQty = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = ?",
                    Integer.class, firstLocId, firstProdId, firstUnit);
            assertThat(actualDbQty).isEqualTo(expectedQty);

            // DB検証 (SC-INV-071): STOCKTAKE_ADJUSTMENT movement記録
            int adjustmentCount = countInventoryMovements("STOCKTAKE_ADJUSTMENT");
            assertThat(adjustmentCount).isEqualTo(1); // 差異あり1件分のみ

            // DB検証 (SC-INV-072): 棚卸ロックが解除されている
            Boolean isLockedAfter = jdbcTemplate.queryForObject(
                    "SELECT is_stocktaking_locked FROM locations WHERE id = ?",
                    Boolean.class, locA01_01_01_01);
            assertThat(isLockedAfter).isFalse();

            // 確定後のステータス
            String confirmedStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM stocktake_headers WHERE id = ?", String.class, stocktakeId);
            assertThat(confirmedStatus).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("SC-INV-052: 同一範囲で実施中の棚卸がある場合は開始不可")
        void stocktake_duplicateStart_returns409() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);

            // 1回目: 成功
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s",
                        "note": "1回目"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());

            ResponseEntity<String> first = postJson(STOCKTAKES_URL, startBody, adminHeaders);
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // 2回目: 同範囲で開始 → エラー（ロケーションが棚卸ロック中）
            String startBody2 = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s",
                        "note": "2回目"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());

            ResponseEntity<String> second = postJson(STOCKTAKES_URL, startBody2, adminHeaders);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode errJson = parseJson(second.getBody());
            assertThat(errJson.get("code").asText()).isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }

        @Test
        @DisplayName("SC-INV-073: 未入力明細がある状態での棚卸確定はエラー")
        void stocktake_confirmWithUncounted_returnsError() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);
            insertInventory(locA01_01_01_02, productAmb2Id, "CASE", 3, 0);

            // 棚卸開始
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());

            ResponseEntity<String> startResp = postJson(STOCKTAKES_URL, startBody, adminHeaders);
            Long stocktakeId = parseJson(startResp.getBody()).get("id").asLong();

            // 明細を一部だけ入力（1件のみ）
            ResponseEntity<String> detailResp = get(
                    STOCKTAKES_URL + "/" + stocktakeId + "?page=0&size=100", adminHeaders);
            JsonNode firstLine = parseJson(detailResp.getBody()).get("lines").get("content").get(0);
            long firstLineId = firstLine.get("lineId").asLong();

            String partialSave = String.format("""
                    {"lines": [{"lineId": %d, "actualQty": 5}]}""", firstLineId);
            put(STOCKTAKES_URL + "/" + stocktakeId + "/lines", partialSave, adminHeaders);

            // 確定 → エラー（未入力明細あり）
            ResponseEntity<String> confirmResp = postNoBody(
                    STOCKTAKES_URL + "/" + stocktakeId + "/confirm", adminHeaders);

            assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

            // DB: ステータスはSTARTEDのまま
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM stocktake_headers WHERE id = ?", String.class, stocktakeId);
            assertThat(status).isEqualTo("STARTED");
        }

        @Test
        @DisplayName("SC-INV-074: 実数が引当数を下回る明細がある場合は棚卸確定不可")
        void stocktake_countedBelowAllocated_returnsError() throws Exception {
            // allocated_qty=3 の在庫
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 3);

            // 棚卸開始
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());

            ResponseEntity<String> startResp = postJson(STOCKTAKES_URL, startBody, adminHeaders);
            Long stocktakeId = parseJson(startResp.getBody()).get("id").asLong();

            // 全明細を取得して、実数を引当数未満（2）で入力
            ResponseEntity<String> detailResp = get(
                    STOCKTAKES_URL + "/" + stocktakeId + "?page=0&size=100", adminHeaders);
            JsonNode allLines = parseJson(detailResp.getBody()).get("lines").get("content");

            StringBuilder linesBody = new StringBuilder("[");
            for (int i = 0; i < allLines.size(); i++) {
                JsonNode line = allLines.get(i);
                long lineId = line.get("lineId").asLong();
                // 引当ありの在庫は実数2（引当数3を下回る）
                int actualQty = 2;
                if (i > 0) linesBody.append(",");
                linesBody.append(String.format("{\"lineId\": %d, \"actualQty\": %d}", lineId, actualQty));
            }
            linesBody.append("]");

            put(STOCKTAKES_URL + "/" + stocktakeId + "/lines",
                    "{\"lines\": " + linesBody + "}", adminHeaders);

            // 確定 → エラー（引当数下回り）
            ResponseEntity<String> confirmResp = postNoBody(
                    STOCKTAKES_URL + "/" + stocktakeId + "/confirm", adminHeaders);

            assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    // ========================================================
    // 棚卸一覧・詳細照会
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/inventory/stocktakes — 棚卸一覧・詳細")
    class StocktakeQuery {

        @Test
        @DisplayName("棚卸一覧が正常に取得できる")
        void listStocktakes_returnsPage() throws Exception {
            // 棚卸を1件作成
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());
            postJson(STOCKTAKES_URL, startBody, adminHeaders);

            // 一覧取得
            String url = STOCKTAKES_URL + "?warehouseId=" + warehouseId
                    + "&page=0&size=20&sort=startedAt,desc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
            JsonNode first = json.get("content").get(0);
            assertThat(first.has("stocktakeNumber")).isTrue();
            assertThat(first.has("status")).isTrue();
            assertThat(first.has("totalLines")).isTrue();
            assertThat(first.has("countedLines")).isTrue();
        }

        @Test
        @DisplayName("棚卸一覧をステータスでフィルタできる")
        void listStocktakes_filterByStatus() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());
            postJson(STOCKTAKES_URL, startBody, adminHeaders);

            // STARTED でフィルタ
            String url = STOCKTAKES_URL + "?warehouseId=" + warehouseId
                    + "&status=STARTED&page=0&size=20&sort=startedAt,desc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("status").asText()).isEqualTo("STARTED");
            }

            // CONFIRMED でフィルタ → 0件
            String url2 = STOCKTAKES_URL + "?warehouseId=" + warehouseId
                    + "&status=CONFIRMED&page=0&size=20&sort=startedAt,desc";
            ResponseEntity<String> response2 = get(url2, adminHeaders);
            assertThat(parseJson(response2.getBody()).get("totalElements").asInt()).isEqualTo(0);
        }
    }

    // ========================================================
    // 棚卸ロック排他テスト (SC-INV-080〜082)
    // ========================================================

    @Nested
    @DisplayName("棚卸ロック中の排他テスト")
    class StocktakeLockExclusion {

        @Test
        @DisplayName("SC-INV-080: 棚卸ロック中に在庫移動を試みるとエラー")
        void stocktakeLock_moveBlocked() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);

            // 棚卸開始でロケーションをロック
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());
            postJson(STOCKTAKES_URL, startBody, adminHeaders);

            // ロック中ロケーションからの移動 → エラー
            String moveBody = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 1
                    }""", locA01_01_01_01, productAmbId, locA02_01_01_01);

            ResponseEntity<String> moveResp = postJson(MOVE_URL, moveBody, adminHeaders);
            assertThat(moveResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(parseJson(moveResp.getBody()).get("code").asText()).isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");

            // ロック外 → ロック中ロケーションへの移動もエラー
            insertInventory(locA02_01_01_01, productAmbId, "CASE", 3, 0);
            String moveBody2 = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "toLocationId": %d,
                        "moveQty": 1
                    }""", locA02_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> moveResp2 = postJson(MOVE_URL, moveBody2, adminHeaders);
            assertThat(moveResp2.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("SC-INV-081: 棚卸ロック中にばらしを試みるとエラー")
        void stocktakeLock_breakdownBlocked() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 0);

            // 棚卸開始でロック
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());
            postJson(STOCKTAKES_URL, startBody, adminHeaders);

            // ばらし → エラー
            String breakdownBody = String.format("""
                    {
                        "fromLocationId": %d,
                        "productId": %d,
                        "fromUnitType": "CASE",
                        "breakdownQty": 1,
                        "toUnitType": "BALL",
                        "toLocationId": %d
                    }""", locA01_01_01_01, productAmbId, locA01_01_01_01);

            ResponseEntity<String> resp = postJson(BREAKDOWN_URL, breakdownBody, adminHeaders);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(parseJson(resp.getBody()).get("code").asText()).isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }

        @Test
        @DisplayName("SC-INV-082: 棚卸ロック中に在庫訂正を試みるとエラー")
        void stocktakeLock_correctionBlocked() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 3, 0);

            // 棚卸開始でロック
            String startBody = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingId": %d,
                        "areaId": %d,
                        "stocktakeDate": "%s"
                    }""", warehouseId, buildingAId, areaA01Id,
                    LocalDate.now().toString());
            postJson(STOCKTAKES_URL, startBody, adminHeaders);

            // 訂正 → エラー
            String correctionBody = String.format("""
                    {
                        "locationId": %d,
                        "productId": %d,
                        "unitType": "CASE",
                        "newQty": 10,
                        "reason": "テスト"
                    }""", locA01_01_01_01, productAmbId);

            ResponseEntity<String> resp = postJson(CORRECTION_URL, correctionBody, adminHeaders);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(parseJson(resp.getBody()).get("code").asText()).isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }
    }
}
