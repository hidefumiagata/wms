package com.wms.inbound.controller;

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

@DisplayName("結合テスト: 入荷管理")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InboundSlipIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/inbound/slips";
    private static final String RESULTS_URL = "/api/v1/inbound/results";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";

    private HttpHeaders adminHeaders;
    private Long warehouseId;
    private Long partnerId;
    private Long productId;           // AMB-001 (常温, ロットなし, 期限なし)
    private Long productIdLot;        // REF-001 (冷蔵, ロットあり, 期限あり)
    private Long inboundLocationId;   // A-01-INB-01 (INBOUNDエリア)
    private String plannedDate;       // テスト全体で統一する予定日

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        partnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'SUP001'", Long.class);
        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);
        productIdLot = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'REF-001'", Long.class);
        inboundLocationId = jdbcTemplate.queryForObject(
                "SELECT l.id FROM locations l JOIN areas a ON l.area_id = a.id WHERE l.location_code = 'A-01-INB-01' AND a.area_type = 'INBOUND'",
                Long.class);
    }

    @BeforeEach
    void setUp() {
        // テスト用入荷伝票データのクリーンアップ（在庫含む）
        jdbcTemplate.update(
                "DELETE FROM inventory_movements WHERE inbound_slip_id IN (SELECT id FROM inbound_slips)");
        jdbcTemplate.update(
                "DELETE FROM inventories WHERE warehouse_id = ?", warehouseId);
        jdbcTemplate.update("DELETE FROM inbound_slip_lines");
        jdbcTemplate.update("DELETE FROM inbound_slips");

        // テスト汚染防止: is_active / is_stocktaking_locked を必ず初期値に戻す（try/finally の二重安全網）
        jdbcTemplate.update("UPDATE products SET is_active = true WHERE id = ?", productId);
        jdbcTemplate.update("UPDATE locations SET is_stocktaking_locked = false WHERE id = ?", inboundLocationId);

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
        plannedDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    // ========================================================
    // POST /api/v1/inbound/slips — 入荷伝票作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/inbound/slips — 入荷伝票作成")
    class CreateInboundSlip {

        @Test
        @DisplayName("SC-INB-001: 単一明細の通常入荷伝票を正常に作成できる")
        void create_singleLine_returns201() throws Exception {
            String body = createSlipBody(productId, "PIECE", 100, null, null);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("slipNumber").asText()).startsWith("INB-");
            assertThat(json.get("slipType").asText()).isEqualTo("NORMAL");
            assertThat(json.get("status").asText()).isEqualTo("PLANNED");
            assertThat(json.get("warehouseId").asLong()).isEqualTo(warehouseId);
            assertThat(json.get("partnerId").asLong()).isEqualTo(partnerId);
            assertThat(json.get("lines").size()).isEqualTo(1);
            assertThat(json.get("lines").get(0).get("lineNo").asInt()).isEqualTo(1);
            assertThat(json.get("lines").get(0).get("plannedQty").asInt()).isEqualTo(100);
            assertThat(json.get("lines").get(0).get("lineStatus").asText()).isEqualTo("PENDING");

            // DB検証
            Long slipId = json.get("id").asLong();
            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM inbound_slips WHERE id = ?", String.class, slipId);
            assertThat(dbStatus).isEqualTo("PLANNED");
        }

        @Test
        @DisplayName("SC-INB-002: 複数明細の入荷伝票を作成できる")
        void create_multipleLines_returns201() throws Exception {
            Long productId2 = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "partnerId": %d,
                        "plannedDate": "%s",
                        "slipType": "NORMAL",
                        "lines": [
                            { "productId": %d, "unitType": "PIECE", "plannedQty": 50 },
                            { "productId": %d, "unitType": "CASE", "plannedQty": 10 }
                        ]
                    }
                    """, warehouseId, partnerId, plannedDate, productId, productId2);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("lines").size()).isEqualTo(2);
            assertThat(json.get("lines").get(0).get("lineNo").asInt()).isEqualTo(1);
            assertThat(json.get("lines").get(1).get("lineNo").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("SC-INB-003: ロット・期限管理商品を含む伝票を作成できる")
        void create_lotAndExpiryProduct_returns201() throws Exception {
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "partnerId": %d,
                        "plannedDate": "%s",
                        "slipType": "NORMAL",
                        "lines": [
                            {
                                "productId": %d,
                                "unitType": "PIECE",
                                "plannedQty": 30,
                                "lotNumber": "LOT-2026-001",
                                "expiryDate": "2026-12-31"
                            }
                        ]
                    }
                    """, warehouseId, partnerId, plannedDate, productIdLot);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            JsonNode line = json.get("lines").get(0);
            assertThat(line.get("lotNumber").asText()).isEqualTo("LOT-2026-001");
            assertThat(line.get("expiryDate").asText()).isEqualTo("2026-12-31");
        }

        @Test
        @DisplayName("無効商品を含む伝票作成 → 422")
        void create_inactiveProduct_returns422() throws Exception {
            try {
                jdbcTemplate.update("UPDATE products SET is_active = false WHERE id = ?", productId);
                String body = createSlipBody(productId, "PIECE", 100, null, null);

                ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                JsonNode json = parseJson(response.getBody());
                assertThat(json.get("code").asText()).isEqualTo("PRODUCT_INACTIVE");
                assertThat(json.has("traceId")).isTrue();
            } finally {
                jdbcTemplate.update("UPDATE products SET is_active = true WHERE id = ?", productId);
            }
        }

        @Test
        @DisplayName("SC-INB-005: 必須フィールド未入力 → 400")
        void create_missingRequiredFields_returns400() throws Exception {
            String body = """
                    {
                        "warehouseId": null,
                        "lines": []
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("code")).isTrue();
        }

        @Test
        @DisplayName("同一商品の重複明細 → 409")
        void create_duplicateProductInLines_returns409() throws Exception {
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "partnerId": %d,
                        "plannedDate": "%s",
                        "slipType": "NORMAL",
                        "lines": [
                            { "productId": %d, "unitType": "PIECE", "plannedQty": 50 },
                            { "productId": %d, "unitType": "CASE", "plannedQty": 10 }
                        ]
                    }
                    """, warehouseId, partnerId, plannedDate, productId, productId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DUPLICATE_PRODUCT_IN_LINES");
        }

        @Test
        @DisplayName("ロット管理商品にロット番号なし → 422")
        void create_lotProductWithoutLotNumber_returns422() throws Exception {
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "partnerId": %d,
                        "plannedDate": "%s",
                        "slipType": "NORMAL",
                        "lines": [
                            { "productId": %d, "unitType": "PIECE", "plannedQty": 10 }
                        ]
                    }
                    """, warehouseId, partnerId, plannedDate, productIdLot);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("LOT_NUMBER_REQUIRED");
        }

        @Test
        @DisplayName("期限管理商品に期限日なし → 422")
        void create_expiryProductWithoutExpiryDate_returns422() throws Exception {
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "partnerId": %d,
                        "plannedDate": "%s",
                        "slipType": "NORMAL",
                        "lines": [
                            { "productId": %d, "unitType": "PIECE", "plannedQty": 10, "lotNumber": "LOT-001" }
                        ]
                    }
                    """, warehouseId, partnerId, plannedDate, productIdLot);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("EXPIRY_DATE_REQUIRED");
        }

        @Test
        @DisplayName("通常入荷で仕入先未指定 → 422")
        void create_normalWithoutPartner_returns422() throws Exception {
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "plannedDate": "%s",
                        "slipType": "NORMAL",
                        "lines": [
                            { "productId": %d, "unitType": "PIECE", "plannedQty": 10 }
                        ]
                    }
                    """, warehouseId, plannedDate, productId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_PARTNER_REQUIRED");
        }

        @Test
        @DisplayName("出荷先タイプの取引先で入荷 → 422")
        void create_customerPartner_returns422() throws Exception {
            Long customerId = jdbcTemplate.queryForObject(
                    "SELECT id FROM partners WHERE partner_code = 'CUS001'", Long.class);
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "partnerId": %d,
                        "plannedDate": "%s",
                        "slipType": "NORMAL",
                        "lines": [
                            { "productId": %d, "unitType": "PIECE", "plannedQty": 10 }
                        ]
                    }
                    """, warehouseId, customerId, plannedDate, productId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_PARTNER_NOT_SUPPLIER");
        }

        @Test
        @DisplayName("過去日の入荷予定日 → 422")
        void create_pastPlannedDate_returns422() throws Exception {
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "partnerId": %d,
                        "plannedDate": "2020-01-01",
                        "slipType": "NORMAL",
                        "lines": [
                            { "productId": %d, "unitType": "PIECE", "plannedQty": 10 }
                        ]
                    }
                    """, warehouseId, partnerId, productId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("PLANNED_DATE_TOO_EARLY");
        }
    }

    // ========================================================
    // GET /api/v1/inbound/slips/{id} — 入荷伝票詳細取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/inbound/slips/{id} — 入荷伝票詳細取得")
    class GetInboundSlip {

        @Test
        @DisplayName("正常系: 入荷伝票の詳細を取得できる")
        void get_existingSlip_returns200() throws Exception {
            Long slipId = createTestSlip();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + slipId, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("id").asLong()).isEqualTo(slipId);
            assertThat(json.get("slipNumber").asText()).startsWith("INB-");
            assertThat(json.get("status").asText()).isEqualTo("PLANNED");
            assertThat(json.has("lines")).isTrue();
            assertThat(json.has("createdByName")).isTrue();
        }

        @Test
        @DisplayName("存在しないID → 404")
        void get_nonExistentId_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }
    }

    // ========================================================
    // GET /api/v1/inbound/slips — 入荷伝票一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/inbound/slips — 入荷伝票一覧取得")
    class ListInboundSlips {

        @Test
        @DisplayName("SC-INB-010: 倉庫指定で一覧取得できる")
        void list_byWarehouse_returns200() throws Exception {
            createTestSlip();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + warehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("content")).isTrue();
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);
            assertThat(json.has("totalElements")).isTrue();
            assertThat(json.has("totalPages")).isTrue();
        }

        @Test
        @DisplayName("SC-INB-011: ステータスで絞り込みできる")
        void list_filterByStatus_returns200() throws Exception {
            createTestSlip();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + warehouseId + "&status=PLANNED&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("status").asText()).isEqualTo("PLANNED");
            }
        }

        @Test
        @DisplayName("ページネーションが正しく動作する")
        void list_pagination_works() throws Exception {
            createTestSlip();
            createTestSlip();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + warehouseId + "&page=0&size=1",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isEqualTo(1);
            assertThat(json.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        }
    }

    // ========================================================
    // DELETE /api/v1/inbound/slips/{id} — 入荷伝票削除
    // ========================================================

    @Nested
    @DisplayName("DELETE /api/v1/inbound/slips/{id} — 入荷伝票削除")
    class DeleteInboundSlip {

        @Test
        @DisplayName("SC-INB-006: PLANNED状態の伝票を削除できる")
        void delete_plannedSlip_returns204() throws Exception {
            Long slipId = createTestSlip();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + slipId, HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // DB検証: 物理削除されている
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slips WHERE id = ?", Integer.class, slipId);
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("SC-INB-007: CONFIRMED状態の伝票は削除不可 → 409")
        void delete_confirmedSlip_returns409() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + slipId, HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("存在しないID → 404")
        void delete_nonExistent_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }
    }

    // ========================================================
    // POST /api/v1/inbound/slips/{id}/confirm — 入荷確定
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/inbound/slips/{id}/confirm — 入荷確定")
    class ConfirmInboundSlip {

        @Test
        @DisplayName("SC-INB-020: PLANNED → CONFIRMED に遷移できる")
        void confirm_plannedSlip_returns200() throws Exception {
            Long slipId = createTestSlip();

            ResponseEntity<String> response = postNoBody(BASE_URL + "/" + slipId + "/confirm", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("CONFIRMED");

            // DB検証
            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM inbound_slips WHERE id = ?", String.class, slipId);
            assertThat(dbStatus).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("SC-INB-021: CONFIRMED状態から確定 → 409")
        void confirm_alreadyConfirmed_returns409() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);

            ResponseEntity<String> response = postNoBody(BASE_URL + "/" + slipId + "/confirm", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_INVALID_STATUS");
        }
    }

    // ========================================================
    // POST /api/v1/inbound/slips/{id}/cancel — 入荷キャンセル
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/inbound/slips/{id}/cancel — 入荷キャンセル")
    class CancelInboundSlip {

        @Test
        @DisplayName("SC-INB-030: PLANNED状態からキャンセルできる")
        void cancel_plannedSlip_returns200() throws Exception {
            Long slipId = createTestSlip();

            ResponseEntity<String> response = postNoBody(BASE_URL + "/" + slipId + "/cancel", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("CANCELLED");
            assertThat(json.get("cancelledAt")).isNotNull();
            assertThat(json.get("cancelledBy")).isNotNull();

            // DB検証: 全明細もCANCELLED
            Integer cancelledLines = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slip_lines WHERE inbound_slip_id = ? AND line_status = 'CANCELLED'",
                    Integer.class, slipId);
            Integer totalLines = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slip_lines WHERE inbound_slip_id = ?",
                    Integer.class, slipId);
            assertThat(cancelledLines).isEqualTo(totalLines);
        }

        @Test
        @DisplayName("SC-INB-031: CONFIRMED状態からキャンセルできる")
        void cancel_confirmedSlip_returns200() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);

            ResponseEntity<String> response = postNoBody(BASE_URL + "/" + slipId + "/cancel", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("STORED状態からキャンセル → 409")
        void cancel_storedSlip_returns409() throws Exception {
            Long slipId = createAndCompleteFullFlow();

            ResponseEntity<String> response = postNoBody(BASE_URL + "/" + slipId + "/cancel", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("CANCELLED状態からキャンセル → 409")
        void cancel_alreadyCancelled_returns409() throws Exception {
            Long slipId = createTestSlip();
            postNoBody(BASE_URL + "/" + slipId + "/cancel", adminHeaders);

            ResponseEntity<String> response = postNoBody(BASE_URL + "/" + slipId + "/cancel", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("SC-INB-031a: INSPECTING状態からキャンセルできる")
        void cancel_inspectingSlip_returns200() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);
            inspectLine(slipId, lineId, 100);

            ResponseEntity<String> response = postNoBody(BASE_URL + "/" + slipId + "/cancel", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("CANCELLED");
        }
    }

    // ========================================================
    // POST /api/v1/inbound/slips/{id}/inspect — 検品
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/inbound/slips/{id}/inspect — 検品")
    class InspectInboundSlip {

        @Test
        @DisplayName("SC-INB-040: 全数一致の検品が正常に完了する")
        void inspect_fullMatch_returns200() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": 100 }] }
                    """, lineId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("INSPECTING");
            JsonNode line = json.get("lines").get(0);
            assertThat(line.get("inspectedQty").asInt()).isEqualTo(100);
            assertThat(line.get("diffQty").asInt()).isEqualTo(0);
            assertThat(line.get("lineStatus").asText()).isEqualTo("INSPECTED");
        }

        @Test
        @DisplayName("SC-INB-041: 検品数量に差異がある場合（過不足）")
        void inspect_withDiscrepancy_returns200() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": 80 }] }
                    """, lineId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            JsonNode line = json.get("lines").get(0);
            assertThat(line.get("inspectedQty").asInt()).isEqualTo(80);
            assertThat(line.get("diffQty").asInt()).isEqualTo(-20);
        }

        @Test
        @DisplayName("SC-INB-043: 検品数量の上書き更新ができる")
        void inspect_overwrite_returns200() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);

            // 初回検品
            String body1 = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": 80 }] }
                    """, lineId);
            postJson(BASE_URL + "/" + slipId + "/inspect", body1, adminHeaders);

            // 上書き検品
            String body2 = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": 95 }] }
                    """, lineId);
            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", body2, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            JsonNode line = json.get("lines").get(0);
            assertThat(line.get("inspectedQty").asInt()).isEqualTo(95);
        }

        @Test
        @DisplayName("PLANNED状態から検品 → 409")
        void inspect_plannedSlip_returns409() throws Exception {
            Long slipId = createTestSlip();
            Long lineId = getFirstLineId(slipId);

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": 100 }] }
                    """, lineId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("存在しない明細ID → 404")
        void inspect_nonExistentLine_returns404() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);

            String body = """
                    { "lines": [{ "lineId": 999999, "inspectedQty": 100 }] }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_LINE_NOT_FOUND");
        }

        @Test
        @DisplayName("重複明細ID → 422")
        void inspect_duplicateLineId_returns422() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);

            String body = String.format("""
                    { "lines": [
                        { "lineId": %d, "inspectedQty": 50 },
                        { "lineId": %d, "inspectedQty": 60 }
                    ] }
                    """, lineId, lineId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DUPLICATE_LINE_IN_REQUEST");
        }

        @Test
        @DisplayName("SC-INB-044: PARTIAL_STORED状態で残明細を検品できる")
        void inspect_partialStoredRemaining_returns200() throws Exception {
            Long productId2 = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
            Long slipId = createTestSlipMultiLine(productId, productId2);
            confirmSlip(slipId);

            JsonNode detail = getSlipDetail(slipId);
            Long lineId1 = detail.get("lines").get(0).get("id").asLong();
            Long lineId2 = detail.get("lines").get(1).get("id").asLong();

            // 1明細のみ検品→入庫してPARTIAL_STOREDへ
            inspectLine(slipId, lineId1, 50);
            String storeBody = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId1, inboundLocationId);
            postJson(BASE_URL + "/" + slipId + "/store", storeBody, adminHeaders);

            // PARTIAL_STORED状態で残明細を検品
            String inspectBody = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": 10 }] }
                    """, lineId2);
            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", inspectBody, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("PARTIAL_STORED");
        }

        @Test
        @DisplayName("SC-INB-045: 入庫済み明細の検品 → 409")
        void inspect_storedLine_returns409() throws Exception {
            Long productId2 = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
            Long slipId = createTestSlipMultiLine(productId, productId2);
            confirmSlip(slipId);

            JsonNode detail = getSlipDetail(slipId);
            Long lineId1 = detail.get("lines").get(0).get("id").asLong();
            Long lineId2 = detail.get("lines").get(1).get("id").asLong();

            // 1明細のみ検品→入庫
            inspectLine(slipId, lineId1, 50);
            inspectLine(slipId, lineId2, 10);
            String storeBody = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId1, inboundLocationId);
            postJson(BASE_URL + "/" + slipId + "/store", storeBody, adminHeaders);

            // 入庫済みの明細を再検品 → エラー
            String inspectBody = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": 60 }] }
                    """, lineId1);
            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", inspectBody, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_LINE_ALREADY_STORED");
        }

        @Test
        @DisplayName("検品数が負数 → 422")
        void inspect_negativeQty_returns422() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": -1 }] }
                    """, lineId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_INSPECTED_QTY_NEGATIVE");
        }
    }

    // ========================================================
    // POST /api/v1/inbound/slips/{id}/store — 入庫
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/inbound/slips/{id}/store — 入庫")
    class StoreInboundSlip {

        @Test
        @DisplayName("SC-INB-050: 全明細を入庫してSTOREDに遷移する")
        void store_allLines_returns200() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);
            inspectLine(slipId, lineId, 100);

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId, inboundLocationId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/store", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("STORED");
            JsonNode line = json.get("lines").get(0);
            assertThat(line.get("lineStatus").asText()).isEqualTo("STORED");
            assertThat(line.get("putawayLocationId").asLong()).isEqualTo(inboundLocationId);
            assertThat(line.get("storedAt")).isNotNull();
            assertThat(line.get("storedBy")).isNotNull();

            // DB検証: 在庫が作成されている
            Integer inventoryQty = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM inventories WHERE location_id = ? AND product_id = ?",
                    Integer.class, inboundLocationId, productId);
            assertThat(inventoryQty).isEqualTo(100);
        }

        @Test
        @DisplayName("SC-INB-051: 一部明細のみ入庫 → PARTIAL_STORED")
        void store_partialLines_returnsPartialStored() throws Exception {
            Long productId2 = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
            Long slipId = createTestSlipMultiLine(productId, productId2);
            confirmSlip(slipId);

            // 全明細を検品
            JsonNode detail = getSlipDetail(slipId);
            Long lineId1 = detail.get("lines").get(0).get("id").asLong();
            Long lineId2 = detail.get("lines").get(1).get("id").asLong();
            inspectLine(slipId, lineId1, 50);
            inspectLine(slipId, lineId2, 10);

            // 1明細のみ入庫
            String body = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId1, inboundLocationId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/store", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("PARTIAL_STORED");
        }

        @Test
        @DisplayName("PLANNED状態から入庫 → 409")
        void store_plannedSlip_returns409() throws Exception {
            Long slipId = createTestSlip();
            Long lineId = getFirstLineId(slipId);

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId, inboundLocationId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/store", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("検品済みでない明細の入庫 → 409")
        void store_uninspectedLine_returns409() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);
            // 検品しない状態で入庫を試みる → ステータスがINSPECTINGにならないので先にステータスエラー

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId, inboundLocationId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/store", body, adminHeaders);

            // CONFIRMEDステータスからは入庫不可
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("出荷エリアのロケーションに入庫 → 422")
        void store_outboundAreaLocation_returns422() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);
            inspectLine(slipId, lineId, 100);

            Long outboundLocationId = jdbcTemplate.queryForObject(
                    "SELECT l.id FROM locations l JOIN areas a ON l.area_id = a.id WHERE l.location_code = 'A-01-OUT-01' AND a.area_type = 'OUTBOUND'",
                    Long.class);

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId, outboundLocationId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/store", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("AREA_NOT_INBOUND");
        }

        @Test
        @DisplayName("棚卸中ロケーションへの入庫 → 422")
        void store_stocktakeLockedLocation_returns422() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);
            inspectLine(slipId, lineId, 100);

            try {
                jdbcTemplate.update("UPDATE locations SET is_stocktaking_locked = true WHERE id = ?", inboundLocationId);

                String body = String.format("""
                        { "lines": [{ "lineId": %d, "locationId": %d }] }
                        """, lineId, inboundLocationId);

                ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/store", body, adminHeaders);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                JsonNode json = parseJson(response.getBody());
                assertThat(json.get("code").asText()).isEqualTo("LOCATION_STOCKTAKE_LOCKED");
                assertThat(json.has("traceId")).isTrue();
            } finally {
                jdbcTemplate.update("UPDATE locations SET is_stocktaking_locked = false WHERE id = ?", inboundLocationId);
            }
        }

        @Test
        @DisplayName("検品数0の明細を入庫 → 422")
        void store_zeroInspectedQty_returns422() throws Exception {
            Long slipId = createTestSlip();
            confirmSlip(slipId);
            Long lineId = getFirstLineId(slipId);
            inspectLine(slipId, lineId, 0);

            String body = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId, inboundLocationId);

            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/store", body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INSPECTED_QTY_ZERO");
        }

        @Test
        @DisplayName("SC-INB-052: 別商品が存在するロケーションへの入庫 → 422")
        void store_differentProductAtLocation_returns422() throws Exception {
            // 最初の伝票で AMB-001 をロケーションに入庫
            createAndCompleteFullFlow();

            // 別商品 AMB-002 の伝票を作成→確定→検品
            Long productId2 = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
            String body = createSlipBody(productId2, "PIECE", 20, null, null);
            ResponseEntity<String> createResp = postJson(BASE_URL, body, adminHeaders);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Long slipId2 = parseJson(createResp.getBody()).get("id").asLong();
            confirmSlip(slipId2);
            Long lineId2 = getFirstLineId(slipId2);
            inspectLine(slipId2, lineId2, 20);

            // 同じロケーションに別商品を入庫 → エラー
            String storeBody = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId2, inboundLocationId);
            ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId2 + "/store", storeBody, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DIFFERENT_PRODUCT_AT_LOCATION");
        }
    }

    // ========================================================
    // ステータス遷移 一気通貫テスト
    // ========================================================

    @Nested
    @DisplayName("ステータス遷移 一気通貫テスト")
    class StatusTransitionE2E {

        @Test
        @DisplayName("SC-INB-080: PLANNED → CONFIRMED → INSPECTING → STORED の完全フロー")
        void fullFlow_planned_to_stored() throws Exception {
            // 1. 作成 (PLANNED)
            String createBody = createSlipBody(productId, "PIECE", 100, null, null);
            ResponseEntity<String> createResp = postJson(BASE_URL, createBody, adminHeaders);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode createJson = parseJson(createResp.getBody());
            Long slipId = createJson.get("id").asLong();
            Long lineId = createJson.get("lines").get(0).get("id").asLong();
            assertThat(createJson.get("status").asText()).isEqualTo("PLANNED");

            // 2. 確定 (CONFIRMED)
            ResponseEntity<String> confirmResp = postNoBody(BASE_URL + "/" + slipId + "/confirm", adminHeaders);
            assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parseJson(confirmResp.getBody()).get("status").asText()).isEqualTo("CONFIRMED");

            // 3. 検品 (INSPECTING)
            String inspectBody = String.format("""
                    { "lines": [{ "lineId": %d, "inspectedQty": 100 }] }
                    """, lineId);
            ResponseEntity<String> inspectResp = postJson(BASE_URL + "/" + slipId + "/inspect", inspectBody, adminHeaders);
            assertThat(inspectResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parseJson(inspectResp.getBody()).get("status").asText()).isEqualTo("INSPECTING");

            // 4. 入庫 (STORED)
            String storeBody = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId, inboundLocationId);
            ResponseEntity<String> storeResp = postJson(BASE_URL + "/" + slipId + "/store", storeBody, adminHeaders);
            assertThat(storeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode storeJson = parseJson(storeResp.getBody());
            assertThat(storeJson.get("status").asText()).isEqualTo("STORED");

            // DB検証: 在庫反映
            Integer inventoryQty = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM inventories WHERE location_id = ? AND product_id = ?",
                    Integer.class, inboundLocationId, productId);
            assertThat(inventoryQty).isEqualTo(100);
        }

        @Test
        @DisplayName("SC-INB-061: PARTIAL_STORED → 残り入庫 → STORED")
        void partialStore_thenComplete() throws Exception {
            Long productId2 = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
            Long slipId = createTestSlipMultiLine(productId, productId2);
            confirmSlip(slipId);

            JsonNode detail = getSlipDetail(slipId);
            Long lineId1 = detail.get("lines").get(0).get("id").asLong();
            Long lineId2 = detail.get("lines").get(1).get("id").asLong();

            // 全明細検品
            inspectLine(slipId, lineId1, 50);
            inspectLine(slipId, lineId2, 10);

            // 1明細のみ入庫 → PARTIAL_STORED
            String storeBody1 = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId1, inboundLocationId);
            ResponseEntity<String> resp1 = postJson(BASE_URL + "/" + slipId + "/store", storeBody1, adminHeaders);
            assertThat(parseJson(resp1.getBody()).get("status").asText()).isEqualTo("PARTIAL_STORED");

            // 残り入庫 → STORED
            String storeBody2 = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId2, inboundLocationId);
            ResponseEntity<String> resp2 = postJson(BASE_URL + "/" + slipId + "/store", storeBody2, adminHeaders);
            assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parseJson(resp2.getBody()).get("status").asText()).isEqualTo("STORED");
        }

        @Test
        @DisplayName("SC-INB-033: PARTIAL_STOREDからキャンセル → 在庫ロールバック")
        void cancel_partialStored_rollsBackInventory() throws Exception {
            Long productId2 = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
            Long slipId = createTestSlipMultiLine(productId, productId2);
            confirmSlip(slipId);

            JsonNode detail = getSlipDetail(slipId);
            Long lineId1 = detail.get("lines").get(0).get("id").asLong();
            Long lineId2 = detail.get("lines").get(1).get("id").asLong();

            // 全明細検品
            inspectLine(slipId, lineId1, 50);
            inspectLine(slipId, lineId2, 10);

            // 1明細のみ入庫 → PARTIAL_STORED
            String storeBody = String.format("""
                    { "lines": [{ "lineId": %d, "locationId": %d }] }
                    """, lineId1, inboundLocationId);
            postJson(BASE_URL + "/" + slipId + "/store", storeBody, adminHeaders);

            // 在庫が存在することを確認
            Integer qtyBefore = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM inventories WHERE location_id = ? AND product_id = ?",
                    Integer.class, inboundLocationId, productId);
            assertThat(qtyBefore).isEqualTo(50);

            // キャンセル → 在庫ロールバック
            ResponseEntity<String> cancelResp = postNoBody(BASE_URL + "/" + slipId + "/cancel", adminHeaders);
            assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parseJson(cancelResp.getBody()).get("status").asText()).isEqualTo("CANCELLED");

            // 在庫が0になっている（または行が削除されている）
            Integer qtyAfter = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(quantity), 0) FROM inventories WHERE location_id = ? AND product_id = ?",
                    Integer.class, inboundLocationId, productId);
            assertThat(qtyAfter).isEqualTo(0);
        }
    }

    // ========================================================
    // GET /api/v1/inbound/results — 入荷実績
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/inbound/results — 入荷実績")
    class ListInboundResults {

        @Test
        @DisplayName("SC-INB-070: 入庫済み伝票が入荷実績に表示される")
        void results_storedSlip_returns200() throws Exception {
            createAndCompleteFullFlow();

            ResponseEntity<String> response = restTemplate.exchange(
                    RESULTS_URL + "?warehouseId=" + warehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("content")).isTrue();
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);

            JsonNode first = json.get("content").get(0);
            assertThat(first.has("slipNumber")).isTrue();
            assertThat(first.has("productCode")).isTrue();
            assertThat(first.has("inspectedQty")).isTrue();
            assertThat(first.has("storedAt")).isTrue();
            assertThat(first.has("locationCode")).isTrue();
        }

        @Test
        @DisplayName("SC-INB-071: 商品コードで入荷実績を絞り込みできる")
        void results_filterByProductCode_returns200() throws Exception {
            createAndCompleteFullFlow();

            ResponseEntity<String> response = restTemplate.exchange(
                    RESULTS_URL + "?warehouseId=" + warehouseId + "&productCode=AMB-001&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("productCode").asText()).startsWith("AMB-001");
            }
        }
    }

    // ========================================================
    // アクセス制御テスト
    // ========================================================

    @Nested
    @DisplayName("アクセス制御")
    class AccessControl {

        @Test
        @DisplayName("認証なしでアクセス → 401")
        void access_unauthenticated_returns401() {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + warehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(createJsonHeaders()), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは入荷伝票を作成できる")
        void create_asStaff_returns201() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = createSlipBody(productId, "PIECE", 50, null, null);

            ResponseEntity<String> response = postJson(BASE_URL, body, staffHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは一覧取得できる")
        void list_asStaff_returns200() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + warehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(staffHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ========================================================
    // ヘルパーメソッド
    // ========================================================

    private String createSlipBody(Long prodId, String unitType, int qty, String lotNumber, String expiryDate) {
        String lotPart = lotNumber != null ? String.format(", \"lotNumber\": \"%s\"", lotNumber) : "";
        String expiryPart = expiryDate != null ? String.format(", \"expiryDate\": \"%s\"", expiryDate) : "";

        return String.format("""
                {
                    "warehouseId": %d,
                    "partnerId": %d,
                    "plannedDate": "%s",
                    "slipType": "NORMAL",
                    "lines": [
                        { "productId": %d, "unitType": "%s", "plannedQty": %d%s%s }
                    ]
                }
                """, warehouseId, partnerId, plannedDate, prodId, unitType, qty, lotPart, expiryPart);
    }

    private Long createTestSlip() throws Exception {
        String body = createSlipBody(productId, "PIECE", 100, null, null);
        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Long createTestSlipMultiLine(Long prodId1, Long prodId2) throws Exception {
        String body = String.format("""
                {
                    "warehouseId": %d,
                    "partnerId": %d,
                    "plannedDate": "%s",
                    "slipType": "NORMAL",
                    "lines": [
                        { "productId": %d, "unitType": "PIECE", "plannedQty": 50 },
                        { "productId": %d, "unitType": "CASE", "plannedQty": 10 }
                    ]
                }
                """, warehouseId, partnerId, plannedDate, prodId1, prodId2);
        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private void confirmSlip(Long slipId) throws Exception {
        ResponseEntity<String> response = postNoBody(BASE_URL + "/" + slipId + "/confirm", adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Long getFirstLineId(Long slipId) throws Exception {
        JsonNode detail = getSlipDetail(slipId);
        return detail.get("lines").get(0).get("id").asLong();
    }

    private JsonNode getSlipDetail(Long slipId) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/" + slipId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parseJson(response.getBody());
    }

    private void inspectLine(Long slipId, Long lineId, int qty) throws Exception {
        String body = String.format("""
                { "lines": [{ "lineId": %d, "inspectedQty": %d }] }
                """, lineId, qty);
        ResponseEntity<String> response = postJson(BASE_URL + "/" + slipId + "/inspect", body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Long createAndCompleteFullFlow() throws Exception {
        Long slipId = createTestSlip();
        confirmSlip(slipId);
        Long lineId = getFirstLineId(slipId);
        inspectLine(slipId, lineId, 100);

        String storeBody = String.format("""
                { "lines": [{ "lineId": %d, "locationId": %d }] }
                """, lineId, inboundLocationId);
        ResponseEntity<String> resp = postJson(BASE_URL + "/" + slipId + "/store", storeBody, adminHeaders);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseJson(resp.getBody()).get("status").asText()).isEqualTo("STORED");
        return slipId;
    }
}
