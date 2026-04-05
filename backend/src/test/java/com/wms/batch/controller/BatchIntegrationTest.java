package com.wms.batch.controller;

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

@DisplayName("結合テスト: バッチ管理")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchIntegrationTest extends IntegrationTestBase {

    private static final String DAILY_CLOSE_URL = "/api/v1/batch/daily-close";
    private static final String EXECUTIONS_URL = "/api/v1/batch/executions";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String VIEWER_CODE = "viewer01";
    private static final String VIEWER_PASSWORD = "Test@1234";

    private HttpHeaders adminHeaders;
    private Long warehouseId;
    private Long productAmbId;
    private Long productAmb2Id;
    private Long locA01_01_01_01;
    private Long supplierPartnerId;
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
        supplierPartnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'SUP001'", Long.class);
        customerPartnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM partners WHERE partner_code = 'CUS001'", Long.class);
        adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);
    }

    @BeforeAll
    void initAuth() {
        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    @BeforeEach
    void setUp() {
        // Clean up batch-related tables (dependency order)
        jdbcTemplate.update("DELETE FROM daily_summary_records");
        jdbcTemplate.update("DELETE FROM unreceived_list_records");
        jdbcTemplate.update("DELETE FROM unshipped_list_records");
        jdbcTemplate.update("DELETE FROM inventory_snapshots");
        jdbcTemplate.update("DELETE FROM inbound_summaries");
        jdbcTemplate.update("DELETE FROM outbound_summaries");
        jdbcTemplate.update("DELETE FROM batch_execution_logs");
        jdbcTemplate.update("DELETE FROM inbound_slips_backup");
        jdbcTemplate.update("DELETE FROM inbound_slip_lines_backup");
        jdbcTemplate.update("DELETE FROM outbound_slips_backup");
        jdbcTemplate.update("DELETE FROM outbound_slip_lines_backup");
        jdbcTemplate.update("DELETE FROM inventory_movements_backup");
        jdbcTemplate.update("DELETE FROM inventory_movements");
        jdbcTemplate.update("DELETE FROM return_slips");
        jdbcTemplate.update("DELETE FROM allocation_details");
        jdbcTemplate.update("DELETE FROM unpack_instructions");
        jdbcTemplate.update("DELETE FROM picking_instruction_lines");
        jdbcTemplate.update("DELETE FROM picking_instructions");
        jdbcTemplate.update("DELETE FROM outbound_slip_lines");
        jdbcTemplate.update("DELETE FROM outbound_slips");
        jdbcTemplate.update("DELETE FROM inbound_slip_lines");
        jdbcTemplate.update("DELETE FROM inbound_slips");
        jdbcTemplate.update("DELETE FROM inventories");
    }

    // ========== ヘルパーメソッド ==========

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void setBusinessDate(LocalDate date) {
        jdbcTemplate.update("UPDATE business_date SET current_business_date = ? WHERE id = 1", date);
    }

    private LocalDate getBusinessDate() {
        return jdbcTemplate.queryForObject(
                "SELECT current_business_date FROM business_date WHERE id = 1", LocalDate.class);
    }

    private String dailyCloseBody(LocalDate targetDate) {
        return String.format("{\"targetBusinessDate\":\"%s\"}", targetDate);
    }

    private void insertInboundSlipStored(String slipNumber, LocalDate plannedDate, String storedDate) {
        jdbcTemplate.update("""
                INSERT INTO inbound_slips (slip_number, warehouse_id, warehouse_code, warehouse_name,
                    partner_id, partner_code, partner_name, planned_date, status,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, 'WH001', 'テスト倉庫', ?, 'SUP001', '仕入先A', ?, 'STORED',
                    now(), ?, now(), ?, 0)
                """, slipNumber, warehouseId, supplierPartnerId, plannedDate, adminUserId, adminUserId);

        Long slipId = jdbcTemplate.queryForObject(
                "SELECT id FROM inbound_slips WHERE slip_number = ?", Long.class, slipNumber);

        jdbcTemplate.update("""
                INSERT INTO inbound_slip_lines (inbound_slip_id, line_no, product_id, product_code, product_name,
                    unit_type, planned_qty, inspected_qty, line_status, stored_at, created_at, updated_at)
                VALUES (?, 1, ?, 'AMB-001', 'ミネラルウォーター', 'PIECE', 10, 10, 'STORED',
                    ?::timestamptz, now(), now())
                """, slipId, productAmbId, storedDate + "T10:00:00+09:00");
    }

    private void insertOutboundSlipShipped(String slipNumber, LocalDate plannedDate, String shippedDate) {
        jdbcTemplate.update("""
                INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name,
                    partner_id, partner_code, partner_name, planned_date, status, shipped_at,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (?, 'NORMAL', ?, 'WH001', 'テスト倉庫', ?, 'CUS001', '得意先A', ?, 'SHIPPED',
                    ?::timestamptz, now(), ?, now(), ?, 0)
                """, slipNumber, warehouseId, customerPartnerId, plannedDate,
                shippedDate + "T15:00:00+09:00", adminUserId, adminUserId);

        Long slipId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slips WHERE slip_number = ?", Long.class, slipNumber);

        jdbcTemplate.update("""
                INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code, product_name,
                    unit_type, ordered_qty, shipped_qty, line_status, created_at, updated_at)
                VALUES (?, 1, ?, 'AMB-002', '緑茶ペットボトル', 'PIECE', 5, 5, 'SHIPPED', now(), now())
                """, slipId, productAmb2Id);
    }

    private void insertInboundSlipPending(String slipNumber, LocalDate plannedDate, String status) {
        jdbcTemplate.update("""
                INSERT INTO inbound_slips (slip_number, warehouse_id, warehouse_code, warehouse_name,
                    partner_id, partner_code, partner_name, planned_date, status,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, 'WH001', 'テスト倉庫', ?, 'SUP001', '仕入先A', ?, ?,
                    now(), ?, now(), ?, 0)
                """, slipNumber, warehouseId, supplierPartnerId, plannedDate, status, adminUserId, adminUserId);

        Long slipId = jdbcTemplate.queryForObject(
                "SELECT id FROM inbound_slips WHERE slip_number = ?", Long.class, slipNumber);

        jdbcTemplate.update("""
                INSERT INTO inbound_slip_lines (inbound_slip_id, line_no, product_id, product_code, product_name,
                    unit_type, planned_qty, inspected_qty, line_status, created_at, updated_at)
                VALUES (?, 1, ?, 'AMB-001', 'ミネラルウォーター', 'PIECE', 10, 0, ?, now(), now())
                """, slipId, productAmbId, status);
    }

    private void insertOutboundSlipPending(String slipNumber, LocalDate plannedDate, String status) {
        jdbcTemplate.update("""
                INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name,
                    partner_id, partner_code, partner_name, planned_date, status,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (?, 'NORMAL', ?, 'WH001', 'テスト倉庫', ?, 'CUS001', '得意先A', ?, ?,
                    now(), ?, now(), ?, 0)
                """, slipNumber, warehouseId, customerPartnerId, plannedDate, status, adminUserId, adminUserId);

        Long slipId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slips WHERE slip_number = ?", Long.class, slipNumber);

        jdbcTemplate.update("""
                INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code, product_name,
                    unit_type, ordered_qty, shipped_qty, line_status, created_at, updated_at)
                VALUES (?, 1, ?, 'AMB-001', 'ミネラルウォーター', 'PIECE', 10, 0, ?, now(), now())
                """, slipId, productAmbId, status);
    }

    private void insertInventory(int quantity) {
        jdbcTemplate.update("""
                INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type,
                    quantity, allocated_qty, updated_at)
                VALUES (?, ?, ?, 'PIECE', ?, 0, now())
                """, warehouseId, locA01_01_01_01, productAmbId, quantity);
    }

    // ========================================================
    // POST /api/v1/batch/daily-close — 日次締め実行
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/batch/daily-close — 日次締め実行")
    class ExecuteDailyClose {

        @Test
        @DisplayName("SC-001: 日替処理の全6ステップが正常完了する")
        void execute_normalRun_allStepsSuccess() throws Exception {
            LocalDate currentDate = LocalDate.of(2026, 3, 19);
            LocalDate targetDate = LocalDate.of(2026, 3, 20);
            setBusinessDate(currentDate);

            // テストデータ: 入荷STORED、出荷SHIPPED、在庫
            insertInboundSlipStored("INB-TEST-001", currentDate, "2026-03-20");
            insertOutboundSlipShipped("OUT-TEST-001", currentDate, "2026-03-20");
            insertInventory(100);

            ResponseEntity<String> response = postJson(DAILY_CLOSE_URL, dailyCloseBody(targetDate), adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
            assertThat(json.get("targetBusinessDate").asText()).isEqualTo("2026-03-20");
            assertThat(json.get("executionId").asLong()).isGreaterThan(0);
            assertThat(json.get("steps")).hasSize(6);

            // All steps should be SUCCESS
            for (JsonNode step : json.get("steps")) {
                assertThat(step.get("status").asText()).isEqualTo("SUCCESS");
            }

            // DB検証: business_date updated
            assertThat(getBusinessDate()).isEqualTo(targetDate);

            // DB検証: batch_execution_logs
            var logMap = jdbcTemplate.queryForMap(
                    "SELECT * FROM batch_execution_logs WHERE target_business_date = ?", targetDate);
            assertThat(logMap.get("status")).isEqualTo("SUCCESS");
            assertThat(logMap.get("step1_status")).isEqualTo("SUCCESS");
            assertThat(logMap.get("step6_status")).isEqualTo("SUCCESS");

            // DB検証: daily_summary_records
            int summaryCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM daily_summary_records WHERE business_date = ?",
                    Integer.class, targetDate);
            assertThat(summaryCount).isGreaterThanOrEqualTo(1);

            // DB検証: inventory_snapshots
            int snapshotCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory_snapshots WHERE business_date = ?",
                    Integer.class, targetDate);
            assertThat(snapshotCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("SC-002: 二重実行防止（同一営業日に再実行で409）")
        void execute_duplicateRun_returns409() throws Exception {
            LocalDate currentDate = LocalDate.of(2026, 3, 19);
            LocalDate targetDate = LocalDate.of(2026, 3, 20);
            setBusinessDate(currentDate);

            // First run
            ResponseEntity<String> first = postJson(DAILY_CLOSE_URL, dailyCloseBody(targetDate), adminHeaders);
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Second run (duplicate)
            ResponseEntity<String> second = postJson(DAILY_CLOSE_URL, dailyCloseBody(targetDate), adminHeaders);

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(second.getBody());
            assertThat(json.get("code").asText()).isEqualTo("BATCH_ALREADY_RUNNING");

            // DB検証: still 1 record
            int logCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM batch_execution_logs WHERE target_business_date = ?",
                    Integer.class, targetDate);
            assertThat(logCount).isEqualTo(1);
        }

        @Test
        @DisplayName("SC-005: 営業日連続性チェック（2日飛ばしでエラー）")
        void execute_nonConsecutiveDate_fails() throws Exception {
            LocalDate currentDate = LocalDate.of(2026, 3, 19);
            LocalDate targetDate = LocalDate.of(2026, 3, 22); // 3 days later
            setBusinessDate(currentDate);

            ResponseEntity<String> response = postJson(DAILY_CLOSE_URL, dailyCloseBody(targetDate), adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("FAILED");
            assertThat(json.get("steps").get(0).get("status").asText()).isEqualTo("FAILED");
            assertThat(json.get("steps").get(0).get("errorMessage").asText()).contains("連続していません");

            // Remaining steps should be SKIPPED
            for (int i = 1; i < 6; i++) {
                assertThat(json.get("steps").get(i).get("status").asText()).isEqualTo("SKIPPED");
            }

            // DB検証: business_date unchanged
            assertThat(getBusinessDate()).isEqualTo(currentDate);

            // DB検証: FAILED log
            var logMap = jdbcTemplate.queryForMap(
                    "SELECT * FROM batch_execution_logs WHERE target_business_date = ?", targetDate);
            assertThat(logMap.get("status")).isEqualTo("FAILED");
            assertThat(logMap.get("step1_status")).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("SC-010: 0件データでの日替処理実行")
        void execute_zeroData_allStepsSuccess() throws Exception {
            LocalDate currentDate = LocalDate.of(2026, 4, 1);
            LocalDate targetDate = LocalDate.of(2026, 4, 2);
            setBusinessDate(currentDate);

            ResponseEntity<String> response = postJson(DAILY_CLOSE_URL, dailyCloseBody(targetDate), adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("SUCCESS");

            // DB検証: business_date updated
            assertThat(getBusinessDate()).isEqualTo(targetDate);

            // DB検証: 0件の場合でもdaily_summary_recordsが生成される（アクティブ倉庫分）
            int summaryCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM daily_summary_records WHERE business_date = ?",
                    Integer.class, targetDate);
            assertThat(summaryCount).isGreaterThanOrEqualTo(1);

            // DB検証: 全数値項目が0
            var summary = jdbcTemplate.queryForMap(
                    "SELECT * FROM daily_summary_records WHERE business_date = ? AND warehouse_id = ?",
                    targetDate, warehouseId);
            assertThat(((Number) summary.get("inbound_count")).intValue()).isEqualTo(0);
            assertThat(((Number) summary.get("outbound_count")).intValue()).isEqualTo(0);
            assertThat(((Number) summary.get("return_count")).intValue()).isEqualTo(0);

            // DB検証: batch_execution_logs SUCCESS
            var logMap = jdbcTemplate.queryForMap(
                    "SELECT * FROM batch_execution_logs WHERE target_business_date = ?", targetDate);
            assertThat(logMap.get("status")).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("SC-003: 途中失敗→再実行（完了済みステップスキップ）")
        void execute_retryAfterFailure_skipsCompletedSteps() throws Exception {
            LocalDate currentDate = LocalDate.of(2026, 3, 21);
            LocalDate targetDate = LocalDate.of(2026, 3, 22);
            setBusinessDate(currentDate);

            // Insert a FAILED log with step1=SUCCESS, step2=SUCCESS, rest=FAILED/SKIPPED
            jdbcTemplate.update("""
                    INSERT INTO batch_execution_logs (target_business_date, status,
                        step1_status, step2_status, step3_status, step4_status, step5_status, step6_status,
                        error_message, started_at, completed_at, executed_by)
                    VALUES (?, 'FAILED', 'SUCCESS', 'SUCCESS', 'FAILED', 'SKIPPED', 'SKIPPED', 'SKIPPED',
                        'Test error', now(), now(), ?)
                    """, targetDate, adminUserId);

            // Set business date to target (step1 was already successful)
            setBusinessDate(targetDate);

            // Retry
            ResponseEntity<String> response = postJson(DAILY_CLOSE_URL, dailyCloseBody(targetDate), adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("SUCCESS");

            // All steps should be SUCCESS
            for (JsonNode step : json.get("steps")) {
                assertThat(step.get("status").asText()).isEqualTo("SUCCESS");
            }

            // DB検証: old FAILED deleted, new SUCCESS exists
            int logCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM batch_execution_logs WHERE target_business_date = ?",
                    Integer.class, targetDate);
            assertThat(logCount).isEqualTo(1);

            String logStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM batch_execution_logs WHERE target_business_date = ?",
                    String.class, targetDate);
            assertThat(logStatus).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("SC-004: 営業日更新の冪等性（再実行で二重進行しない）")
        void execute_idempotentBusinessDate_noDoubleAdvance() throws Exception {
            LocalDate targetDate = LocalDate.of(2026, 3, 22);
            setBusinessDate(targetDate); // Already advanced

            // Insert FAILED log
            jdbcTemplate.update("""
                    INSERT INTO batch_execution_logs (target_business_date, status,
                        step1_status, step2_status, step3_status, step4_status, step5_status, step6_status,
                        started_at, completed_at, executed_by)
                    VALUES (?, 'FAILED', 'SUCCESS', 'FAILED', 'SKIPPED', 'SKIPPED', 'SKIPPED', 'SKIPPED',
                        now(), now(), ?)
                    """, targetDate, adminUserId);

            ResponseEntity<String> response = postJson(DAILY_CLOSE_URL, dailyCloseBody(targetDate), adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("SUCCESS");

            // DB検証: business_date has NOT advanced to 2026-03-23
            assertThat(getBusinessDate()).isEqualTo(targetDate);
        }

        @Test
        @DisplayName("SC-006: 未入荷/未出荷リスト生成の検証")
        void execute_generatesUnreceivedAndUnshippedLists() throws Exception {
            LocalDate currentDate = LocalDate.of(2026, 3, 24);
            LocalDate targetDate = LocalDate.of(2026, 3, 25);
            setBusinessDate(currentDate);

            // 未入荷: planned_date <= targetDate かつ未入庫完了
            insertInboundSlipPending("INB-UNREC-001", targetDate, "PLANNED");
            insertInboundSlipPending("INB-UNREC-002", targetDate.minusDays(1), "INSPECTED");
            // 入庫完了（リスト対象外）
            insertInboundSlipStored("INB-STORED-001", targetDate, "2026-03-25");
            // 未来の予定日（リスト対象外）
            insertInboundSlipPending("INB-FUTURE-001", targetDate.plusDays(5), "PLANNED");

            // 未出荷: planned_date <= targetDate かつ未出荷完了
            insertOutboundSlipPending("OUT-UNSHIP-001", targetDate, "ORDERED");
            // 出荷完了（リスト対象外）
            insertOutboundSlipShipped("OUT-SHIPPED-001", targetDate, "2026-03-25");

            ResponseEntity<String> response = postJson(DAILY_CLOSE_URL, dailyCloseBody(targetDate), adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("SUCCESS");

            // DB検証: unreceived_list_records
            int unreceivedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT inbound_slip_id) FROM unreceived_list_records WHERE batch_business_date = ?",
                    Integer.class, targetDate);
            assertThat(unreceivedCount).isEqualTo(2); // PLANNED + INSPECTED

            // DB検証: STOREDはリスト対象外
            int storedInList = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM unreceived_list_records WHERE batch_business_date = ? AND current_status = 'STORED'",
                    Integer.class, targetDate);
            assertThat(storedInList).isEqualTo(0);

            // DB検証: 未来の予定日はリスト対象外
            int futureInList = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM unreceived_list_records ur " +
                            "JOIN inbound_slips s ON s.id = ur.inbound_slip_id " +
                            "WHERE ur.batch_business_date = ? AND s.slip_number = 'INB-FUTURE-001'",
                    Integer.class, targetDate);
            assertThat(futureInList).isEqualTo(0);

            // DB検証: unshipped_list_records
            int unshippedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT outbound_slip_id) FROM unshipped_list_records WHERE batch_business_date = ?",
                    Integer.class, targetDate);
            assertThat(unshippedCount).isEqualTo(1); // ORDERED only

            // レスポンスにカウントが含まれる
            assertThat(json.get("unreceivedCount")).isNotNull();
            assertThat(json.get("unshippedCount")).isNotNull();
        }
    }

    // ========================================================
    // GET /api/v1/batch/executions — バッチ実行履歴
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/batch/executions — バッチ実行履歴")
    class ListBatchExecutions {

        private void createTestLogs() {
            // 4 records: 3 SUCCESS, 1 FAILED
            jdbcTemplate.update("""
                    INSERT INTO batch_execution_logs (target_business_date, status,
                        step1_status, step2_status, step3_status, step4_status, step5_status, step6_status,
                        started_at, completed_at, executed_by)
                    VALUES ('2026-03-17', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS',
                        '2026-03-17 10:00:00+09', '2026-03-17 10:01:00+09', ?)
                    """, adminUserId);
            jdbcTemplate.update("""
                    INSERT INTO batch_execution_logs (target_business_date, status,
                        step1_status, step2_status, step3_status, step4_status, step5_status, step6_status,
                        started_at, completed_at, executed_by)
                    VALUES ('2026-03-18', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS',
                        '2026-03-18 10:00:00+09', '2026-03-18 10:01:00+09', ?)
                    """, adminUserId);
            jdbcTemplate.update("""
                    INSERT INTO batch_execution_logs (target_business_date, status,
                        step1_status, step2_status, step3_status, step4_status, step5_status, step6_status,
                        error_message, started_at, completed_at, executed_by)
                    VALUES ('2026-03-19', 'FAILED', 'SUCCESS', 'SUCCESS', 'FAILED', 'SKIPPED', 'SKIPPED', 'SKIPPED',
                        'Step 3 failed', '2026-03-19 10:00:00+09', '2026-03-19 10:01:00+09', ?)
                    """, adminUserId);
            jdbcTemplate.update("""
                    INSERT INTO batch_execution_logs (target_business_date, status,
                        step1_status, step2_status, step3_status, step4_status, step5_status, step6_status,
                        started_at, completed_at, executed_by)
                    VALUES ('2026-03-20', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS', 'SUCCESS',
                        '2026-03-20 10:00:00+09', '2026-03-20 10:01:00+09', ?)
                    """, adminUserId);
        }

        @Test
        @DisplayName("SC-008: 全件取得")
        void list_all_returns4Items() throws Exception {
            createTestLogs();

            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?page=0&size=20&sort=startedAt,desc", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(4);
            assertThat(json.get("content")).hasSize(4);
        }

        @Test
        @DisplayName("SC-008: ステータスフィルタ（FAILEDのみ）")
        void list_filterByStatus_returnsFailed() throws Exception {
            createTestLogs();

            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?status=FAILED&page=0&size=20&sort=startedAt,desc", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("status").asText()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("SC-008: 対象営業日フィルタ")
        void list_filterByTargetDate_returnsSpecific() throws Exception {
            createTestLogs();

            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?targetBusinessDate=2026-03-20&page=0&size=20&sort=startedAt,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("targetBusinessDate").asText()).isEqualTo("2026-03-20");
        }

        @Test
        @DisplayName("SC-008: 詳細取得（6ステップ結果含む）")
        void getDetail_returnsAllSteps() throws Exception {
            createTestLogs();
            Long logId = jdbcTemplate.queryForObject(
                    "SELECT id FROM batch_execution_logs WHERE target_business_date = '2026-03-19'", Long.class);

            ResponseEntity<String> response = get(EXECUTIONS_URL + "/" + logId, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("FAILED");
            assertThat(json.get("step1Status").asText()).isEqualTo("SUCCESS");
            assertThat(json.get("step3Status").asText()).isEqualTo("FAILED");
            assertThat(json.get("step4Status").asText()).isEqualTo("SKIPPED");
            assertThat(json.get("errorMessage").asText()).isEqualTo("Step 3 failed");
            assertThat(json.get("executedByName").asText()).isNotBlank();
        }

        @Test
        @DisplayName("SC-008: 存在しないID→404")
        void getDetail_nonExisting_returns404() throws Exception {
            ResponseEntity<String> response = get(EXECUTIONS_URL + "/999999", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("BATCH_EXECUTION_NOT_FOUND");
        }
    }

    // ========================================================
    // ロール別アクセス制御
    // ========================================================

    @Nested
    @DisplayName("ロール別アクセス制御")
    class RoleBasedAccess {

        @Test
        @DisplayName("VIEWERロールで日次締め実行→403")
        void execute_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            ResponseEntity<String> response = postJson(DAILY_CLOSE_URL,
                    dailyCloseBody(LocalDate.of(2026, 3, 20)), viewerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("VIEWERロールでバッチ履歴一覧→403")
        void list_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?page=0&size=20&sort=startedAt,desc", viewerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("未認証で日次締め実行→401")
        void execute_unauthenticated_returns401() throws Exception {
            HttpHeaders headers = createJsonHeaders();

            ResponseEntity<String> response = postJson(DAILY_CLOSE_URL,
                    dailyCloseBody(LocalDate.of(2026, 3, 20)), headers);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
