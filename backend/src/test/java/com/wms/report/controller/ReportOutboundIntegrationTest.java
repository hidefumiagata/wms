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
 * 結合テスト: 出荷系レポート (RPT-12, RPT-13, RPT-14, RPT-15, RPT-16)
 *
 * <p>テスト仕様: docs/test-specifications/TST-RPT-reports.md
 */
@DisplayName("結合テスト: レポート（出荷系）")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportOutboundIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String VIEWER_CODE = "viewer01";
    private static final String VIEWER_PASSWORD = "Test@1234";

    private HttpHeaders adminHeaders;
    private HttpHeaders viewerHeaders;

    private Long warehouseId;
    private Long productAmbId;
    private Long productAmb2Id;
    private Long locA01_01_01_01;
    private Long customerPartnerId;
    private Long adminUserId;

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        productAmbId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);
        productAmb2Id = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'AMB-002'", Long.class);
        locA01_01_01_01 = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE location_code = 'A-01-A01-01-01-01' AND warehouse_id = ?",
                Long.class, warehouseId);
        customerPartnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'CUS001'", Long.class);
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
        jdbcTemplate.update("DELETE FROM unshipped_list_records");
        jdbcTemplate.update("DELETE FROM picking_instruction_lines");
        jdbcTemplate.update("DELETE FROM picking_instructions");
        jdbcTemplate.update("DELETE FROM outbound_slip_lines");
        jdbcTemplate.update("DELETE FROM outbound_slips");
    }

    // ========== ヘルパー ==========

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<byte[]> getBytes(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    /** 出荷伝票（明細1件付き）を投入し slipId を返す。 */
    private Long insertOutboundSlip(String slipNumber, LocalDate plannedDate, String status,
                                     String carrier, String trackingNumber,
                                     Long productId, String productCode, String productName,
                                     int orderedQty, Integer inspectedQty, int shippedQty,
                                     String lineStatus) {
        jdbcTemplate.update("""
                INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name,
                    partner_id, partner_code, partner_name, planned_date, carrier, tracking_number, status,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (?, 'NORMAL', ?, 'WH001', 'テスト倉庫',
                    ?, 'CUS001', '得意先A', ?, ?, ?, ?,
                    now(), ?, now(), ?, 0)
                """, slipNumber, warehouseId, customerPartnerId, plannedDate,
                carrier, trackingNumber, status, adminUserId, adminUserId);

        Long slipId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slips WHERE slip_number = ?", Long.class, slipNumber);

        jdbcTemplate.update("""
                INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code, product_name,
                    unit_type, ordered_qty, inspected_qty, shipped_qty, line_status, created_at, updated_at)
                VALUES (?, 1, ?, ?, ?, 'PIECE', ?, ?, ?, ?, now(), now())
                """, slipId, productId, productCode, productName, orderedQty, inspectedQty, shippedQty,
                lineStatus);

        return slipId;
    }

    /** ピッキング指示と明細（出荷伝票明細1件分）を投入し pickingInstructionId を返す。 */
    private Long insertPickingInstruction(String number, Long outboundSlipId,
                                          int qtyToPick, int qtyPicked) {
        Long outboundSlipLineId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = 1",
                Long.class, outboundSlipId);

        jdbcTemplate.update("""
                INSERT INTO picking_instructions (instruction_number, warehouse_id, status,
                    created_at, created_by)
                VALUES (?, ?, 'COMPLETED', now(), ?)
                """, number, warehouseId, adminUserId);

        Long instructionId = jdbcTemplate.queryForObject(
                "SELECT id FROM picking_instructions WHERE instruction_number = ?",
                Long.class, number);

        jdbcTemplate.update("""
                INSERT INTO picking_instruction_lines (picking_instruction_id, line_no,
                    outbound_slip_line_id, location_id, location_code,
                    product_id, product_code, product_name, unit_type,
                    qty_to_pick, qty_picked, line_status, created_at, updated_at)
                VALUES (?, 1, ?, ?, 'A-01-A01-01-01-01', ?, 'AMB-001', 'ミネラルウォーター',
                    'PIECE', ?, ?, 'COMPLETED', now(), now())
                """, instructionId, outboundSlipLineId, locA01_01_01_01, productAmbId,
                qtyToPick, qtyPicked);

        return instructionId;
    }

    private void insertUnshippedListRecord(LocalDate batchDate, Long outboundSlipId,
                                           String slipNumber, LocalDate plannedDate,
                                           int orderedQty, String currentStatus) {
        jdbcTemplate.update("""
                INSERT INTO unshipped_list_records (batch_business_date, outbound_slip_id, slip_number,
                    planned_date, warehouse_code, partner_code, partner_name,
                    product_code, product_name, unit_type, ordered_qty, current_status, created_at)
                VALUES (?, ?, ?, ?, 'WH001', 'CUS001', '得意先A',
                    'AMB-001', 'ミネラルウォーター', 'PIECE', ?, ?, now())
                """, batchDate, outboundSlipId, slipNumber, plannedDate, orderedQty, currentStatus);
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

    private void assertCsvResponse(ResponseEntity<byte[]> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("text/csv"));
        byte[] body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThanOrEqualTo(3);
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
    }

    private HttpHeaders unauthHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON));
        return h;
    }

    // ========================================================
    // RPT-12: ピッキング指示書
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/picking-instruction — RPT-12 ピッキング指示書")
    class PickingInstructionReport {

        @Test
        @DisplayName("正常系: ピッキング指示の明細データが返る")
        void getPicking_data_returnsItems() throws Exception {
            Long slipId = insertOutboundSlip("OUT-RPT12-001", LocalDate.of(2026, 3, 20),
                    "PICKING_COMPLETED", null, null,
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "PICKING_COMPLETED");
            Long instructionId = insertPickingInstruction("PI-RPT12-001", slipId, 10, 10);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/picking-instruction?pickingInstructionId=" + instructionId
                            + "&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("locationCode").asText()).isEqualTo("A-01-A01-01-01-01");
            assertThat(body.get(0).get("instructedQuantity").asInt()).isEqualTo(10);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getPicking_pdf_returnsBinary() {
            Long slipId = insertOutboundSlip("OUT-RPT12-PDF", LocalDate.of(2026, 3, 20),
                    "PICKING_COMPLETED", null, null,
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "PICKING_COMPLETED");
            Long instructionId = insertPickingInstruction("PI-RPT12-PDF", slipId, 10, 10);

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/picking-instruction?pickingInstructionId=" + instructionId
                            + "&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しないピッキング指示IDで404")
        void getPicking_notFound_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/picking-instruction?pickingInstructionId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(parseJson(response.getBody()).get("code").asText())
                    .isEqualTo("PICKING_NOT_FOUND");
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能")
        void getPicking_viewer_succeeds() {
            Long slipId = insertOutboundSlip("OUT-RPT12-VIEWER", LocalDate.of(2026, 3, 20),
                    "PICKING_COMPLETED", null, null,
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "PICKING_COMPLETED");
            Long instructionId = insertPickingInstruction("PI-RPT12-VIEWER", slipId, 10, 10);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/picking-instruction?pickingInstructionId=" + instructionId
                            + "&format=json",
                    viewerHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("権限: 未認証で401")
        void getPicking_unauthenticated_returns401() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/picking-instruction?pickingInstructionId=1&format=json",
                    unauthHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getPicking_csv_returnsCsv() {
            Long slipId = insertOutboundSlip("OUT-RPT12-CSV", LocalDate.of(2026, 3, 20),
                    "PICKING_COMPLETED", null, null,
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "PICKING_COMPLETED");
            Long instructionId = insertPickingInstruction("PI-RPT12-CSV", slipId, 10, 10);
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/picking-instruction?pickingInstructionId=" + instructionId
                            + "&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }

    // ========================================================
    // RPT-13: 出荷検品レポート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/shipping-inspection — RPT-13 出荷検品レポート")
    class ShippingInspectionReport {

        @Test
        @DisplayName("正常系: 検品データが返る")
        void getShippingInspection_data_returnsItems() throws Exception {
            Long slipId = insertOutboundSlip("OUT-RPT13-001", LocalDate.of(2026, 3, 20),
                    "INSPECTING", null, null,
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10, 0, "PICKING_COMPLETED");
            insertPickingInstruction("PI-RPT13-001", slipId, 10, 10);

            ResponseEntity<String> response = get(
                    "/api/v1/reports/shipping-inspection?slipId=" + slipId + "&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("OUT-RPT13-001");
            assertThat(body.get(0).get("pickedQuantity").asInt()).isEqualTo(10);
            assertThat(body.get(0).get("inspectedQuantity").asInt()).isEqualTo(10);
            assertThat(body.get(0).get("diffQuantity").asInt()).isEqualTo(0);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getShippingInspection_pdf_returnsBinary() {
            Long slipId = insertOutboundSlip("OUT-RPT13-PDF", LocalDate.of(2026, 3, 20),
                    "INSPECTING", null, null,
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10, 0, "PICKING_COMPLETED");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/shipping-inspection?slipId=" + slipId + "&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない伝票IDで404")
        void getShippingInspection_notFound_returns404() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/shipping-inspection?slipId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(parseJson(response.getBody()).get("code").asText())
                    .isEqualTo("OUTBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能 / 未認証で401")
        void getShippingInspection_authChecks() {
            Long slipId = insertOutboundSlip("OUT-RPT13-AUTH", LocalDate.of(2026, 3, 20),
                    "INSPECTING", null, null,
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10, 0, "PICKING_COMPLETED");
            String url = "/api/v1/reports/shipping-inspection?slipId=" + slipId + "&format=json";
            assertThat(get(url, viewerHeaders).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(url, unauthHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getShippingInspection_csv_returnsCsv() {
            Long slipId = insertOutboundSlip("OUT-RPT13-CSV", LocalDate.of(2026, 3, 20),
                    "INSPECTING", null, null,
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, 10, 0, "PICKING_COMPLETED");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/shipping-inspection?slipId=" + slipId + "&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }

    // ========================================================
    // RPT-14: 配送リスト
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/delivery-list — RPT-14 配送リスト")
    class DeliveryListReport {

        @Test
        @DisplayName("正常系: 期間指定で配送データが返る")
        void getDeliveryList_data_returnsItems() throws Exception {
            insertOutboundSlip("OUT-RPT14-001", LocalDate.of(2026, 3, 20), "ALLOCATED",
                    "ヤマト運輸", "TRACK-001",
                    productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/delivery-list?warehouseId=" + warehouseId
                            + "&plannedDateFrom=2026-03-20&plannedDateTo=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("OUT-RPT14-001");
            assertThat(body.get(0).get("carrier").asText()).isEqualTo("ヤマト運輸");
            assertThat(body.get(0).get("trackingNumber").asText()).isEqualTo("TRACK-001");
            assertThat(body.get(0).get("lines").isArray()).isTrue();
            assertThat(body.get(0).get("lines")).hasSize(1);
        }

        @Test
        @DisplayName("正常系: ステータス絞り込み")
        void getDeliveryList_statusFilter_returnsFiltered() throws Exception {
            insertOutboundSlip("OUT-RPT14-A", LocalDate.of(2026, 3, 20), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");
            insertOutboundSlip("OUT-RPT14-S", LocalDate.of(2026, 3, 20), "SHIPPED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 10, "SHIPPED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/delivery-list?warehouseId=" + warehouseId
                            + "&status=SHIPPED&format=json",
                    adminHeaders);

            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("status").asText()).isEqualTo("SHIPPED");
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getDeliveryList_pdf_returnsBinary() {
            insertOutboundSlip("OUT-RPT14-PDF", LocalDate.of(2026, 3, 20), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/delivery-list?warehouseId=" + warehouseId + "&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("正常系: データ0件で空配列")
        void getDeliveryList_noData_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/delivery-list?warehouseId=" + warehouseId + "&format=json",
                    adminHeaders);
            assertThat(parseJson(response.getBody())).isEmpty();
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getDeliveryList_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/delivery-list?warehouseId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能 / 未認証で401")
        void getDeliveryList_authChecks() {
            String url = "/api/v1/reports/delivery-list?warehouseId=" + warehouseId + "&format=json";
            assertThat(get(url, viewerHeaders).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(url, unauthHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getDeliveryList_csv_returnsCsv() {
            insertOutboundSlip("OUT-RPT14-CSV", LocalDate.of(2026, 3, 20), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/delivery-list?warehouseId=" + warehouseId + "&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }

    // ========================================================
    // RPT-15: 未出荷リスト（リアルタイム）
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/unshipped-realtime — RPT-15 未出荷リスト（RT）")
    class UnshippedRealtimeReport {

        @Test
        @DisplayName("正常系: 未出荷の伝票が返り、SHIPPEDは除外される")
        void getUnshippedRealtime_data_returnsItems() throws Exception {
            insertOutboundSlip("OUT-RPT15-001", LocalDate.of(2026, 3, 19), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");
            // SHIPPED は除外
            insertOutboundSlip("OUT-RPT15-EX", LocalDate.of(2026, 3, 19), "SHIPPED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 10, "SHIPPED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/unshipped-realtime?warehouseId=" + warehouseId
                            + "&asOfDate=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("OUT-RPT15-001");
            assertThat(body.get(0).get("delayDays").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getUnshippedRealtime_pdf_returnsBinary() {
            insertOutboundSlip("OUT-RPT15-PDF", LocalDate.of(2026, 3, 19), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/unshipped-realtime?warehouseId=" + warehouseId
                            + "&asOfDate=2026-03-20&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getUnshippedRealtime_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/unshipped-realtime?warehouseId=99999999&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("正常系: データ0件で空配列")
        void getUnshippedRealtime_noData_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/unshipped-realtime?warehouseId=" + warehouseId
                            + "&asOfDate=2026-03-20&format=json",
                    adminHeaders);
            assertThat(parseJson(response.getBody())).isEmpty();
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能 / 未認証で401")
        void getUnshippedRealtime_authChecks() {
            String url = "/api/v1/reports/unshipped-realtime?warehouseId=" + warehouseId
                    + "&asOfDate=2026-03-20&format=json";
            assertThat(get(url, viewerHeaders).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(url, unauthHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getUnshippedRealtime_csv_returnsCsv() {
            insertOutboundSlip("OUT-RPT15-CSV", LocalDate.of(2026, 3, 19), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/unshipped-realtime?warehouseId=" + warehouseId
                            + "&asOfDate=2026-03-20&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }

    // ========================================================
    // RPT-16: 未出荷リスト（確定）
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/reports/unshipped-confirmed — RPT-16 未出荷リスト（確定）")
    class UnshippedConfirmedReport {

        @Test
        @DisplayName("正常系: 確定済リストが返る")
        void getUnshippedConfirmed_data_returnsItems() throws Exception {
            Long slipId = insertOutboundSlip("OUT-RPT16-001", LocalDate.of(2026, 3, 19), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");
            insertUnshippedListRecord(LocalDate.of(2026, 3, 20), slipId, "OUT-RPT16-001",
                    LocalDate.of(2026, 3, 19), 10, "ALLOCATED");

            ResponseEntity<String> response = get(
                    "/api/v1/reports/unshipped-confirmed?warehouseId=" + warehouseId
                            + "&batchBusinessDate=2026-03-20&format=json",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("slipNumber").asText()).isEqualTo("OUT-RPT16-001");
            assertThat(body.get(0).get("batchBusinessDate").asText()).isEqualTo("2026-03-20");
        }

        @Test
        @DisplayName("正常系: 該当バッチデータなしで空配列")
        void getUnshippedConfirmed_noData_returnsEmpty() throws Exception {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/unshipped-confirmed?warehouseId=" + warehouseId
                            + "&batchBusinessDate=2026-03-30&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parseJson(response.getBody())).isEmpty();
        }

        @Test
        @DisplayName("正常系: PDF形式で200")
        void getUnshippedConfirmed_pdf_returnsBinary() {
            Long slipId = insertOutboundSlip("OUT-RPT16-PDF", LocalDate.of(2026, 3, 19), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");
            insertUnshippedListRecord(LocalDate.of(2026, 3, 20), slipId, "OUT-RPT16-PDF",
                    LocalDate.of(2026, 3, 19), 10, "ALLOCATED");

            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/unshipped-confirmed?warehouseId=" + warehouseId
                            + "&batchBusinessDate=2026-03-20&format=pdf",
                    adminHeaders);
            assertPdfResponse(response);
        }

        @Test
        @DisplayName("異常系: 存在しない倉庫IDで404")
        void getUnshippedConfirmed_warehouseNotFound_returns404() {
            ResponseEntity<String> response = get(
                    "/api/v1/reports/unshipped-confirmed?warehouseId=99999999"
                            + "&batchBusinessDate=2026-03-20&format=json",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("権限: VIEWERでも閲覧可能 / 未認証で401")
        void getUnshippedConfirmed_authChecks() {
            String url = "/api/v1/reports/unshipped-confirmed?warehouseId=" + warehouseId
                    + "&batchBusinessDate=2026-03-20&format=json";
            assertThat(get(url, viewerHeaders).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(url, unauthHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常系: CSV形式で200 + UTF-8 BOM")
        void getUnshippedConfirmed_csv_returnsCsv() {
            Long slipId = insertOutboundSlip("OUT-RPT16-CSV", LocalDate.of(2026, 3, 19), "ALLOCATED",
                    null, null, productAmbId, "AMB-001", "ミネラルウォーター", 10, null, 0, "ALLOCATED");
            insertUnshippedListRecord(LocalDate.of(2026, 3, 20), slipId, "OUT-RPT16-CSV",
                    LocalDate.of(2026, 3, 19), 10, "ALLOCATED");
            ResponseEntity<byte[]> response = getBytes(
                    "/api/v1/reports/unshipped-confirmed?warehouseId=" + warehouseId
                            + "&batchBusinessDate=2026-03-20&format=csv",
                    adminHeaders);
            assertCsvResponse(response);
        }
    }
}
