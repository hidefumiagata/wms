package com.wms.returns.controller;

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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("結合テスト: 返品管理")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReturnSlipIntegrationTest extends IntegrationTestBase {

    private static final String RETURNS_URL = "/api/v1/returns";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String VIEWER_CODE = "viewer01";
    private static final String VIEWER_PASSWORD = "Test@1234";

    private HttpHeaders adminHeaders;
    private Long warehouseId;
    private String warehouseCode;
    private Long productAmbId;   // AMB-001
    private Long productAmb2Id;  // AMB-002
    private Long productRefId;   // REF-001 (lot + expiry managed)
    private Long locA01_01_01_01;
    private Long locA01_01_01_02;
    private Long supplierPartnerId;
    private Long adminUserId;

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        warehouseCode = "WH001";

        productAmbId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);
        productAmb2Id = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
        productRefId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'REF-001'", Long.class);

        locA01_01_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);
        locA01_01_01_02 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-02' AND warehouse_id = ?",
                Long.class, warehouseId);

        supplierPartnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'SUP001'", Long.class);

        adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);
    }

    @BeforeAll
    void initAuth() {
        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM inventory_movements");
        jdbcTemplate.update("DELETE FROM return_slips");
        jdbcTemplate.update("DELETE FROM inventories");
        // Reset stocktake lock on locations
        jdbcTemplate.update("UPDATE locations SET is_stocktaking_locked = false");
    }

    // ========== ヘルパーメソッド ==========

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void insertInventory(Long locationId, Long productId, String unitType,
                                  int quantity, int allocatedQty) {
        jdbcTemplate.update(
                "INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type, " +
                        "lot_number, expiry_date, quantity, allocated_qty, updated_at) " +
                        "VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, now())",
                warehouseId, locationId, productId, unitType, quantity, allocatedQty);
    }

    private void insertInventoryWithLot(Long locationId, Long productId, String unitType,
                                         int quantity, int allocatedQty,
                                         String lotNumber, String expiryDate) {
        jdbcTemplate.update(
                "INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type, " +
                        "lot_number, expiry_date, quantity, allocated_qty, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?::date, ?, ?, now())",
                warehouseId, locationId, productId, unitType,
                lotNumber, expiryDate, quantity, allocatedQty);
    }

    private String createReturnRequestBody(String returnType, Long productId, int quantity,
                                            String unitType, String returnReason) {
        return String.format("""
                {
                  "returnType": "%s",
                  "productId": %d,
                  "quantity": %d,
                  "unitType": "%s",
                  "returnReason": "%s"
                }""", returnType, productId, quantity, unitType, returnReason);
    }

    private String createReturnRequestBodyWithLocation(String returnType, Long productId,
                                                        int quantity, String unitType,
                                                        String returnReason, Long locationId) {
        return String.format("""
                {
                  "returnType": "%s",
                  "productId": %d,
                  "quantity": %d,
                  "unitType": "%s",
                  "returnReason": "%s",
                  "locationId": %d
                }""", returnType, productId, quantity, unitType, returnReason, locationId);
    }

    private int countReturnSlips() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM return_slips", Integer.class);
    }

    private int countInventoryMovements() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class);
    }

    private int getInventoryQuantity(Long locationId, Long productId, String unitType) {
        return jdbcTemplate.queryForObject(
                "SELECT SUM(quantity) FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = ?",
                Integer.class, locationId, productId, unitType);
    }

    // ========================================================
    // POST /api/v1/returns — 返品登録
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/returns — 返品登録")
    class CreateReturn {

        @Test
        @DisplayName("SC-001: 入荷返品の登録（在庫影響なし）")
        void create_inboundReturn_returns201AndNoInventoryChange() throws Exception {
            // 在庫データを事前投入（変動しないことの確認用）
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 0);

            String body = String.format("""
                    {
                      "returnType": "INBOUND",
                      "productId": %d,
                      "quantity": 5,
                      "unitType": "CASE",
                      "returnReason": "QUALITY_DEFECT",
                      "relatedSlipNumber": "INB-20260318-0001"
                    }""", productAmbId);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("slipNumber").asText()).startsWith("RTN-I-");
            assertThat(json.get("returnType").asText()).isEqualTo("INBOUND");
            assertThat(json.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(json.get("quantity").asInt()).isEqualTo(5);
            assertThat(json.get("unitType").asText()).isEqualTo("CASE");
            assertThat(json.get("returnReason").asText()).isEqualTo("QUALITY_DEFECT");
            assertThat(json.get("relatedSlipNumber").asText()).isEqualTo("INB-20260318-0001");
            assertThat(json.get("productCode").asText()).isEqualTo("AMB-001");
            assertThat(json.get("warehouseId").asLong()).isEqualTo(warehouseId);

            // DB検証: return_slips に1件追加
            assertThat(countReturnSlips()).isEqualTo(1);

            // DB検証: 在庫は変動なし（入荷返品は在庫操作なし）
            assertThat(getInventoryQuantity(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(10);

            // DB検証: inventory_movements は追加なし
            assertThat(countInventoryMovements()).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-002: 在庫返品の登録（在庫即時減算）")
        void create_inventoryReturn_returns201AndDeductsInventory() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 20, 0);

            String body = createReturnRequestBodyWithLocation(
                    "INVENTORY", productAmbId, 3, "CASE", "EXCESS_QUANTITY", locA01_01_01_01);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("slipNumber").asText()).startsWith("RTN-S-");
            assertThat(json.get("returnType").asText()).isEqualTo("INVENTORY");
            assertThat(json.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(json.get("returnReason").asText()).isEqualTo("EXCESS_QUANTITY");

            // DB検証: 在庫が20→17に減少
            assertThat(getInventoryQuantity(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(17);

            // DB検証: inventory_movements に1件追加
            assertThat(countInventoryMovements()).isEqualTo(1);
            var movement = jdbcTemplate.queryForMap(
                    "SELECT movement_type, quantity, quantity_after FROM inventory_movements " +
                            "WHERE reference_type = 'RETURN_SLIP' ORDER BY id DESC LIMIT 1");
            assertThat(((Number) movement.get("quantity")).intValue()).isEqualTo(-3);
            assertThat(movement.get("movement_type")).isEqualTo("RETURN_OUT");
            assertThat(((Number) movement.get("quantity_after")).intValue()).isEqualTo(17);
        }

        @Test
        @DisplayName("SC-003: 出荷返品の登録（在庫影響なし、出荷伝票番号リンク）")
        void create_outboundReturn_returns201AndNoInventoryChange() throws Exception {
            insertInventory(locA01_01_01_01, productAmb2Id, "CASE", 5, 0);

            String body = String.format("""
                    {
                      "returnType": "OUTBOUND",
                      "productId": %d,
                      "quantity": 2,
                      "unitType": "CASE",
                      "returnReason": "WRONG_DELIVERY",
                      "relatedSlipNumber": "OUT-20260315-001"
                    }""", productAmb2Id);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("slipNumber").asText()).startsWith("RTN-O-");
            assertThat(json.get("returnType").asText()).isEqualTo("OUTBOUND");
            assertThat(json.get("relatedSlipNumber").asText()).isEqualTo("OUT-20260315-001");
            assertThat(json.get("returnReason").asText()).isEqualTo("WRONG_DELIVERY");

            // DB検証: 在庫は変動なし
            assertThat(getInventoryQuantity(locA01_01_01_01, productAmb2Id, "CASE")).isEqualTo(5);

            // DB検証: inventory_movements なし
            assertThat(countInventoryMovements()).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-004: 返品理由「その他」で備考入力して正常登録")
        void create_otherReasonWithNote_returns201() throws Exception {
            String body = String.format("""
                    {
                      "returnType": "INBOUND",
                      "productId": %d,
                      "quantity": 1,
                      "unitType": "CASE",
                      "returnReason": "OTHER",
                      "returnReasonNote": "サンプル品の返却のため"
                    }""", productAmbId);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("returnReason").asText()).isEqualTo("OTHER");
            assertThat(json.get("returnReasonNote").asText()).isEqualTo("サンプル品の返却のため");

            // DB検証
            String noteInDb = jdbcTemplate.queryForObject(
                    "SELECT return_reason_note FROM return_slips WHERE return_reason = 'OTHER'",
                    String.class);
            assertThat(noteInDb).isEqualTo("サンプル品の返却のため");
        }

        @Test
        @DisplayName("SC-005: 全6種の返品理由で登録成功")
        void create_allReturnReasons_allReturn201() throws Exception {
            String[] reasons = {"QUALITY_DEFECT", "EXCESS_QUANTITY", "WRONG_DELIVERY",
                    "EXPIRED", "DAMAGED", "OTHER"};
            for (String reason : reasons) {
                String body;
                if ("OTHER".equals(reason)) {
                    body = String.format("""
                            {
                              "returnType": "INBOUND",
                              "productId": %d,
                              "quantity": 1,
                              "unitType": "CASE",
                              "returnReason": "%s",
                              "returnReasonNote": "その他の理由テスト"
                            }""", productAmbId, reason);
                } else {
                    body = createReturnRequestBody("INBOUND", productAmbId, 1, "CASE", reason);
                }

                ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

                assertThat(response.getStatusCode())
                        .as("returnReason=%s should return 201", reason)
                        .isEqualTo(HttpStatus.CREATED);
            }
            assertThat(countReturnSlips()).isEqualTo(6);
        }

        @Test
        @DisplayName("SC-007: 在庫返品で有効在庫数超過→422エラー")
        void create_inventoryReturnExceedsAvailable_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 5, 0);

            String body = createReturnRequestBodyWithLocation(
                    "INVENTORY", productAmbId, 10, "CASE", "EXCESS_QUANTITY", locA01_01_01_01);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("RETURN_INSUFFICIENT_QUANTITY");

            // DB検証: return_slips は追加されない（ロールバック）
            // NOTE: 伝票は先にINSERTされるが、deductReturnStockで例外発生→トランザクション全体がロールバック
            assertThat(countReturnSlips()).isEqualTo(0);
            assertThat(getInventoryQuantity(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(5);
        }

        @Test
        @DisplayName("SC-008: 在庫返品で引当済み在庫がある場合→422エラー")
        void create_inventoryReturnWithAllocated_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 3);

            String body = createReturnRequestBodyWithLocation(
                    "INVENTORY", productAmbId, 2, "CASE", "DAMAGED", locA01_01_01_01);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("RETURN_ALLOCATED_INVENTORY");

            assertThat(countReturnSlips()).isEqualTo(0);
            assertThat(getInventoryQuantity(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(10);
        }

        @Test
        @DisplayName("SC-009: 在庫返品で棚卸ロック中のロケーション→422エラー")
        void create_inventoryReturnStocktakeLocked_returns422() throws Exception {
            insertInventory(locA01_01_01_01, productAmbId, "CASE", 10, 0);

            // ロケーションを棚卸ロック状態にする
            jdbcTemplate.update(
                    "UPDATE locations SET is_stocktaking_locked = true WHERE id = ?", locA01_01_01_01);

            String body = createReturnRequestBodyWithLocation(
                    "INVENTORY", productAmbId, 1, "CASE", "QUALITY_DEFECT", locA01_01_01_01);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("RETURN_STOCKTAKE_LOCKED");

            assertThat(countReturnSlips()).isEqualTo(0);
            assertThat(getInventoryQuantity(locA01_01_01_01, productAmbId, "CASE")).isEqualTo(10);
        }

        @Test
        @DisplayName("SC-010: 返品理由「その他」で備考未入力→422エラー")
        void create_otherReasonWithoutNote_returns422() throws Exception {
            String body = String.format("""
                    {
                      "returnType": "INBOUND",
                      "productId": %d,
                      "quantity": 1,
                      "unitType": "CASE",
                      "returnReason": "OTHER"
                    }""", productAmbId);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("VALIDATION_ERROR");

            assertThat(countReturnSlips()).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-012: ロット管理品・期限管理品の返品登録")
        void create_lotAndExpiryManagedProduct_returns201() throws Exception {
            insertInventoryWithLot(locA01_01_01_01, productRefId, "CASE", 10, 0,
                    "LOT-2026-001", "2026-12-31");

            String body = String.format("""
                    {
                      "returnType": "INVENTORY",
                      "productId": %d,
                      "quantity": 2,
                      "unitType": "CASE",
                      "returnReason": "QUALITY_DEFECT",
                      "locationId": %d,
                      "lotNumber": "LOT-2026-001",
                      "expiryDate": "2026-12-31"
                    }""", productRefId, locA01_01_01_01);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("lotNumber").asText()).isEqualTo("LOT-2026-001");
            assertThat(json.get("expiryDate").asText()).isEqualTo("2026-12-31");

            // DB検証
            String lotInDb = jdbcTemplate.queryForObject(
                    "SELECT lot_number FROM return_slips WHERE id = ?",
                    String.class, json.get("id").asLong());
            assertThat(lotInDb).isEqualTo("LOT-2026-001");

            // 在庫が10→8に減少
            assertThat(getInventoryQuantity(locA01_01_01_01, productRefId, "CASE")).isEqualTo(8);
        }

        @Test
        @DisplayName("SC-013: 在庫返品でlocationId未指定→422エラー")
        void create_inventoryReturnWithoutLocation_returns422() throws Exception {
            String body = createReturnRequestBody("INVENTORY", productAmbId, 1, "CASE", "QUALITY_DEFECT");

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("SC-014: 存在しない商品IDで返品→404エラー")
        void create_nonExistingProduct_returns404() throws Exception {
            String body = """
                    {
                      "returnType": "INBOUND",
                      "productId": 999999,
                      "quantity": 1,
                      "unitType": "CASE",
                      "returnReason": "QUALITY_DEFECT"
                    }""";

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(countReturnSlips()).isEqualTo(0);
        }

        @Test
        @DisplayName("取引先IDを指定して返品登録できる")
        void create_withPartner_returns201() throws Exception {
            String body = String.format("""
                    {
                      "returnType": "INBOUND",
                      "productId": %d,
                      "quantity": 3,
                      "unitType": "PIECE",
                      "returnReason": "DAMAGED",
                      "partnerId": %d,
                      "relatedSlipNumber": "INB-20260320-0001"
                    }""", productAmbId, supplierPartnerId);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("partnerId").asLong()).isEqualTo(supplierPartnerId);
            assertThat(json.get("partnerCode").asText()).isEqualTo("SUP001");
        }
    }

    // ========================================================
    // GET /api/v1/returns — 返品一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/returns — 返品一覧取得")
    class GetReturns {

        private void createReturnSlips() {
            // 3種類の返品を登録
            postJson(RETURNS_URL, String.format("""
                    {
                      "returnType": "INBOUND",
                      "productId": %d,
                      "quantity": 5,
                      "unitType": "CASE",
                      "returnReason": "QUALITY_DEFECT",
                      "relatedSlipNumber": "INB-20260318-0001"
                    }""", productAmbId), adminHeaders);

            postJson(RETURNS_URL, String.format("""
                    {
                      "returnType": "OUTBOUND",
                      "productId": %d,
                      "quantity": 2,
                      "unitType": "PIECE",
                      "returnReason": "WRONG_DELIVERY",
                      "relatedSlipNumber": "OUT-20260315-001"
                    }""", productAmb2Id), adminHeaders);

            insertInventory(locA01_01_01_01, productAmbId, "CASE", 20, 0);
            postJson(RETURNS_URL, String.format("""
                    {
                      "returnType": "INVENTORY",
                      "productId": %d,
                      "quantity": 3,
                      "unitType": "CASE",
                      "returnReason": "EXCESS_QUANTITY",
                      "locationId": %d
                    }""", productAmbId, locA01_01_01_01), adminHeaders);
        }

        @Test
        @DisplayName("全件取得: 3件の返品が取得できる")
        void getAll_returns3Items() throws Exception {
            createReturnSlips();

            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId + "&page=0&size=20&sort=returnDate,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(3);
            assertThat(json.get("content")).hasSize(3);
        }

        @Test
        @DisplayName("返品種別フィルタ: INBOUNDで絞り込み")
        void filter_byReturnType_returnsFiltered() throws Exception {
            createReturnSlips();

            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId +
                            "&returnType=INBOUND&page=0&size=20&sort=returnDate,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("returnType").asText()).isEqualTo("INBOUND");
        }

        @Test
        @DisplayName("返品理由フィルタ: EXCESS_QUANTITYで絞り込み")
        void filter_byReturnReason_returnsFiltered() throws Exception {
            createReturnSlips();

            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId +
                            "&returnReason=EXCESS_QUANTITY&page=0&size=20&sort=returnDate,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("returnReason").asText())
                    .isEqualTo("EXCESS_QUANTITY");
        }

        @Test
        @DisplayName("商品IDフィルタ: AMB-002の返品のみ取得")
        void filter_byProductId_returnsFiltered() throws Exception {
            createReturnSlips();

            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId +
                            "&productId=" + productAmb2Id + "&page=0&size=20&sort=returnDate,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("productCode").asText()).isEqualTo("AMB-002");
        }

        @Test
        @DisplayName("ページネーション: size=1で取得")
        void pagination_size1_returns1PerPage() throws Exception {
            createReturnSlips();

            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId + "&page=0&size=1&sort=returnDate,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(3);
            assertThat(json.get("totalPages").asInt()).isEqualTo(3);
            assertThat(json.get("content")).hasSize(1);
        }

        @Test
        @DisplayName("存在しない倉庫IDで検索→404エラー")
        void getReturns_nonExistingWarehouse_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=999999&page=0&size=20&sort=returnDate,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("返品日範囲From > To で検索→422エラー")
        void getReturns_invalidDateRange_returns422() throws Exception {
            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId +
                            "&returnDateFrom=2026-03-20&returnDateTo=2026-03-10" +
                            "&page=0&size=20&sort=returnDate,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("返品日範囲指定で正常にフィルタされる")
        void filter_byDateRange_returnsFiltered() throws Exception {
            createReturnSlips();

            // 本日の営業日で登録されているので、本日を含む範囲で検索
            String today = java.time.LocalDate.now().toString();
            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId +
                            "&returnDateFrom=" + today + "&returnDateTo=" + today +
                            "&page=0&size=20&sort=returnDate,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        }
    }

    // ========================================================
    // ロール別アクセス制御
    // ========================================================

    @Nested
    @DisplayName("ロール別アクセス制御")
    class RoleBasedAccess {

        @Test
        @DisplayName("VIEWERロールで返品登録→403エラー")
        void create_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            String body = createReturnRequestBody("INBOUND", productAmbId, 1, "CASE", "QUALITY_DEFECT");

            ResponseEntity<String> response = postJson(RETURNS_URL, body, viewerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(countReturnSlips()).isEqualTo(0);
        }

        @Test
        @DisplayName("VIEWERロールで返品一覧取得→403エラー")
        void getReturns_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId + "&page=0&size=20&sort=returnDate,desc",
                    viewerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("未認証で返品登録→401エラー")
        void create_unauthenticated_returns401() throws Exception {
            HttpHeaders headers = createJsonHeaders();
            String body = createReturnRequestBody("INBOUND", productAmbId, 1, "CASE", "QUALITY_DEFECT");

            ResponseEntity<String> response = postJson(RETURNS_URL, body, headers);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("未認証で返品一覧取得→401エラー")
        void getReturns_unauthenticated_returns401() throws Exception {
            HttpHeaders headers = createJsonHeaders();

            ResponseEntity<String> response = get(
                    RETURNS_URL + "?warehouseId=" + warehouseId + "&page=0&size=20&sort=returnDate,desc",
                    headers);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ========================================================
    // SC-013: 必須項目バリデーション
    // ========================================================

    @Nested
    @DisplayName("必須項目バリデーション")
    class RequiredFieldValidation {

        @Test
        @DisplayName("SC-013: productIdが未指定→400エラー")
        void create_missingProductId_returns400() throws Exception {
            String body = """
                    {
                      "returnType": "INBOUND",
                      "quantity": 1,
                      "unitType": "CASE",
                      "returnReason": "QUALITY_DEFECT"
                    }""";

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("SC-013: quantityが未指定→400エラー")
        void create_missingQuantity_returns400() throws Exception {
            String body = String.format("""
                    {
                      "returnType": "INBOUND",
                      "productId": %d,
                      "unitType": "CASE",
                      "returnReason": "QUALITY_DEFECT"
                    }""", productAmbId);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("SC-013: unitTypeが未指定→400エラー")
        void create_missingUnitType_returns400() throws Exception {
            String body = String.format("""
                    {
                      "returnType": "INBOUND",
                      "productId": %d,
                      "quantity": 1,
                      "returnReason": "QUALITY_DEFECT"
                    }""", productAmbId);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("SC-013: returnReasonが未指定→400エラー")
        void create_missingReturnReason_returns400() throws Exception {
            String body = String.format("""
                    {
                      "returnType": "INBOUND",
                      "productId": %d,
                      "quantity": 1,
                      "unitType": "CASE"
                    }""", productAmbId);

            ResponseEntity<String> response = postJson(RETURNS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
