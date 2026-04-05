package com.wms.allocation.controller;

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
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("結合テスト: 在庫引当")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AllocationIntegrationTest extends IntegrationTestBase {

    private static final String ALLOCATION_ORDERS_URL = "/api/v1/allocation/orders";
    private static final String ALLOCATION_EXECUTE_URL = "/api/v1/allocation/execute";
    private static final String ALLOCATION_RELEASE_URL = "/api/v1/allocation/release";
    private static final String ALLOCATED_ORDERS_URL = "/api/v1/allocation/allocated-orders";
    private static final String UNPACK_INSTRUCTIONS_URL = "/api/v1/allocation/unpack-instructions";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";

    private HttpHeaders adminHeaders;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long partnerId;
    private String partnerCode;
    private String partnerName;
    private Long adminUserId;

    // テスト用ロケーション ID
    private Long locA01_01_01_01;  // A-01-A01-01-01-01
    private Long locA01_01_01_02;  // A-01-A01-01-01-02
    private Long locA01_02_01_01;  // A-01-A01-02-01-01

    // テスト用商品 ID
    private Long productAmbId;     // AMB-001 (常温, case_qty=6, ball_qty=4, ロットなし, 期限なし)
    private Long productAmb2Id;    // AMB-002 (常温, case_qty=6, ball_qty=4, ロットなし, 期限なし)
    private Long productRefId;     // REF-001 (冷蔵, case_qty=3, ball_qty=4, ロットあり, 期限あり)

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        warehouseCode = "WH001";
        warehouseName = jdbcTemplate.queryForObject(
                "SELECT warehouse_name FROM warehouses WHERE warehouse_code = 'WH001'", String.class);

        partnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'SUP001'", Long.class);
        partnerCode = "SUP001";
        partnerName = jdbcTemplate.queryForObject(
                "SELECT partner_name FROM partners WHERE partner_code = 'SUP001'", String.class);

        adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);

        locA01_01_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);
        locA01_01_01_02 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-02' AND warehouse_id = ?",
                Long.class, warehouseId);
        locA01_02_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-02-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);

        productAmbId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);
        productAmb2Id = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
        productRefId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'REF-001'", Long.class);
    }

    @BeforeAll
    void initAuth() {
        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    @BeforeEach
    void setUp() {
        // テスト間の独立性のためにデータクリーンアップ（依存順序に注意）
        jdbcTemplate.update("DELETE FROM inventory_movements");
        jdbcTemplate.update("DELETE FROM allocation_details");
        jdbcTemplate.update("DELETE FROM unpack_instructions");
        jdbcTemplate.update("DELETE FROM picking_instruction_lines");
        jdbcTemplate.update("DELETE FROM picking_instructions");
        jdbcTemplate.update("DELETE FROM outbound_slip_lines");
        jdbcTemplate.update("DELETE FROM outbound_slips");
        jdbcTemplate.update("DELETE FROM inventories");
    }

    // ========== ヘルパーメソッド ==========

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> put(String url, String body, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    /**
     * 出荷伝票を直接DBに挿入する。
     * @return 挿入された出荷伝票のID
     */
    private Long insertOutboundSlip(String slipNumber, String status, LocalDate plannedDate) {
        jdbcTemplate.update(
                "INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name, " +
                        "partner_id, partner_code, partner_name, planned_date, status, " +
                        "created_at, created_by, updated_at, updated_by, version) " +
                        "VALUES (?, 'NORMAL', ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now(), ?, 0)",
                slipNumber, warehouseId, warehouseCode, warehouseName,
                partnerId, partnerCode, partnerName, plannedDate, status,
                adminUserId, adminUserId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slips WHERE slip_number = ?", Long.class, slipNumber);
    }

    /**
     * 出荷伝票明細を直接DBに挿入する。
     * @return 挿入された明細のID
     */
    private Long insertOutboundSlipLine(Long slipId, int lineNo, Long productId,
                                         String productCode, String productName,
                                         String unitType, int orderedQty, String lineStatus) {
        jdbcTemplate.update(
                "INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code, product_name, " +
                        "unit_type, ordered_qty, shipped_qty, line_status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, now(), now())",
                slipId, lineNo, productId, productCode, productName,
                unitType, orderedQty, lineStatus);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = ?",
                Long.class, slipId, lineNo);
    }

    /**
     * 在庫を直接DBに挿入する。
     */
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

    private int getAllocatedQty(Long locationId, Long productId, String unitType) {
        return jdbcTemplate.queryForObject(
                "SELECT allocated_qty FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = ?",
                Integer.class, locationId, productId, unitType);
    }

    private int getInventoryQty(Long locationId, Long productId, String unitType) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = ?",
                Integer.class, locationId, productId, unitType);
    }

    private String getSlipStatus(Long slipId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbound_slips WHERE id = ?", String.class, slipId);
    }

    private int countAllocationDetails(Long slipId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM allocation_details WHERE outbound_slip_id = ?",
                Integer.class, slipId);
    }

    private int sumAllocatedQtyForSlip(Long slipId) {
        Integer sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(allocated_qty), 0) FROM allocation_details WHERE outbound_slip_id = ?",
                Integer.class, slipId);
        return sum != null ? sum : 0;
    }

    private int countUnpackInstructions(Long slipId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM unpack_instructions WHERE outbound_slip_id = ?",
                Integer.class, slipId);
    }

    private int countInventoryMovements(String movementType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE movement_type = ?",
                Integer.class, movementType);
    }

    // ========================================================
    // SC-001: 正常系: FIFO順で全量引当が成功する
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/allocation/execute — 引当実行")
    class ExecuteAllocation {

        @Test
        @DisplayName("SC-001: FIFO順で全量引当が成功する")
        void execute_fifoFullAllocation_returns200() throws Exception {
            // Arrange: 受注10個(PIECE)、在庫はロケーションA(入庫日3/10, 5個)とロケーションB(入庫日3/12, 8個)
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-001", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");

            // ロケーションA: 入庫日が早い → FIFO順で先に引当される
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 5, 0);
            // ロケーションB: 入庫日が遅い
            insertInventory(locA01_01_01_02, productAmbId, "PIECE", 8, 0);

            // Act
            String body = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> response = postJson(ALLOCATION_EXECUTE_URL, body, adminHeaders);

            // Assert: レスポンス
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("allocatedCount").asInt()).isEqualTo(1);
            assertThat(json.get("allocatedSlips")).hasSize(1);
            assertThat(json.get("allocatedSlips").get(0).get("status").asText()).isEqualTo("ALLOCATED");
            assertThat(json.get("unpackInstructions")).isEmpty();
            assertThat(json.get("unallocatedLines")).isEmpty();

            // Assert: DB — 伝票ステータス
            assertThat(getSlipStatus(slipId)).isEqualTo("ALLOCATED");

            // Assert: DB — 引当明細が2件作成
            assertThat(countAllocationDetails(slipId)).isEqualTo(2);
            assertThat(sumAllocatedQtyForSlip(slipId)).isEqualTo(10);

            // Assert: DB — 在庫のallocated_qty
            assertThat(getAllocatedQty(locA01_01_01_01, productAmbId, "PIECE")).isEqualTo(5);
            assertThat(getAllocatedQty(locA01_01_01_02, productAmbId, "PIECE")).isEqualTo(5);
        }

        @Test
        @DisplayName("SC-002: 部分引当（在庫不足時に引当可能分のみ引当）")
        void execute_partialAllocation_returns200() throws Exception {
            // Arrange: 受注20個、在庫合計12個
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-002", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmb2Id, "AMB-002", "緑茶ペットボトル 500ml",
                    "PIECE", 20, "ORDERED");

            insertInventory(locA01_01_01_01, productAmb2Id, "PIECE", 12, 0);

            // Act
            String body = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> response = postJson(ALLOCATION_EXECUTE_URL, body, adminHeaders);

            // Assert: レスポンス
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("allocatedSlips")).hasSize(1);
            assertThat(json.get("allocatedSlips").get(0).get("status").asText()).isEqualTo("PARTIAL_ALLOCATED");
            assertThat(json.get("unallocatedLines")).hasSize(1);
            assertThat(json.get("unallocatedLines").get(0).get("shortageQty").asInt()).isEqualTo(8);

            // Assert: DB
            assertThat(getSlipStatus(slipId)).isEqualTo("PARTIAL_ALLOCATED");
            assertThat(sumAllocatedQtyForSlip(slipId)).isEqualTo(12);
            assertThat(getAllocatedQty(locA01_01_01_01, productAmb2Id, "PIECE")).isEqualTo(12);
        }

        @Test
        @DisplayName("SC-003: FEFO引当（賞味期限管理品は期限短い順）")
        void execute_fefoAllocation_returns200() throws Exception {
            // Arrange: 受注8個(PIECE)、3ロケーションに期限違いの在庫
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-003", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productRefId, "REF-001", "牛乳 1L",
                    "PIECE", 8, "ORDERED");

            // ロケーションA: 期限2026-04-30, 5個
            insertInventory(locA01_01_01_01, productRefId, "PIECE", 5, 0,
                    "LOT-A", LocalDate.of(2026, 4, 30));
            // ロケーションB: 期限2026-06-30, 10個
            insertInventory(locA01_01_01_02, productRefId, "PIECE", 10, 0,
                    "LOT-B", LocalDate.of(2026, 6, 30));
            // ロケーションC: 期限2026-03-31, 3個（最短期限→最優先）
            insertInventory(locA01_02_01_01, productRefId, "PIECE", 3, 0,
                    "LOT-C", LocalDate.of(2026, 3, 31));

            // Act
            String body = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> response = postJson(ALLOCATION_EXECUTE_URL, body, adminHeaders);

            // Assert: レスポンス
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("allocatedCount").asInt()).isEqualTo(1);
            assertThat(json.get("allocatedSlips").get(0).get("status").asText()).isEqualTo("ALLOCATED");

            // Assert: DB — FEFO順で引当（期限短い順: C→A→B）
            assertThat(getSlipStatus(slipId)).isEqualTo("ALLOCATED");
            assertThat(sumAllocatedQtyForSlip(slipId)).isEqualTo(8);
            // ロケーションC(期限3/31): 全3個引当
            assertThat(getAllocatedQty(locA01_02_01_01, productRefId, "PIECE")).isEqualTo(3);
            // ロケーションA(期限4/30): 5個引当
            assertThat(getAllocatedQty(locA01_01_01_01, productRefId, "PIECE")).isEqualTo(5);
            // ロケーションB(期限6/30): 引当なし
            assertThat(getAllocatedQty(locA01_01_01_02, productRefId, "PIECE")).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-004: ばらし指示自動生成（ボール→バラ）")
        void execute_unpackBallToPiece_returns200() throws Exception {
            // Arrange: 受注10個(PIECE)、バラ在庫5、ボール在庫2（ボール入数=4 → ボール2=バラ8）
            // AMB-001: case_qty=6, ball_qty=4
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-004", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");

            // バラ在庫: 5個
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 5, 0);
            // ボール在庫: 2個（2×4=8バラ分）
            insertInventory(locA01_01_01_02, productAmbId, "BALL", 2, 0);

            // Act
            String body = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> response = postJson(ALLOCATION_EXECUTE_URL, body, adminHeaders);

            // Assert: レスポンス
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("allocatedCount").asInt()).isEqualTo(1);
            assertThat(json.get("allocatedSlips").get(0).get("status").asText()).isEqualTo("ALLOCATED");

            // Assert: ばらし指示が1件生成
            JsonNode unpackInstructions = json.get("unpackInstructions");
            assertThat(unpackInstructions).hasSize(1);
            assertThat(unpackInstructions.get(0).get("fromUnitType").asText()).isEqualTo("BALL");
            assertThat(unpackInstructions.get(0).get("toUnitType").asText()).isEqualTo("PIECE");
            assertThat(unpackInstructions.get(0).get("status").asText()).isEqualTo("INSTRUCTED");

            // Assert: DB — バラ在庫のallocated_qty
            assertThat(getAllocatedQty(locA01_01_01_01, productAmbId, "PIECE")).isEqualTo(5);
            // Assert: DB — ボール在庫のallocated_qty（仮確保）
            int ballAllocated = getAllocatedQty(locA01_01_01_02, productAmbId, "BALL");
            assertThat(ballAllocated).isGreaterThan(0);
            // Assert: DB — ばらし指示レコード
            assertThat(countUnpackInstructions(slipId)).isEqualTo(1);
        }

        @Test
        @DisplayName("SC-004a: ばらし指示自動生成（ケース→バラ）")
        void execute_unpackCaseToPiece_returns200() throws Exception {
            // Arrange: 受注50個(PIECE)、バラ在庫10、ボール在庫0、ケース在庫3
            // AMB-001: case_qty=6(ケース入数), ball_qty=4(ボール入数)
            // ケース→PIECEの変換率 = case_quantity = 6
            // ただしケースからピースの変換はcaseQuantity=6を使う
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-004A", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 50, "ORDERED");

            // バラ在庫: 10個
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 10, 0);
            // ケース在庫: 3個（3×6=18バラ分） ※ バラ+ケース=10+18=28 < 50 → 部分引当
            insertInventory(locA01_01_01_02, productAmbId, "CASE", 3, 0);

            // Act
            String body = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> response = postJson(ALLOCATION_EXECUTE_URL, body, adminHeaders);

            // Assert: レスポンス
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());

            // ばらし指示（ケース→バラ）
            JsonNode unpackInstructions = json.get("unpackInstructions");
            assertThat(unpackInstructions).hasSize(1);
            assertThat(unpackInstructions.get(0).get("fromUnitType").asText()).isEqualTo("CASE");
            assertThat(unpackInstructions.get(0).get("toUnitType").asText()).isEqualTo("PIECE");

            // Assert: DB — バラ在庫のallocated_qty
            assertThat(getAllocatedQty(locA01_01_01_01, productAmbId, "PIECE")).isEqualTo(10);
            // Assert: DB — ケース在庫のallocated_qty（仮確保）
            assertThat(getAllocatedQty(locA01_01_01_02, productAmbId, "CASE")).isGreaterThan(0);
            // Assert: DB — ばらし指示レコード
            assertThat(countUnpackInstructions(slipId)).isEqualTo(1);
        }

        @Test
        @DisplayName("SC-009: 異常系 — 受注未選択（空リスト）で引当実行すると400エラー")
        void execute_emptySlipIds_returns400() throws Exception {
            String body = "{\"outboundSlipIds\":[]}";
            ResponseEntity<String> response = postJson(ALLOCATION_EXECUTE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("SC-012: 複数受注の一括引当（全量+部分混在）")
        void execute_bulkAllocation_mixedResult_returns200() throws Exception {
            // Arrange: 受注A(5個, AMB-001)、受注B(20個, AMB-002)
            // AMB-001の在庫: 10個（全量OK）、AMB-002の在庫: 8個（部分引当）
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipIdA = insertOutboundSlip("OUT-TEST-012A", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipIdA, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 5, "ORDERED");

            Long slipIdB = insertOutboundSlip("OUT-TEST-012B", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipIdB, 1, productAmb2Id, "AMB-002", "緑茶ペットボトル 500ml",
                    "PIECE", 20, "ORDERED");

            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 10, 0);
            insertInventory(locA01_01_01_02, productAmb2Id, "PIECE", 8, 0);

            // Act
            String body = String.format("{\"outboundSlipIds\":[%d,%d]}", slipIdA, slipIdB);
            ResponseEntity<String> response = postJson(ALLOCATION_EXECUTE_URL, body, adminHeaders);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("allocatedCount").asInt()).isEqualTo(1); // 全量引当成功は1件
            assertThat(json.get("allocatedSlips")).hasSize(2);

            // Assert: DB — 受注A: ALLOCATED, 受注B: PARTIAL_ALLOCATED
            assertThat(getSlipStatus(slipIdA)).isEqualTo("ALLOCATED");
            assertThat(getSlipStatus(slipIdB)).isEqualTo("PARTIAL_ALLOCATED");
            assertThat(sumAllocatedQtyForSlip(slipIdA)).isEqualTo(5);
            assertThat(sumAllocatedQtyForSlip(slipIdB)).isEqualTo(8);
        }
    }

    // ========================================================
    // SC-005: ばらし完了
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/allocation/unpack-instructions/{id}/complete — ばらし完了")
    class CompleteUnpackInstruction {

        @Test
        @DisplayName("SC-005: ばらし完了 — 在庫変動が正しく記録される")
        void complete_unpack_returns200() throws Exception {
            // Arrange: 引当実行してばらし指示を生成
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-005", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");

            // バラ在庫5、ボール在庫2（ball_qty=4 → 2×4=8バラ分）
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 5, 0);
            insertInventory(locA01_01_01_02, productAmbId, "BALL", 2, 0);

            // 引当実行
            String execBody = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> execResponse = postJson(ALLOCATION_EXECUTE_URL, execBody, adminHeaders);
            assertThat(execResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode execJson = parseJson(execResponse.getBody());

            // ばらし指示IDを取得
            Long unpackId = execJson.get("unpackInstructions").get(0).get("id").asLong();

            // ばらし前のボール在庫を記録
            int ballQtyBefore = getInventoryQty(locA01_01_01_02, productAmbId, "BALL");
            int ballAllocBefore = getAllocatedQty(locA01_01_01_02, productAmbId, "BALL");

            // Act: ばらし完了
            String completeUrl = UNPACK_INSTRUCTIONS_URL + "/" + unpackId + "/complete";
            ResponseEntity<String> response = put(completeUrl, null, adminHeaders);

            // Assert: レスポンス
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(json.has("completedAt")).isTrue();

            // Assert: 在庫移動（BREAKDOWN_OUT, BREAKDOWN_IN）
            JsonNode movements = json.get("inventoryMovements");
            assertThat(movements).hasSize(2);

            // Assert: DB — ばらし指示ステータス
            String unpackStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM unpack_instructions WHERE id = ?", String.class, unpackId);
            assertThat(unpackStatus).isEqualTo("COMPLETED");

            // Assert: DB — inventory_movements
            assertThat(countInventoryMovements("BREAKDOWN_OUT")).isGreaterThanOrEqualTo(1);
            assertThat(countInventoryMovements("BREAKDOWN_IN")).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("SC-011: 異常系 — 完了済みばらし指示の再完了は409エラー")
        void complete_alreadyCompleted_returns409() throws Exception {
            // Arrange: 引当実行→ばらし完了
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-011", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");

            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 5, 0);
            insertInventory(locA01_01_01_02, productAmbId, "BALL", 2, 0);

            String execBody = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> execResponse = postJson(ALLOCATION_EXECUTE_URL, execBody, adminHeaders);
            JsonNode execJson = parseJson(execResponse.getBody());
            Long unpackId = execJson.get("unpackInstructions").get(0).get("id").asLong();

            // 1回目: ばらし完了
            String completeUrl = UNPACK_INSTRUCTIONS_URL + "/" + unpackId + "/complete";
            ResponseEntity<String> firstComplete = put(completeUrl, null, adminHeaders);
            assertThat(firstComplete.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 在庫移動数を記録
            int movementsBefore = countInventoryMovements("BREAKDOWN_OUT");

            // Act: 2回目の完了 → エラー
            ResponseEntity<String> response = put(completeUrl, null, adminHeaders);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("ALREADY_COMPLETED");

            // Assert: DB — 在庫変動がないこと
            assertThat(countInventoryMovements("BREAKDOWN_OUT")).isEqualTo(movementsBefore);
        }
    }

    // ========================================================
    // SC-006, SC-007: 引当済み一覧・引当解放
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/allocation/allocated-orders — 引当済み一覧")
    class AllocatedOrders {

        @Test
        @DisplayName("SC-006: 引当済み一覧表示 — ALLOCATED/PARTIAL_ALLOCATEDの受注が表示される")
        void getAllocatedOrders_returns200() throws Exception {
            // Arrange: 3件の受注を引当（2件全量、1件部分）
            LocalDate plannedDate = LocalDate.now().plusDays(1);

            // 全量引当1
            Long slipId1 = insertOutboundSlip("OUT-TEST-006A", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId1, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 5, "ORDERED");
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 20, 0);

            // 全量引当2
            Long slipId2 = insertOutboundSlip("OUT-TEST-006B", "ORDERED", plannedDate.plusDays(1));
            insertOutboundSlipLine(slipId2, 1, productAmb2Id, "AMB-002", "緑茶ペットボトル 500ml",
                    "PIECE", 3, "ORDERED");
            insertInventory(locA01_01_01_02, productAmb2Id, "PIECE", 10, 0);

            // 部分引当
            Long slipId3 = insertOutboundSlip("OUT-TEST-006C", "ORDERED", plannedDate.plusDays(2));
            insertOutboundSlipLine(slipId3, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 100, "ORDERED");

            // 引当実行（3件同時）
            String execBody = String.format("{\"outboundSlipIds\":[%d,%d,%d]}", slipId1, slipId2, slipId3);
            postJson(ALLOCATION_EXECUTE_URL, execBody, adminHeaders);

            // Act
            ResponseEntity<String> response = get(ALLOCATED_ORDERS_URL + "?page=0&size=20&sort=plannedDate,asc", adminHeaders);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(3);

            JsonNode content = json.get("content");
            assertThat(content).hasSize(3);
            // 出荷予定日昇順
            assertThat(content.get(0).get("slipNumber").asText()).isEqualTo("OUT-TEST-006A");
            assertThat(content.get(1).get("slipNumber").asText()).isEqualTo("OUT-TEST-006B");
            assertThat(content.get(2).get("slipNumber").asText()).isEqualTo("OUT-TEST-006C");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/allocation/release — 引当解放")
    class ReleaseAllocation {

        @Test
        @DisplayName("SC-007: 引当解放 — 在庫が復元される")
        void release_allocatedOrder_returns200() throws Exception {
            // Arrange: 引当実行（2ロケーションから合計15個引当）
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-007", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 15, "ORDERED");

            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 10, 0);
            insertInventory(locA01_01_01_02, productAmbId, "PIECE", 8, 0);

            String execBody = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            postJson(ALLOCATION_EXECUTE_URL, execBody, adminHeaders);

            // 引当後の確認
            assertThat(getSlipStatus(slipId)).isEqualTo("ALLOCATED");
            assertThat(sumAllocatedQtyForSlip(slipId)).isEqualTo(15);

            // Act: 引当解放
            String releaseBody = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> response = postJson(ALLOCATION_RELEASE_URL, releaseBody, adminHeaders);

            // Assert: レスポンス
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("releasedCount").asInt()).isEqualTo(1);
            assertThat(json.get("releasedSlips").get(0).get("previousStatus").asText()).isEqualTo("ALLOCATED");
            assertThat(json.get("releasedSlips").get(0).get("newStatus").asText()).isEqualTo("ORDERED");

            // Assert: DB — 伝票ステータスがORDEREDに戻る
            assertThat(getSlipStatus(slipId)).isEqualTo("ORDERED");

            // Assert: DB — allocation_detailsが全削除
            assertThat(countAllocationDetails(slipId)).isEqualTo(0);

            // Assert: DB — 在庫のallocated_qtyが0に戻る
            assertThat(getAllocatedQty(locA01_01_01_01, productAmbId, "PIECE")).isEqualTo(0);
            assertThat(getAllocatedQty(locA01_01_01_02, productAmbId, "PIECE")).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-010: 異常系 — ピッキング指示済み受注の引当解放拒否（409）")
        void release_pickingCompleted_returns409() throws Exception {
            // Arrange: PICKING_COMPLETED ステータスの受注をDBに直接作成
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-010", "PICKING_COMPLETED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ALLOCATED");

            // 引当明細もダミー挿入（解放拒否のテストなので削除されないはず）
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 10, 10);
            Long inventoryId = jdbcTemplate.queryForObject(
                    "SELECT id FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = 'PIECE'",
                    Long.class, locA01_01_01_01, productAmbId);
            Long lineId = jdbcTemplate.queryForObject(
                    "SELECT id FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = 1",
                    Long.class, slipId);
            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, inventory_id, " +
                            "location_id, product_id, unit_type, allocated_qty, warehouse_id, created_at, created_by) " +
                            "VALUES (?, ?, ?, ?, ?, 'PIECE', 10, ?, now(), ?)",
                    slipId, lineId, inventoryId, locA01_01_01_01, productAmbId, warehouseId, adminUserId);

            // Act
            String releaseBody = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> response = postJson(ALLOCATION_RELEASE_URL, releaseBody, adminHeaders);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("RELEASE_NOT_ALLOWED");

            // Assert: DB — ステータス変更なし
            assertThat(getSlipStatus(slipId)).isEqualTo("PICKING_COMPLETED");
            assertThat(countAllocationDetails(slipId)).isEqualTo(1);
            assertThat(getAllocatedQty(locA01_01_01_01, productAmbId, "PIECE")).isEqualTo(10);
        }
    }

    // ========================================================
    // SC-013: ロール別アクセス制御
    // ========================================================

    @Nested
    @DisplayName("ロール別アクセス制御")
    class AccessControl {

        @Test
        @DisplayName("SC-013: WAREHOUSE_STAFFでの引当実行は403エラー")
        void execute_withStaffRole_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            String body = "{\"outboundSlipIds\":[1]}";
            ResponseEntity<String> response = postJson(ALLOCATION_EXECUTE_URL, body, staffHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("SC-013: WAREHOUSE_STAFFでの引当対象受注一覧取得は403エラー")
        void getOrders_withStaffRole_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            ResponseEntity<String> response = get(
                    ALLOCATION_ORDERS_URL + "?warehouseId=" + warehouseId + "&page=0&size=20&sort=plannedDate,asc",
                    staffHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("SC-013: WAREHOUSE_STAFFでの引当解放は403エラー")
        void release_withStaffRole_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            String body = "{\"outboundSlipIds\":[1]}";
            ResponseEntity<String> response = postJson(ALLOCATION_RELEASE_URL, body, staffHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFでのばらし完了は許可される（200系）")
        void completeUnpack_withStaffRole_allowed() throws Exception {
            // STAFFはばらし完了のみ許可（SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF）
            // ばらし指示をまず作成
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-013S", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");

            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 5, 0);
            insertInventory(locA01_01_01_02, productAmbId, "BALL", 2, 0);

            // admin で引当実行
            String execBody = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> execResponse = postJson(ALLOCATION_EXECUTE_URL, execBody, adminHeaders);
            JsonNode execJson = parseJson(execResponse.getBody());
            Long unpackId = execJson.get("unpackInstructions").get(0).get("id").asLong();

            // Act: staff でばらし完了
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String completeUrl = UNPACK_INSTRUCTIONS_URL + "/" + unpackId + "/complete";
            ResponseEntity<String> response = put(completeUrl, null, staffHeaders);

            // Assert: 許可される
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ========================================================
    // 引当対象受注一覧
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/allocation/orders — 引当対象受注一覧")
    class GetAllocationOrders {

        @Test
        @DisplayName("引当対象受注一覧 — ORDERED/PARTIAL_ALLOCATEDのみ表示")
        void getOrders_filtersCorrectly_returns200() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);

            // ORDERED (表示対象)
            insertOutboundSlip("OUT-ORD-001", "ORDERED", plannedDate);
            // PARTIAL_ALLOCATED (表示対象)
            insertOutboundSlip("OUT-PAR-001", "PARTIAL_ALLOCATED", plannedDate);
            // ALLOCATED (表示対象外)
            insertOutboundSlip("OUT-ALL-001", "ALLOCATED", plannedDate);
            // SHIPPED (表示対象外)
            insertOutboundSlip("OUT-SHP-001", "SHIPPED", plannedDate);

            // Act
            ResponseEntity<String> response = get(
                    ALLOCATION_ORDERS_URL + "?warehouseId=" + warehouseId + "&page=0&size=20&sort=plannedDate,asc",
                    adminHeaders);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(2);

            JsonNode content = json.get("content");
            for (int i = 0; i < content.size(); i++) {
                String status = content.get(i).get("status").asText();
                assertThat(status).isIn("ORDERED", "PARTIAL_ALLOCATED");
            }
        }

        @Test
        @DisplayName("引当対象受注一覧 — 出荷予定日で絞り込み")
        void getOrders_filterByDate_returns200() throws Exception {
            LocalDate baseDate = LocalDate.now().plusDays(5);

            insertOutboundSlip("OUT-D-001", "ORDERED", baseDate);
            insertOutboundSlip("OUT-D-002", "ORDERED", baseDate.plusDays(3));
            insertOutboundSlip("OUT-D-003", "ORDERED", baseDate.plusDays(10));

            // Act: baseDate〜baseDate+5の範囲
            String url = String.format("%s?warehouseId=%d&shippingDateFrom=%s&shippingDateTo=%s&page=0&size=20&sort=plannedDate,asc",
                    ALLOCATION_ORDERS_URL, warehouseId,
                    baseDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    baseDate.plusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE));
            ResponseEntity<String> response = get(url, adminHeaders);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(2);
        }
    }

    // ========================================================
    // ばらし指示一覧
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/allocation/unpack-instructions — ばらし指示一覧")
    class GetUnpackInstructions {

        @Test
        @DisplayName("ばらし指示一覧 — 引当実行後にINSTRUCTED状態で表示される")
        void getUnpackInstructions_afterAllocation_returns200() throws Exception {
            // Arrange: ばらし指示が生成される引当を実行
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-UNP", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");

            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 5, 0);
            insertInventory(locA01_01_01_02, productAmbId, "BALL", 2, 0);

            String execBody = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            postJson(ALLOCATION_EXECUTE_URL, execBody, adminHeaders);

            // Act
            ResponseEntity<String> response = get(
                    UNPACK_INSTRUCTIONS_URL + "?outboundSlipId=" + slipId + "&page=0&size=20",
                    adminHeaders);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);

            JsonNode content = json.get("content");
            assertThat(content.get(0).get("status").asText()).isEqualTo("INSTRUCTED");
            assertThat(content.get(0).get("fromUnitType").asText()).isEqualTo("BALL");
            assertThat(content.get(0).get("toUnitType").asText()).isEqualTo("PIECE");
        }
    }
}
