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
 * 結合テスト: 日次集計・返品レポート (RPT-17, RPT-18)
 *
 * <p>テスト仕様: docs/test-specifications/TST-RPT-reports.md
 */
@DisplayName("結合テスト: レポート（日次集計・返品）")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportMiscIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String VIEWER_CODE = "viewer01";
    private static final String VIEWER_PASSWORD = "Test@1234";

    private HttpHeaders adminHeaders;
    private HttpHeaders viewerHeaders;

    private Long warehouseId;
    private Long productAmbId;
    private Long supplierPartnerId;
    private Long adminUserId;

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        productAmbId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);
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
        jdbcTemplate.update("DELETE FROM daily_summary_records");
        jdbcTemplate.update("DELETE FROM batch_execution_logs");
        jdbcTemplate.update("DELETE FROM return_slips");
    }

    // ========== ヘルパー ==========

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<byte[]> getBytes(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    /** バッチ実行ログ（SUCCESS）を投入。 */
    private void insertBatchLogSuccess(LocalDate businessDate) {
        jdbcTemplate.update("""
                INSERT INTO batch_execution_logs (target_business_date, status,
                    step1_status, step2_status, step3_status, step4_status, step5_status, step6_status,
                    started_at, completed_at, executed_by)
                VALUES (?, 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS',
                    now(), now(), ?)
                """, businessDate, adminUserId);
    }

    /** 日次集計レコードを投入。 */
    private void insertDailySummary(LocalDate businessDate, int inboundCount, int outboundCount,
                                     int unreceivedCount, int unshippedCount) {
        jdbcTemplate.update("""
                INSERT INTO daily_summary_records (business_date, warehouse_id, warehouse_code,
                    inbound_count, inbound_line_count, inbound_quantity_total,
                    outbound_count, outbound_line_count, outbound_quantity_total,
                    return_count, return_quantity_total,
                    inventory_quantity_total, unreceived_count, unshipped_count, created_at)
                VALUES (?, ?, 'WH001',
                    ?, ?, ?,
                    ?, ?, ?,
                    0, 0,
                    1000, ?, ?, now())
                """, businessDate, warehouseId,
                inboundCount, inboundCount, inboundCount * 10L,
                outboundCount, outboundCount, outboundCount * 5L,
                unreceivedCount, unshippedCount);
    }

    /** 返品伝票を投入。 */
    private void insertReturnSlip(String returnNumber, String returnType,
                                   LocalDate returnDate, int quantity,
                                   String returnReason) {
        jdbcTemplate.update("""
                INSERT INTO return_slips (return_number, return_type, status,
                    warehouse_id, warehouse_code, warehouse_name,
                    partner_id, partner_code, partner_name,
                    product_id, product_code, product_name,
                    quantity, unit_type, return_reason, return_date,
                    created_by, created_at, updated_by, updated_at, version)
                VALUES (?, ?, 'COMPLETED',
                    ?, 'WH001', 'テスト倉庫',
                    ?, 'SUP001', '仕入先A',
                    ?, 'AMB-001', 'ミネラルウォーター',
                    ?, 'PIECE', ?, ?,
                    ?, now(), ?, now(), 0)
                """, returnNumber, returnType, warehouseId, supplierPartnerId, productAmbId,
                quantity, returnReason, returnDate, adminUserId, adminUserId);
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
    // RPT-17: 日次集計レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/daily-summary — RPT-17 日次集計")
    class DailySummaryReport {

        @Test
        @DisplayName("正常系: 日替処理完了済の営業日でデータが返る")
        void getDailySummary_data_returnsItems() throws Exception {
            LocalDate businessDate = LocalDate.of(2026, 3, 20);
            insertBatchLogSuccess(businessDate);
            insertDailySummary(businessDate, 5, 3, 1, 2);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/daily-summary?targetBusinessDate=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("warehouseId").asLong()).isEqualTo(warehouseId);
            assertThat(body.get(0).get("inboundCount").asInt()).isEqualTo(5);
            assertThat(body.get(0).get("outboundCount").asInt()).isEqualTo(3);
            assertThat(body.get(0).get("unreceivedCount").asInt()).isEqualTo(1);
            assertThat(body.get(0).get("unshippedCount").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getDailySummary_pdf_returnsBinary() {
            LocalDate businessDate = LocalDate.of(2026, 3, 20);
            insertBatchLogSuccess(businessDate);
            insertDailySummary(businessDate, 5, 3, 0, 0);

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/daily-summary?targetBusinessDate=2026-03-20&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 日替処理未完了の営業日で404")
        void getDailySummary_batchNotCompleted_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/daily-summary?targetBusinessDate=2026-03-30&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(parseJson(response.getBody()).get("code").asText())
                    .isEqualTo("BATCH_EXECUTION_NOT_FOUND");
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能")
        void getDailySummary_viewer_succeeds() {
            LocalDate businessDate = LocalDate.of(2026, 3, 20);
            insertBatchLogSuccess(businessDate);
            insertDailySummary(businessDate, 1, 1, 0, 0);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/daily-summary?targetBusinessDate=2026-03-20&format=json",
                    viewerHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ========================================================
    // RPT-18: 返品レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/returns — RPT-18 返品レポート")
    class ReturnsReport {

        @Test
        @DisplayName("正常系: 返品データが返る")
        void getReturns_data_returnsItems() throws Exception {
            insertReturnSlip("RTN-RPT18-001", "INBOUND", LocalDate.of(2026, 3, 20),
                    5, "QUALITY_DEFECT");
            insertReturnSlip("RTN-RPT18-002", "OUTBOUND", LocalDate.of(2026, 3, 21),
                    3, "DAMAGED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/returns?warehouseId=" + warehouseId
                            + "&returnDateFrom=2026-03-20&returnDateTo=2026-03-21&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(2);
            // 返品種別昇順 (INBOUND → OUTBOUND)
            assertThat(body.get(0).get("returnType").asText()).isEqualTo("INBOUND");
            assertThat(body.get(1).get("returnType").asText()).isEqualTo("OUTBOUND");
        }

        @Test
        @DisplayName("正常系: returnTypeで絞り込み")
        void getReturns_returnTypeFilter_returnsFiltered() throws Exception {
            insertReturnSlip("RTN-RPT18-IN", "INBOUND", LocalDate.of(2026, 3, 20),
                    5, "QUALITY_DEFECT");
            insertReturnSlip("RTN-RPT18-OUT", "OUTBOUND", LocalDate.of(2026, 3, 20),
                    3, "DAMAGED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/returns?warehouseId=" + warehouseId
                            + "&returnType=INBOUND&format=json",
                    adminHeaders);

            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("returnType").asText()).isEqualTo("INBOUND");
        }

        @Test
        @DisplayName("正常系: returnReasonで絞り込み")
        void getReturns_reasonFilter_returnsFiltered() throws Exception {
            insertReturnSlip("RTN-RPT18-Q", "INBOUND", LocalDate.of(2026, 3, 20),
                    5, "QUALITY_DEFECT");
            insertReturnSlip("RTN-RPT18-D", "INBOUND", LocalDate.of(2026, 3, 20),
                    3, "DAMAGED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/returns?warehouseId=" + warehouseId
                            + "&returnReason=DAMAGED&format=json",
                    adminHeaders);

            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("returnReason").asText()).isEqualTo("DAMAGED");
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getReturns_pdf_returnsBinary() {
            insertReturnSlip("RTN-RPT18-PDF", "INBOUND", LocalDate.of(2026, 3, 20),
                    5, "QUALITY_DEFECT");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/returns?warehouseId=" + warehouseId + "&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("正常系: データ0件で空配列")
        void getReturns_noData_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/returns?warehouseId=" + warehouseId + "&format=json",
                    adminHeaders);
            assertThat(parseJson(response.getBody())).isEmpty();
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getReturns_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/returns?warehouseId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
