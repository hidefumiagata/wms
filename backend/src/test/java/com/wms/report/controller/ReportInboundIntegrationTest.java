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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 結合テスト: 入荷系レポート (RPT-01, RPT-03, RPT-04, RPT-05, RPT-06)
 *
 * <p>テスト仕様: docs/test-specifications/TST-RPT-reports.md
 */
@DisplayName("結合テスト: レポート（入荷系）")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportInboundIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String VIEWER_CODE = "viewer01";
    private static final String VIEWER_PASSWORD = "Test@1234";

    private HttpHeaders adminHeaders;
    private HttpHeaders viewerHeaders;

    private Long warehouseId;
    private Long productAmbId;
    private Long productAmb2Id;
    private Long supplierPartnerId;
    private Long adminUserId;

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        productAmbId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);
        productAmb2Id = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
        supplierPartnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'SUP001'", Long.class);
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
        jdbcTemplate.update("DELETE FROM unreceived_list_records");
        jdbcTemplate.update("DELETE FROM inbound_slip_lines");
        jdbcTemplate.update("DELETE FROM inbound_slips");
    }

    // ========== ヘルパー ==========

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<byte[]> getBytes(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    /** 入荷伝票（任意ステータス、明細1件）を投入し ID を返す。 */
    private Long insertInboundSlip(String slipNumber, LocalDate plannedDate, String status,
                                   Long productId, String productCode, String productName,
                                   int plannedQty, Integer inspectedQty, String storedDateTime,
                                   String lineStatus) {
        jdbcTemplate.update("""
                INSERT INTO inbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name,
                    partner_id, partner_code, partner_name, planned_date, status,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (?, 'NORMAL', ?, 'WH001', 'テスト倉庫', ?, 'SUP001', '仕入先A', ?, ?,
                    now(), ?, now(), ?, 0)
                """, slipNumber, warehouseId, supplierPartnerId, plannedDate, status, adminUserId, adminUserId);

        Long slipId = jdbcTemplate.queryForObject(
                "SELECT id FROM inbound_slips WHERE slip_number = ?", Long.class, slipNumber);

        jdbcTemplate.update("""
                INSERT INTO inbound_slip_lines (inbound_slip_id, line_no, product_id, product_code, product_name,
                    unit_type, planned_qty, inspected_qty, line_status, stored_at, created_at, updated_at)
                VALUES (?, 1, ?, ?, ?, 'PIECE', ?, ?, ?, ?::timestamptz, now(), now())
                """, slipId, productId, productCode, productName, plannedQty, inspectedQty, lineStatus,
                storedDateTime);

        return slipId;
    }

    /**
     * unreceived_list_records は inbound_slips への FK を持つため、
     * 親となる入荷伝票を先に投入し、その slipId を返す前提で呼び出す。
     */
    private void insertUnreceivedListRecord(LocalDate batchDate, Long inboundSlipId,
                                            String slipNumber, LocalDate plannedDate,
                                            int plannedQty, String currentStatus) {
        jdbcTemplate.update("""
                INSERT INTO unreceived_list_records (batch_business_date, inbound_slip_id, slip_number,
                    planned_date, warehouse_code, partner_code, partner_name, product_code, product_name,
                    unit_type, planned_qty, current_status, created_at)
                VALUES (?, ?, ?, ?, 'WH001', 'SUP001', '仕入先A', 'AMB-001', 'ミネラルウォーター',
                    'PIECE', ?, ?, now())
                """, batchDate, inboundSlipId, slipNumber, plannedDate, plannedQty, currentStatus);
    }

    private void assertPdfResponse(ResponseEntity<byte[]> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("application/pdf"));
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .isNotNull()
                .contains("attachment")
                .contains(".pdf");
        byte[] body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(4);
        // %PDF magic bytes
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    /** CSV: 200 + Content-Type text/csv + UTF-8 BOM (0xEF 0xBB 0xBF) を検証。 */
    private void assertCsvResponse(ResponseEntity<byte[]> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("text/csv"));
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .isNotNull()
                .contains("attachment")
                .contains(".csv");
        byte[] body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThanOrEqualTo(3);
        // UTF-8 BOM
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
    }

    /** Spring Security フィルタは Controller より前に走るため、伝票投入なしでも 401 を返す。 */
    private HttpHeaders unauthHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    // ========================================================
    // RPT-01: 入荷検品レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/inbound-inspection — RPT-01 入荷検品レポート")
    class InboundInspectionReport {

        @Test
        @DisplayName("正常系: JSON形式で検品データが返却される")
        void getInboundInspection_json_returnsData() throws Exception {
            Long slipId = insertInboundSlip("INB-RPT01-001", LocalDate.of(2026, 3, 20), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 12, 12,
                    "2026-03-20T10:00:00+09:00", "STORED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-inspection?slipId=" + slipId + "&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType()).isNotNull()
                    .satisfies(ct -> assertThat(ct.toString()).contains("application/json"));
            JsonNode body = parseJson(response.getBody());
            assertThat(body.isArray()).isTrue();
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("INB-RPT01-001");
            assertThat(body.get(0).get("productCode").asText()).isEqualTo("AMB-001");
            assertThat(body.get(0).get("plannedQuantityPcs").asInt()).isEqualTo(12);
            assertThat(body.get(0).get("inspectedQuantityPcs").asInt()).isEqualTo(12);
        }

        @Test
        @DisplayName("正常系: PDF形式で200 + Content-Type: application/pdf が返る")
        void getInboundInspection_pdf_returnsBinary() {
            Long slipId = insertInboundSlip("INB-RPT01-002", LocalDate.of(2026, 3, 20), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 12, 10,
                    "2026-03-20T10:00:00+09:00", "STORED");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inbound-inspection?slipId=" + slipId + "&format=pdf",
                    adminHeaders);

            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない伝票IDで404")
        void getInboundInspection_slipNotFound_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-inspection?slipId=99999999&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode body = parseJson(response.getBody());
            assertThat(body.get("code").asText()).isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能")
        void getInboundInspection_viewer_succeeds() {
            Long slipId = insertInboundSlip("INB-RPT01-003", LocalDate.of(2026, 3, 20), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10,
                    "2026-03-20T10:00:00+09:00", "STORED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-inspection?slipId=" + slipId + "&format=json",
                    viewerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("権限: 未認証で401")
        void getInboundInspection_unauthenticated_returns401() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-inspection?slipId=1&format=json", unauthHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getInboundInspection_csv_returnsCsv() {
            Long slipId = insertInboundSlip("INB-RPT01-CSV", LocalDate.of(2026, 3, 20), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10,
                    "2026-03-20T10:00:00+09:00", "STORED");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inbound-inspection?slipId=" + slipId + "&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }

    // ========================================================
    // RPT-03: 入荷予定レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/inbound-plan — RPT-03 入荷予定レポート")
    class InboundPlanReport {

        @Test
        @DisplayName("正常系: 期間指定で対象データが返る")
        void getInboundPlan_dateRange_returnsFilteredData() throws Exception {
            insertInboundSlip("INB-RPT03-001", LocalDate.of(2026, 3, 20), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");
            insertInboundSlip("INB-RPT03-002", LocalDate.of(2026, 3, 25), "CONFIRMED",
                    productAmb2Id, "AMB-002", "緑茶ペットボトル", 5, null, null, "PENDING");
            // out of range
            insertInboundSlip("INB-RPT03-OUT", LocalDate.of(2026, 4, 10), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-plan?warehouseId=" + warehouseId
                            + "&plannedDateFrom=2026-03-20&plannedDateTo=2026-03-31&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(2);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("INB-RPT03-001");
            assertThat(body.get(1).get("slipNumber").asText()).isEqualTo("INB-RPT03-002");
        }

        @Test
        @DisplayName("正常系: ステータス絞り込み")
        void getInboundPlan_statusFilter_returnsOnlyMatching() throws Exception {
            insertInboundSlip("INB-RPT03-S1", LocalDate.of(2026, 3, 20), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");
            insertInboundSlip("INB-RPT03-S2", LocalDate.of(2026, 3, 21), "CONFIRMED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-plan?warehouseId=" + warehouseId
                            + "&status=PLANNED&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("status").asText()).isEqualTo("PLANNED");
        }

        @Test
        @DisplayName("正常系: データ0件で空配列")
        void getInboundPlan_noData_returnsEmptyArray() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-plan?warehouseId=" + warehouseId + "&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body.isArray()).isTrue();
            assertThat(body).isEmpty();
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getInboundPlan_pdf_returnsBinary() {
            insertInboundSlip("INB-RPT03-PDF", LocalDate.of(2026, 3, 20), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inbound-plan?warehouseId=" + warehouseId + "&format=pdf",
                    adminHeaders);

            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getInboundPlan_warehouseNotFound_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-plan?warehouseId=99999999&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(parseJson(response.getBody()).get("code").asText())
                    .isEqualTo("WAREHOUSE_NOT_FOUND");
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能")
        void getInboundPlan_viewer_succeeds() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-plan?warehouseId=" + warehouseId + "&format=json",
                    viewerHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("権限: 未認証で401")
        void getInboundPlan_unauthenticated_returns401() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-plan?warehouseId=" + warehouseId + "&format=json",
                    unauthHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getInboundPlan_csv_returnsCsv() {
            insertInboundSlip("INB-RPT03-CSV", LocalDate.of(2026, 3, 20), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inbound-plan?warehouseId=" + warehouseId + "&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }

    // ========================================================
    // RPT-04: 入庫実績レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/inbound-result — RPT-04 入庫実績レポート")
    class InboundResultReport {

        @Test
        @DisplayName("正常系: 入庫済データが返る")
        void getInboundResult_storedData_returnsItems() throws Exception {
            insertInboundSlip("INB-RPT04-001", LocalDate.of(2026, 3, 20), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 12, 12,
                    "2026-03-20T10:00:00+09:00", "STORED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-result?warehouseId=" + warehouseId
                            + "&storedDateFrom=2026-03-20&storedDateTo=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("INB-RPT04-001");
            assertThat(body.get(0).get("storedDate").asText()).isEqualTo("2026-03-20");
        }

        @Test
        @DisplayName("正常系: 期間外のデータは含まれない")
        void getInboundResult_outOfRange_excluded() throws Exception {
            insertInboundSlip("INB-RPT04-IN", LocalDate.of(2026, 3, 20), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10,
                    "2026-03-20T10:00:00+09:00", "STORED");
            insertInboundSlip("INB-RPT04-OUT", LocalDate.of(2026, 3, 25), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10,
                    "2026-04-01T10:00:00+09:00", "STORED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-result?warehouseId=" + warehouseId
                            + "&storedDateFrom=2026-03-20&storedDateTo=2026-03-20&format=json",
                    adminHeaders);

            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("INB-RPT04-IN");
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getInboundResult_pdf_returnsBinary() {
            insertInboundSlip("INB-RPT04-PDF", LocalDate.of(2026, 3, 20), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10,
                    "2026-03-20T10:00:00+09:00", "STORED");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inbound-result?warehouseId=" + warehouseId + "&format=pdf",
                    adminHeaders);

            assertPdfResponse(response);
        }

        @Test
        @DisplayName("正常系: データ0件で空配列")
        void getInboundResult_noData_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-result?warehouseId=" + warehouseId + "&format=json",
                    adminHeaders);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).isEmpty();
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getInboundResult_warehouseNotFound_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/inbound-result?warehouseId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能 / 未認証で401")
        void getInboundResult_authChecks() {
            assertThat(get(
                    "/api/v1/reports/inbound-result?warehouseId=" + warehouseId + "&format=json",
                    viewerHeaders).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(
                    "/api/v1/reports/inbound-result?warehouseId=" + warehouseId + "&format=json",
                    unauthHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getInboundResult_csv_returnsCsv() {
            insertInboundSlip("INB-RPT04-CSV", LocalDate.of(2026, 3, 20), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10,
                    "2026-03-20T10:00:00+09:00", "STORED");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/inbound-result?warehouseId=" + warehouseId + "&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }

    // ========================================================
    // RPT-05: 未入荷リスト（リアルタイム）
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/unreceived-realtime — RPT-05 未入荷リスト（RT）")
    class UnreceivedRealtimeReport {

        @Test
        @DisplayName("正常系: 入庫未完了の伝票が返る")
        void getUnreceivedRealtime_pendingSlips_returnsItems() throws Exception {
            insertInboundSlip("INB-RPT05-001", LocalDate.of(2026, 3, 19), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");
            // STORED は除外
            insertInboundSlip("INB-RPT05-EX", LocalDate.of(2026, 3, 19), "STORED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10,
                    "2026-03-19T10:00:00+09:00", "STORED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/unreceived-realtime?warehouseId=" + warehouseId
                            + "&asOfDate=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("INB-RPT05-001");
            assertThat(body.get(0).get("delayDays").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getUnreceivedRealtime_pdf_returnsBinary() {
            insertInboundSlip("INB-RPT05-PDF", LocalDate.of(2026, 3, 19), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/unreceived-realtime?warehouseId=" + warehouseId
                            + "&asOfDate=2026-03-20&format=pdf",
                    adminHeaders);

            assertPdfResponse(response);
        }

        @Test
        @DisplayName("正常系: データ0件で空配列")
        void getUnreceivedRealtime_noData_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/unreceived-realtime?warehouseId=" + warehouseId
                            + "&asOfDate=2026-03-20&format=json",
                    adminHeaders);
            assertThat(parseJson(response.getBody())).isEmpty();
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getUnreceivedRealtime_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/unreceived-realtime?warehouseId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能 / 未認証で401")
        void getUnreceivedRealtime_authChecks() {
            String url = "/api/v1/reports/unreceived-realtime?warehouseId=" + warehouseId
                    + "&asOfDate=2026-03-20&format=json";
            assertThat(get(url, viewerHeaders).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(url, unauthHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getUnreceivedRealtime_csv_returnsCsv() {
            insertInboundSlip("INB-RPT05-CSV", LocalDate.of(2026, 3, 19), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/unreceived-realtime?warehouseId=" + warehouseId
                            + "&asOfDate=2026-03-20&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }

    // ========================================================
    // RPT-06: 未入荷リスト（確定）
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/unreceived-confirmed — RPT-06 未入荷リスト（確定）")
    class UnreceivedConfirmedReport {

        @Test
        @DisplayName("正常系: 確定済リストが返る")
        void getUnreceivedConfirmed_records_returnsItems() throws Exception {
            Long slipId = insertInboundSlip("INB-RPT06-001", LocalDate.of(2026, 3, 19), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");
            insertUnreceivedListRecord(LocalDate.of(2026, 3, 20), slipId, "INB-RPT06-001",
                    LocalDate.of(2026, 3, 19), 10, "PLANNED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/unreceived-confirmed?warehouseId=" + warehouseId
                            + "&batchBusinessDate=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("INB-RPT06-001");
            assertThat(body.get(0).get("batchBusinessDate").asText()).isEqualTo("2026-03-20");
        }

        @Test
        @DisplayName("正常系: 日替未実行の営業日で空配列")
        void getUnreceivedConfirmed_noBatch_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/unreceived-confirmed?warehouseId=" + warehouseId
                            + "&batchBusinessDate=2026-03-30&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parseJson(response.getBody())).isEmpty();
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getUnreceivedConfirmed_pdf_returnsBinary() {
            Long slipId = insertInboundSlip("INB-RPT06-PDF", LocalDate.of(2026, 3, 19), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");
            insertUnreceivedListRecord(LocalDate.of(2026, 3, 20), slipId, "INB-RPT06-PDF",
                    LocalDate.of(2026, 3, 19), 10, "PLANNED");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/unreceived-confirmed?warehouseId=" + warehouseId
                            + "&batchBusinessDate=2026-03-20&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getUnreceivedConfirmed_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/unreceived-confirmed?warehouseId=99999999"
                            + "&batchBusinessDate=2026-03-20&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能 / 未認証で401")
        void getUnreceivedConfirmed_authChecks() {
            String url = "/api/v1/reports/unreceived-confirmed?warehouseId=" + warehouseId
                    + "&batchBusinessDate=2026-03-20&format=json";
            assertThat(get(url, viewerHeaders).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(url, unauthHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getUnreceivedConfirmed_csv_returnsCsv() {
            Long slipId = insertInboundSlip("INB-RPT06-CSV", LocalDate.of(2026, 3, 19), "PLANNED",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, null, "PENDING");
            insertUnreceivedListRecord(LocalDate.of(2026, 3, 20), slipId, "INB-RPT06-CSV",
                    LocalDate.of(2026, 3, 19), 10, "PLANNED");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/unreceived-confirmed?warehouseId=" + warehouseId
                            + "&batchBusinessDate=2026-03-20&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }
}
