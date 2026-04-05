package com.wms.outbound.controller;

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

@DisplayName("結合テスト: 出荷管理")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboundIntegrationTest extends IntegrationTestBase {

    private static final String SLIPS_URL = "/api/v1/outbound/slips";
    private static final String PICKING_URL = "/api/v1/outbound/picking";
    private static final String ALLOCATION_EXECUTE_URL = "/api/v1/allocation/execute";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private HttpHeaders adminHeaders;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long customerPartnerId;
    private String customerPartnerCode;
    private Long supplierPartnerId;
    private Long adminUserId;

    // テスト用ロケーション ID
    private Long locA01_01_01_01;
    private Long locA01_01_01_02;

    // テスト用商品 ID
    private Long productAmbId;     // AMB-001 (常温, ロットなし, 期限なし)
    private Long productAmb2Id;    // AMB-002
    private Long productRefId;     // REF-001 (冷蔵, ロットあり, 期限あり)

    // テスト用エリア ID
    private Long areaA01Id;

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        warehouseCode = "WH001";
        warehouseName = jdbcTemplate.queryForObject(
                "SELECT warehouse_name FROM warehouses WHERE warehouse_code = 'WH001'", String.class);

        customerPartnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'CUS001'", Long.class);
        customerPartnerCode = "CUS001";

        supplierPartnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'SUP001'", Long.class);

        adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);

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
        productRefId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'REF-001'", Long.class);

        areaA01Id = jdbcTemplate.queryForObject(
                "SELECT a.id FROM areas a JOIN buildings b ON a.building_id = b.id " +
                        "WHERE b.warehouse_id = ? AND a.area_code = 'A01'",
                Long.class, warehouseId);
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

    private ResponseEntity<String> delete(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    private Long insertOutboundSlip(String slipNumber, String status, LocalDate plannedDate) {
        jdbcTemplate.update(
                "INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name, " +
                        "partner_id, partner_code, partner_name, planned_date, status, " +
                        "created_at, created_by, updated_at, updated_by, version) " +
                        "VALUES (?, 'NORMAL', ?, ?, ?, ?, ?, '株式会社テスト商事', ?, ?, now(), ?, now(), ?, 0)",
                slipNumber, warehouseId, warehouseCode, warehouseName,
                customerPartnerId, customerPartnerCode, plannedDate, status,
                adminUserId, adminUserId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slips WHERE slip_number = ?", Long.class, slipNumber);
    }

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

    private String getSlipStatus(Long slipId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbound_slips WHERE id = ?", String.class, slipId);
    }

    private String getLineStatus(Long slipId, int lineNo) {
        return jdbcTemplate.queryForObject(
                "SELECT line_status FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = ?",
                String.class, slipId, lineNo);
    }

    private int countSlips() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbound_slips", Integer.class);
    }

    private int countAllocationDetails(Long slipId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM allocation_details WHERE outbound_slip_id = ?",
                Integer.class, slipId);
    }

    private int getAllocatedQty(Long locationId, Long productId, String unitType) {
        return jdbcTemplate.queryForObject(
                "SELECT allocated_qty FROM inventories WHERE location_id = ? AND product_id = ? AND unit_type = ?",
                Integer.class, locationId, productId, unitType);
    }

    private String createSlipRequestBody(LocalDate plannedDate, Long productId1, int qty1,
                                          Long productId2, int qty2) {
        return String.format("""
                {
                  "warehouseId": %d,
                  "slipType": "NORMAL",
                  "partnerId": %d,
                  "plannedDate": "%s",
                  "lines": [
                    {"productId": %d, "unitType": "PIECE", "orderedQty": %d},
                    {"productId": %d, "unitType": "PIECE", "orderedQty": %d}
                  ]
                }""", warehouseId, customerPartnerId, plannedDate,
                productId1, qty1, productId2, qty2);
    }

    // ========================================================
    // 出荷伝票: 作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/outbound/slips — 出荷伝票作成")
    class CreateOutboundSlip {

        @Test
        @DisplayName("SC-OUT-001: 通常出荷の受注登録が明細複数行で成功する")
        void create_normalSlipWithMultipleLines_returns201() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            String body = createSlipRequestBody(plannedDate, productAmbId, 10, productAmb2Id, 20);

            ResponseEntity<String> response = postJson(SLIPS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("slipNumber").asText()).startsWith("OUT-");
            assertThat(json.get("status").asText()).isEqualTo("ORDERED");
            assertThat(json.get("slipType").asText()).isEqualTo("NORMAL");
            assertThat(json.get("warehouseId").asLong()).isEqualTo(warehouseId);
            assertThat(json.get("partnerId").asLong()).isEqualTo(customerPartnerId);
            assertThat(json.get("lines")).hasSize(2);
            assertThat(json.get("lines").get(0).get("orderedQty").asInt()).isEqualTo(10);
            assertThat(json.get("lines").get(1).get("orderedQty").asInt()).isEqualTo(20);

            // DB検証
            assertThat(countSlips()).isEqualTo(1);
            Long slipId = json.get("id").asLong();
            assertThat(getSlipStatus(slipId)).isEqualTo("ORDERED");
        }

        @Test
        @DisplayName("SC-OUT-003: 出荷予定日が過去日の場合422エラー")
        void create_pastPlannedDate_returns422() throws Exception {
            LocalDate pastDate = LocalDate.now().minusDays(1);
            String body = createSlipRequestBody(pastDate, productAmbId, 10, productAmb2Id, 20);

            ResponseEntity<String> response = postJson(SLIPS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("PLANNED_DATE_TOO_EARLY");
            assertThat(countSlips()).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-OUT-003: 明細が空の場合400バリデーションエラー")
        void create_emptyLines_returns400() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            String body = String.format("""
                    {
                      "warehouseId": %d,
                      "slipType": "NORMAL",
                      "partnerId": %d,
                      "plannedDate": "%s",
                      "lines": []
                    }""", warehouseId, customerPartnerId, plannedDate);

            ResponseEntity<String> response = postJson(SLIPS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(countSlips()).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-OUT-004: 出荷禁止商品を含む場合422エラー")
        void create_shipmentStoppedProduct_returns422() throws Exception {
            // 出荷禁止商品を作成
            jdbcTemplate.update(
                    "INSERT INTO products (product_code, product_name, case_quantity, ball_quantity, " +
                            "storage_condition, shipment_stop_flag, lot_manage_flag, expiry_manage_flag, " +
                            "is_active, created_by, updated_by, created_at, updated_at) " +
                            "VALUES ('PRD-STOP', '出荷禁止テスト商品', 6, 4, 'AMBIENT', true, false, false, " +
                            "true, 1, 1, now(), now())");
            Long stoppedProductId = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'PRD-STOP'", Long.class);

            LocalDate plannedDate = LocalDate.now().plusDays(1);
            String body = String.format("""
                    {
                      "warehouseId": %d,
                      "slipType": "NORMAL",
                      "partnerId": %d,
                      "plannedDate": "%s",
                      "lines": [
                        {"productId": %d, "unitType": "PIECE", "orderedQty": 1}
                      ]
                    }""", warehouseId, customerPartnerId, plannedDate, stoppedProductId);

            try {
                ResponseEntity<String> response = postJson(SLIPS_URL, body, adminHeaders);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                JsonNode json = parseJson(response.getBody());
                assertThat(json.get("code").asText()).isEqualTo("OUTBOUND_PRODUCT_SHIPMENT_STOPPED");
            } finally {
                jdbcTemplate.update("DELETE FROM products WHERE product_code = 'PRD-STOP'");
            }
        }

        @Test
        @DisplayName("SC-OUT-003: 通常出荷でpartnerIdがnullの場合422エラー")
        void create_normalSlipWithoutPartner_returns422() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            String body = String.format("""
                    {
                      "warehouseId": %d,
                      "slipType": "NORMAL",
                      "plannedDate": "%s",
                      "lines": [
                        {"productId": %d, "unitType": "PIECE", "orderedQty": 10}
                      ]
                    }""", warehouseId, plannedDate, productAmbId);

            ResponseEntity<String> response = postJson(SLIPS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OUTBOUND_PARTNER_REQUIRED");
        }

        @Test
        @DisplayName("SC-OUT-003: 出荷先がSUPPLIERの場合422エラー")
        void create_supplierPartner_returns422() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            String body = String.format("""
                    {
                      "warehouseId": %d,
                      "slipType": "NORMAL",
                      "partnerId": %d,
                      "plannedDate": "%s",
                      "lines": [
                        {"productId": %d, "unitType": "PIECE", "orderedQty": 10}
                      ]
                    }""", warehouseId, supplierPartnerId, plannedDate, productAmbId);

            ResponseEntity<String> response = postJson(SLIPS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OUTBOUND_PARTNER_NOT_CUSTOMER");
        }

        @Test
        @DisplayName("SC-OUT-003: 同一伝票内に同じ商品が複数指定された場合409エラー")
        void create_duplicateProductInLines_returns409() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            String body = createSlipRequestBody(plannedDate, productAmbId, 10, productAmbId, 20);

            ResponseEntity<String> response = postJson(SLIPS_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DUPLICATE_PRODUCT_IN_LINES");
        }
    }

    // ========================================================
    // 出荷伝票: 一覧・詳細
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/outbound/slips — 出荷伝票一覧")
    class ListOutboundSlips {

        @Test
        @DisplayName("SC-OUT-005: 受注一覧の表示とページネーションが正しく動作する")
        void list_withPagination_returnsPagedResults() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            // 3件の出荷伝票を作成
            insertOutboundSlip("OUT-TEST-L01", "ORDERED", plannedDate);
            insertOutboundSlip("OUT-TEST-L02", "ORDERED", plannedDate);
            insertOutboundSlip("OUT-TEST-L03", "ORDERED", plannedDate.plusDays(1));

            String url = SLIPS_URL + "?warehouseId=" + warehouseId + "&page=0&size=2&sort=slipNumber,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asLong()).isEqualTo(3);
            assertThat(json.get("totalPages").asInt()).isEqualTo(2);
            assertThat(json.get("content")).hasSize(2);
        }

        @Test
        @DisplayName("SC-OUT-006: ステータスフィルタで絞り込みができる")
        void list_filterByStatus_returnsFilteredResults() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            insertOutboundSlip("OUT-TEST-S01", "ORDERED", plannedDate);
            insertOutboundSlip("OUT-TEST-S02", "ALLOCATED", plannedDate);
            insertOutboundSlip("OUT-TEST-S03", "CANCELLED", plannedDate);

            String url = SLIPS_URL + "?warehouseId=" + warehouseId +
                    "&status=ORDERED&page=0&size=20&sort=slipNumber,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asLong()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("status").asText()).isEqualTo("ORDERED");
        }

        @Test
        @DisplayName("SC-OUT-005: 伝票番号で部分一致検索ができる")
        void list_filterBySlipNumber_returnsMatchingResults() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            insertOutboundSlip("OUT-TEST-F01", "ORDERED", plannedDate);
            insertOutboundSlip("OUT-TEST-F02", "ORDERED", plannedDate);
            insertOutboundSlip("OUT-ANOTHER-01", "ORDERED", plannedDate);

            String url = SLIPS_URL + "?warehouseId=" + warehouseId +
                    "&slipNumber=OUT-TEST&page=0&size=20&sort=slipNumber,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asLong()).isEqualTo(2);
        }

        @Test
        @DisplayName("SC-OUT-005: 出荷予定日範囲フィルタが動作する")
        void list_filterByPlannedDateRange_returnsFilteredResults() throws Exception {
            LocalDate date1 = LocalDate.now().plusDays(1);
            LocalDate date2 = LocalDate.now().plusDays(5);
            LocalDate date3 = LocalDate.now().plusDays(10);
            insertOutboundSlip("OUT-TEST-D01", "ORDERED", date1);
            insertOutboundSlip("OUT-TEST-D02", "ORDERED", date2);
            insertOutboundSlip("OUT-TEST-D03", "ORDERED", date3);

            String url = SLIPS_URL + "?warehouseId=" + warehouseId +
                    "&plannedDateFrom=" + date1 + "&plannedDateTo=" + date2 +
                    "&page=0&size=20&sort=plannedDate,asc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asLong()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/outbound/slips/{id} — 出荷伝票詳細")
    class GetOutboundSlip {

        @Test
        @DisplayName("SC-OUT-005: 出荷伝票詳細を取得できる")
        void get_existingSlip_returns200() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-GET01", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");
            insertOutboundSlipLine(slipId, 2, productAmb2Id, "AMB-002", "緑茶ペットボトル 500ml",
                    "PIECE", 20, "ORDERED");

            ResponseEntity<String> response = get(SLIPS_URL + "/" + slipId, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("id").asLong()).isEqualTo(slipId);
            assertThat(json.get("slipNumber").asText()).isEqualTo("OUT-TEST-GET01");
            assertThat(json.get("status").asText()).isEqualTo("ORDERED");
            assertThat(json.get("lines")).hasSize(2);
            assertThat(json.get("lines").get(0).get("productCode").asText()).isEqualTo("AMB-001");
            assertThat(json.get("lines").get(1).get("productCode").asText()).isEqualTo("AMB-002");
        }

        @Test
        @DisplayName("SC-OUT-005: 存在しない伝票IDで404エラー")
        void get_nonExistingSlip_returns404() throws Exception {
            ResponseEntity<String> response = get(SLIPS_URL + "/999999", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OUTBOUND_SLIP_NOT_FOUND");
        }
    }

    // ========================================================
    // 出荷伝票: 削除
    // ========================================================

    @Nested
    @DisplayName("DELETE /api/v1/outbound/slips/{id} — 出荷伝票削除")
    class DeleteOutboundSlip {

        @Test
        @DisplayName("SC-OUT-DEL: ORDERED状態の伝票を削除できる")
        void delete_orderedSlip_returns204() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-DEL01", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");

            ResponseEntity<String> response = delete(SLIPS_URL + "/" + slipId, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(countSlips()).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-OUT-DEL: ALLOCATED状態の伝票は削除できない")
        void delete_allocatedSlip_returns409() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-DEL02", "ALLOCATED", plannedDate);

            ResponseEntity<String> response = delete(SLIPS_URL + "/" + slipId, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OUTBOUND_INVALID_STATUS");
            assertThat(countSlips()).isEqualTo(1);
        }

        @Test
        @DisplayName("SC-OUT-DEL: 存在しない伝票の削除は404エラー")
        void delete_nonExistingSlip_returns404() throws Exception {
            ResponseEntity<String> response = delete(SLIPS_URL + "/999999", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ========================================================
    // 出荷伝票: キャンセル
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/outbound/slips/{id}/cancel — 出荷キャンセル")
    class CancelOutboundSlip {

        @Test
        @DisplayName("SC-OUT-007: ORDERED状態の受注をキャンセルできる")
        void cancel_orderedSlip_returns200() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-CAN01", "ORDERED", plannedDate);
            insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001", "ミネラルウォーター 500ml",
                    "PIECE", 10, "ORDERED");
            insertOutboundSlipLine(slipId, 2, productAmb2Id, "AMB-002", "緑茶ペットボトル 500ml",
                    "PIECE", 20, "ORDERED");

            String body = "{\"reason\": \"テストキャンセル\"}";
            ResponseEntity<String> response = postJson(SLIPS_URL + "/" + slipId + "/cancel", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("CANCELLED");
            assertThat(json.get("cancelledAt")).isNotNull();
            assertThat(json.get("cancelledBy")).isNotNull();

            // DB検証
            assertThat(getSlipStatus(slipId)).isEqualTo("CANCELLED");
            assertThat(getLineStatus(slipId, 1)).isEqualTo("CANCELLED");
            assertThat(getLineStatus(slipId, 2)).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("SC-OUT-010: PICKING_COMPLETED以降のステータスではキャンセルが拒否される")
        void cancel_pickingCompletedSlip_returns409() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-CAN02", "PICKING_COMPLETED", plannedDate);

            String body = "{\"reason\": \"テスト\"}";
            ResponseEntity<String> response = postJson(SLIPS_URL + "/" + slipId + "/cancel", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OUTBOUND_INVALID_STATUS");
            assertThat(getSlipStatus(slipId)).isEqualTo("PICKING_COMPLETED");
        }

        @Test
        @DisplayName("SC-OUT-010: SHIPPED状態ではキャンセルが拒否される")
        void cancel_shippedSlip_returns409() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-CAN03", "SHIPPED", plannedDate);

            String body = "{\"reason\": \"テスト\"}";
            ResponseEntity<String> response = postJson(SLIPS_URL + "/" + slipId + "/cancel", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(getSlipStatus(slipId)).isEqualTo("SHIPPED");
        }
    }

    // ========================================================
    // ピッキング指示: 作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/outbound/picking — ピッキング指示作成")
    class CreatePickingInstruction {

        @Test
        @DisplayName("SC-OUT-011: ALLOCATED状態の受注からピッキング指示を作成できる")
        void create_allocatedSlip_returns201() throws Exception {
            // ALLOCATED状態の伝票 + 引当明細を準備
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-PIC01", "ALLOCATED", plannedDate);
            Long lineId = insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001",
                    "ミネラルウォーター 500ml", "PIECE", 10, "ALLOCATED");

            // 引当明細を直接投入
            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',10, now(), now())",
                    slipId, lineId, locA01_01_01_01, productAmbId);

            String body = String.format("{\"slipIds\":[%d],\"areaId\":%d}", slipId, areaA01Id);
            ResponseEntity<String> response = postJson(PICKING_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("instructionNumber").asText()).startsWith("PIC-");
            assertThat(json.get("status").asText()).isEqualTo("CREATED");
            assertThat(json.get("lines")).hasSize(1);
            assertThat(json.get("lines").get(0).get("qtyToPick").asInt()).isEqualTo(10);
            assertThat(json.get("lines").get(0).get("productCode").asText()).isEqualTo("AMB-001");

            // DB検証
            int pickingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM picking_instructions", Integer.class);
            assertThat(pickingCount).isEqualTo(1);
        }

        @Test
        @DisplayName("SC-OUT-011: 複数受注からピッキング指示を一括作成できる")
        void create_multipleSlips_returns201() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);

            Long slipId1 = insertOutboundSlip("OUT-TEST-PIC02A", "ALLOCATED", plannedDate);
            Long lineId1 = insertOutboundSlipLine(slipId1, 1, productAmbId, "AMB-001",
                    "ミネラルウォーター 500ml", "PIECE", 5, "ALLOCATED");
            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',5, now(), now())",
                    slipId1, lineId1, locA01_01_01_01, productAmbId);

            Long slipId2 = insertOutboundSlip("OUT-TEST-PIC02B", "ALLOCATED", plannedDate);
            Long lineId2 = insertOutboundSlipLine(slipId2, 1, productAmb2Id, "AMB-002",
                    "緑茶ペットボトル 500ml", "PIECE", 8, "ALLOCATED");
            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',8, now(), now())",
                    slipId2, lineId2, locA01_01_01_01, productAmb2Id);

            String body = String.format("{\"slipIds\":[%d,%d],\"areaId\":%d}", slipId1, slipId2, areaA01Id);
            ResponseEntity<String> response = postJson(PICKING_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("lines")).hasSize(2);
        }

        @Test
        @DisplayName("SC-OUT-011: ORDERED状態の伝票ではピッキング指示を作成できない")
        void create_orderedSlip_returns409() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-PIC03", "ORDERED", plannedDate);

            String body = String.format("{\"slipIds\":[%d]}", slipId);
            ResponseEntity<String> response = postJson(PICKING_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OUTBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("SC-OUT-012: ばらし未完了の受注ではピッキング指示を作成できない")
        void create_unpackNotCompleted_returns409() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-PIC04", "ALLOCATED", plannedDate);
            Long lineId = insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001",
                    "ミネラルウォーター 500ml", "PIECE", 10, "ALLOCATED");

            // 引当明細
            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',10, now(), now())",
                    slipId, lineId, locA01_01_01_01, productAmbId);

            // 未完了ばらし指示
            jdbcTemplate.update(
                    "INSERT INTO unpack_instructions (outbound_slip_id, outbound_slip_line_id, " +
                            "location_id, product_id, unit_type, from_unit_type, " +
                            "required_qty, status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 'PIECE', 'CASE', 10, 'INSTRUCTED', now(), now())",
                    slipId, lineId, locA01_01_01_01, productAmbId);

            String body = String.format("{\"slipIds\":[%d],\"areaId\":%d}", slipId, areaA01Id);
            ResponseEntity<String> response = postJson(PICKING_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("UNPACK_NOT_COMPLETED");
        }
    }

    // ========================================================
    // ピッキング指示: 一覧・詳細
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/outbound/picking — ピッキング指示一覧・詳細")
    class ListAndGetPickingInstruction {

        @Test
        @DisplayName("SC-OUT-PIC: ピッキング指示一覧を取得できる")
        void list_returns200() throws Exception {
            // ピッキング指示をDB直接投入
            jdbcTemplate.update(
                    "INSERT INTO picking_instructions (instruction_number, warehouse_id, area_id, " +
                            "status, created_at, created_by) " +
                            "VALUES ('PIC-TEST-001', ?, ?, 'CREATED', now(), ?)",
                    warehouseId, areaA01Id, adminUserId);
            jdbcTemplate.update(
                    "INSERT INTO picking_instructions (instruction_number, warehouse_id, area_id, " +
                            "status, created_at, created_by) " +
                            "VALUES ('PIC-TEST-002', ?, ?, 'COMPLETED', now(), ?)",
                    warehouseId, areaA01Id, adminUserId);

            String url = PICKING_URL + "?warehouseId=" + warehouseId + "&page=0&size=20&sort=createdAt,desc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asLong()).isEqualTo(2);
            assertThat(json.get("content")).hasSize(2);
        }

        @Test
        @DisplayName("SC-OUT-PIC: ステータスフィルタでピッキング指示を絞り込みできる")
        void list_filterByStatus_returnsFiltered() throws Exception {
            jdbcTemplate.update(
                    "INSERT INTO picking_instructions (instruction_number, warehouse_id, area_id, " +
                            "status, created_at, created_by) " +
                            "VALUES ('PIC-TEST-S01', ?, ?, 'CREATED', now(), ?)",
                    warehouseId, areaA01Id, adminUserId);
            jdbcTemplate.update(
                    "INSERT INTO picking_instructions (instruction_number, warehouse_id, area_id, " +
                            "status, created_at, created_by) " +
                            "VALUES ('PIC-TEST-S02', ?, ?, 'COMPLETED', now(), ?)",
                    warehouseId, areaA01Id, adminUserId);

            String url = PICKING_URL + "?warehouseId=" + warehouseId +
                    "&status=CREATED&page=0&size=20&sort=createdAt,desc";
            ResponseEntity<String> response = get(url, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asLong()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("status").asText()).isEqualTo("CREATED");
        }

        @Test
        @DisplayName("SC-OUT-PIC: ピッキング指示詳細を取得できる")
        void get_existingInstruction_returns200() throws Exception {
            // ピッキング指示 + 明細を準備
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-PICGET", "ALLOCATED", plannedDate);
            Long lineId = insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001",
                    "ミネラルウォーター 500ml", "PIECE", 10, "ALLOCATED");

            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',10, now(), now())",
                    slipId, lineId, locA01_01_01_01, productAmbId);

            // APIでピッキング指示作成
            String createBody = String.format("{\"slipIds\":[%d],\"areaId\":%d}", slipId, areaA01Id);
            ResponseEntity<String> createResp = postJson(PICKING_URL, createBody, adminHeaders);
            JsonNode createJson = parseJson(createResp.getBody());
            Long pickingId = createJson.get("id").asLong();

            // 詳細取得
            ResponseEntity<String> response = get(PICKING_URL + "/" + pickingId, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("id").asLong()).isEqualTo(pickingId);
            assertThat(json.get("status").asText()).isEqualTo("CREATED");
            assertThat(json.get("lines")).hasSize(1);
            assertThat(json.get("lines").get(0).get("qtyToPick").asInt()).isEqualTo(10);
            assertThat(json.get("lines").get(0).get("lineStatus").asText()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("SC-OUT-PIC: 存在しないピッキング指示IDで404エラー")
        void get_nonExistingInstruction_returns404() throws Exception {
            ResponseEntity<String> response = get(PICKING_URL + "/999999", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("PICKING_NOT_FOUND");
        }
    }

    // ========================================================
    // ピッキング完了
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/outbound/picking/{id}/complete — ピッキング完了")
    class CompletePickingInstruction {

        @Test
        @DisplayName("SC-OUT-013: 全明細のピッキング完了で受注がPICKING_COMPLETEDに遷移する")
        void complete_allLines_returns200() throws Exception {
            // ALLOCATED伝票 + 引当明細 + ピッキング指示を準備
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-COMP01", "ALLOCATED", plannedDate);
            Long lineId1 = insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001",
                    "ミネラルウォーター 500ml", "PIECE", 10, "ALLOCATED");
            Long lineId2 = insertOutboundSlipLine(slipId, 2, productAmb2Id, "AMB-002",
                    "緑茶ペットボトル 500ml", "PIECE", 20, "ALLOCATED");

            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',10, now(), now())",
                    slipId, lineId1, locA01_01_01_01, productAmbId);
            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',20, now(), now())",
                    slipId, lineId2, locA01_01_01_01, productAmb2Id);

            // ピッキング指示作成
            String createBody = String.format("{\"slipIds\":[%d],\"areaId\":%d}", slipId, areaA01Id);
            ResponseEntity<String> createResp = postJson(PICKING_URL, createBody, adminHeaders);
            JsonNode createJson = parseJson(createResp.getBody());
            Long pickingId = createJson.get("id").asLong();
            Long pickLineId1 = createJson.get("lines").get(0).get("id").asLong();
            Long pickLineId2 = createJson.get("lines").get(1).get("id").asLong();
            int qtyToPick1 = createJson.get("lines").get(0).get("qtyToPick").asInt();
            int qtyToPick2 = createJson.get("lines").get(1).get("qtyToPick").asInt();

            // ピッキング完了
            String completeBody = String.format("""
                    {
                      "lines": [
                        {"lineId": %d, "qtyPicked": %d},
                        {"lineId": %d, "qtyPicked": %d}
                      ]
                    }""", pickLineId1, qtyToPick1, pickLineId2, qtyToPick2);
            ResponseEntity<String> response = put(PICKING_URL + "/" + pickingId + "/complete",
                    completeBody, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(json.get("completedAt")).isNotNull();
            assertThat(json.get("completedBy")).isNotNull();

            // ピッキング明細ステータス
            for (JsonNode line : json.get("lines")) {
                assertThat(line.get("lineStatus").asText()).isEqualTo("COMPLETED");
                assertThat(line.get("qtyPicked").asInt()).isGreaterThan(0);
            }

            // DB検証: 出荷伝票ステータスがPICKING_COMPLETEDに遷移
            assertThat(getSlipStatus(slipId)).isEqualTo("PICKING_COMPLETED");
            assertThat(getLineStatus(slipId, 1)).isEqualTo("PICKING_COMPLETED");
            assertThat(getLineStatus(slipId, 2)).isEqualTo("PICKING_COMPLETED");
        }

        @Test
        @DisplayName("SC-OUT-014: ピッキング数量がqty_to_pickを超過するとエラー")
        void complete_qtyExceeded_returns422() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-COMP02", "ALLOCATED", plannedDate);
            Long lineId = insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001",
                    "ミネラルウォーター 500ml", "PIECE", 5, "ALLOCATED");

            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',5, now(), now())",
                    slipId, lineId, locA01_01_01_01, productAmbId);

            String createBody = String.format("{\"slipIds\":[%d],\"areaId\":%d}", slipId, areaA01Id);
            ResponseEntity<String> createResp = postJson(PICKING_URL, createBody, adminHeaders);
            JsonNode createJson = parseJson(createResp.getBody());
            Long pickingId = createJson.get("id").asLong();
            Long pickLineId = createJson.get("lines").get(0).get("id").asLong();

            // 超過数量で完了登録
            String completeBody = String.format("""
                    {
                      "lines": [
                        {"lineId": %d, "qtyPicked": 6}
                      ]
                    }""", pickLineId);
            ResponseEntity<String> response = put(PICKING_URL + "/" + pickingId + "/complete",
                    completeBody, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("PICKING_QTY_EXCEEDED");
        }

        @Test
        @DisplayName("SC-OUT-014: 既にCOMPLETED状態のピッキング指示に対する完了は409エラー")
        void complete_alreadyCompleted_returns409() throws Exception {
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            Long slipId = insertOutboundSlip("OUT-TEST-COMP03", "ALLOCATED", plannedDate);
            Long lineId = insertOutboundSlipLine(slipId, 1, productAmbId, "AMB-001",
                    "ミネラルウォーター 500ml", "PIECE", 5, "ALLOCATED");

            jdbcTemplate.update(
                    "INSERT INTO allocation_details (outbound_slip_id, outbound_slip_line_id, " +
                            "warehouse_id, location_id, product_id, unit_type, allocated_qty, created_at, updated_at) " +
                            "VALUES (?, ?, " + warehouseId + ", ?, ?, 'PIECE',5, now(), now())",
                    slipId, lineId, locA01_01_01_01, productAmbId);

            String createBody = String.format("{\"slipIds\":[%d],\"areaId\":%d}", slipId, areaA01Id);
            ResponseEntity<String> createResp = postJson(PICKING_URL, createBody, adminHeaders);
            JsonNode createJson = parseJson(createResp.getBody());
            Long pickingId = createJson.get("id").asLong();
            Long pickLineId = createJson.get("lines").get(0).get("id").asLong();

            // 1回目: 完了成功
            String completeBody = String.format("""
                    {"lines": [{"lineId": %d, "qtyPicked": 5}]}""", pickLineId);
            put(PICKING_URL + "/" + pickingId + "/complete", completeBody, adminHeaders);

            // 2回目: 既に完了済み → 409
            ResponseEntity<String> response = put(PICKING_URL + "/" + pickingId + "/complete",
                    completeBody, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OUTBOUND_INVALID_STATUS");
        }
    }

    // ========================================================
    // E2E: 受注→引当→ピッキング→ピッキング完了の一連フロー
    // ========================================================

    @Nested
    @DisplayName("E2E: 受注→引当→ピッキング→ピッキング完了")
    class EndToEndFlow {

        @Test
        @DisplayName("SC-OUT-020: 受注登録から引当→ピッキング→ピッキング完了までの一連フローが動作する")
        void e2e_orderToPickingComplete_succeeds() throws Exception {
            // ========== フェーズ0: 在庫準備 ==========
            insertInventory(locA01_01_01_01, productAmbId, "PIECE", 100, 0);
            insertInventory(locA01_01_01_02, productAmb2Id, "PIECE", 200, 0);

            // ========== フェーズ1: 受注登録 ==========
            LocalDate plannedDate = LocalDate.now().plusDays(1);
            String createBody = createSlipRequestBody(plannedDate, productAmbId, 10, productAmb2Id, 30);
            ResponseEntity<String> createResp = postJson(SLIPS_URL, createBody, adminHeaders);

            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode createJson = parseJson(createResp.getBody());
            Long slipId = createJson.get("id").asLong();
            String slipNumber = createJson.get("slipNumber").asText();
            assertThat(slipNumber).startsWith("OUT-");
            assertThat(createJson.get("status").asText()).isEqualTo("ORDERED");

            // ========== フェーズ2: 在庫引当 ==========
            String allocBody = String.format("{\"outboundSlipIds\":[%d]}", slipId);
            ResponseEntity<String> allocResp = postJson(ALLOCATION_EXECUTE_URL, allocBody, adminHeaders);

            assertThat(allocResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode allocJson = parseJson(allocResp.getBody());
            assertThat(allocJson.get("allocatedCount").asInt()).isEqualTo(1);
            assertThat(allocJson.get("allocatedSlips").get(0).get("status").asText()).isEqualTo("ALLOCATED");

            // DB検証: ステータス
            assertThat(getSlipStatus(slipId)).isEqualTo("ALLOCATED");

            // DB検証: 引当明細
            assertThat(countAllocationDetails(slipId)).isGreaterThanOrEqualTo(2);

            // DB検証: 在庫のallocated_qty
            assertThat(getAllocatedQty(locA01_01_01_01, productAmbId, "PIECE")).isEqualTo(10);
            assertThat(getAllocatedQty(locA01_01_01_02, productAmb2Id, "PIECE")).isEqualTo(30);

            // ========== フェーズ3: ピッキング指示作成 ==========
            String pickBody = String.format("{\"slipIds\":[%d],\"areaId\":%d}", slipId, areaA01Id);
            ResponseEntity<String> pickResp = postJson(PICKING_URL, pickBody, adminHeaders);

            assertThat(pickResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode pickJson = parseJson(pickResp.getBody());
            Long pickingId = pickJson.get("id").asLong();
            assertThat(pickJson.get("instructionNumber").asText()).startsWith("PIC-");
            assertThat(pickJson.get("status").asText()).isEqualTo("CREATED");
            assertThat(pickJson.get("lines").size()).isGreaterThanOrEqualTo(2);

            // DB検証: ピッキング指示作成後も出荷伝票ステータスはALLOCATEDのまま
            assertThat(getSlipStatus(slipId)).isEqualTo("ALLOCATED");

            // ========== フェーズ4: ピッキング完了 ==========
            // 全明細のIDと予定数を取得
            StringBuilder linesJson = new StringBuilder("[");
            for (int i = 0; i < pickJson.get("lines").size(); i++) {
                JsonNode line = pickJson.get("lines").get(i);
                if (i > 0) linesJson.append(",");
                linesJson.append(String.format(
                        "{\"lineId\":%d,\"qtyPicked\":%d}",
                        line.get("id").asLong(), line.get("qtyToPick").asInt()));
            }
            linesJson.append("]");

            String completeBody = "{\"lines\":" + linesJson + "}";
            ResponseEntity<String> completeResp = put(
                    PICKING_URL + "/" + pickingId + "/complete", completeBody, adminHeaders);

            assertThat(completeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode completeJson = parseJson(completeResp.getBody());
            assertThat(completeJson.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(completeJson.get("completedAt")).isNotNull();

            // DB検証: 出荷伝票がPICKING_COMPLETEDに遷移
            assertThat(getSlipStatus(slipId)).isEqualTo("PICKING_COMPLETED");

            // DB検証: ピッキング指示が完了
            String pickingStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM picking_instructions WHERE id = ?", String.class, pickingId);
            assertThat(pickingStatus).isEqualTo("COMPLETED");

            // DB検証: 全ピッキング明細がCOMPLETED
            int pendingLines = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM picking_instruction_lines WHERE picking_instruction_id = ? AND line_status != 'COMPLETED'",
                    Integer.class, pickingId);
            assertThat(pendingLines).isEqualTo(0);

            // DB検証: 引当明細は保持されている（出荷完了後も引当履歴として残る）
            assertThat(countAllocationDetails(slipId)).isGreaterThanOrEqualTo(2);

            // ========== フェーズ5: 受注詳細で最終状態確認 ==========
            ResponseEntity<String> detailResp = get(SLIPS_URL + "/" + slipId, adminHeaders);
            assertThat(detailResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode detailJson = parseJson(detailResp.getBody());
            assertThat(detailJson.get("status").asText()).isEqualTo("PICKING_COMPLETED");
            // 引当数がセットされている
            for (JsonNode line : detailJson.get("lines")) {
                assertThat(line.get("allocatedQty").asInt()).isGreaterThan(0);
                assertThat(line.get("pickingQty").asInt()).isGreaterThan(0);
            }
        }
    }
}
