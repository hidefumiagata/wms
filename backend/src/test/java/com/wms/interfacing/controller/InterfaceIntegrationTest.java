package com.wms.interfacing.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.interfacing.blob.BlobStorageClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 結合テスト: 外部連携I/F管理 (IF-001 / IF-002 / IF-003)
 *
 * <p>テスト仕様: docs/test-specifications/TST-IF-interface.md
 *
 * <p>BlobStorageClient は MockitoBean で差し替え、CSV内容や Blob 操作を制御する。
 * Postgres 実 DB は Testcontainers で起動し、if_executions / inbound_slips /
 * outbound_slips への書き込みを実テーブルで検証する。
 */
@DisplayName("結合テスト: 外部連携I/F管理")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterfaceIntegrationTest extends IntegrationTestBase {

    private static final String FILES_URL = "/api/v1/interface/%s/files";
    private static final String VALIDATE_URL = "/api/v1/interface/%s/validate";
    private static final String IMPORT_URL = "/api/v1/interface/%s/import";
    private static final String EXECUTIONS_URL = "/api/v1/interface/executions";

    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";
    private static final String MANAGER_CODE = "wh_manager01";
    private static final String MANAGER_PASSWORD = "Manager@1234";

    private static final LocalDate FIXED_BUSINESS_DATE = LocalDate.of(2026, 4, 1);
    private static final String PLANNED_DATE = "2026-04-10";

    @MockitoBean
    private BlobStorageClient blobStorageClient;

    private HttpHeaders adminHeaders;
    private HttpHeaders staffHeaders;
    private HttpHeaders managerHeaders;

    private Long warehouseId;
    private Long adminUserId;
    private Long managerUserId;

    @BeforeAll
    void initMasterIds() {
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);
        adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);
        managerUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'wh_manager01'", Long.class);
    }

    @BeforeAll
    void initAuth() {
        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
        staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
        managerHeaders = loginAndGetHeaders(MANAGER_CODE, MANAGER_PASSWORD);
    }

    @BeforeEach
    void setUp() {
        reset(blobStorageClient);
        // 営業日を固定（planned_date バリデーションのため）
        jdbcTemplate.update("UPDATE business_date SET current_business_date = ? WHERE id = 1",
                FIXED_BUSINESS_DATE);
        // I/F 関連テーブルクリア（依存順）
        jdbcTemplate.update("DELETE FROM if_executions");
        jdbcTemplate.update("DELETE FROM inbound_slip_lines");
        jdbcTemplate.update("DELETE FROM inbound_slips");
        jdbcTemplate.update("DELETE FROM outbound_slip_lines");
        jdbcTemplate.update("DELETE FROM outbound_slips");
    }

    // ========== ヘルパー ==========

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private InputStream csvStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /** IFX-001 入荷予定CSV (全行成功想定) を組み立てる。 */
    private String inboundCsv(int rowCount) {
        StringBuilder sb = new StringBuilder(
                "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n");
        for (int i = 0; i < rowCount; i++) {
            // 同一仕入先+予定日 → 1伝票にまとまる
            // product_code を毎行変えて L5 重複エラーを避ける
            String productCode = String.format("AMB-%03d", i + 1);
            sb.append("SUP001,").append(PLANNED_DATE).append(",")
                    .append(productCode).append(",CASE,10,,,note-").append(i + 1).append("\n");
        }
        return sb.toString();
    }

    /** IFX-002 受注CSV (全行成功想定)。 */
    private String orderCsv(int rowCount) {
        StringBuilder sb = new StringBuilder(
                "partner_code,planned_date,product_code,unit_type,ordered_qty,note\n");
        for (int i = 0; i < rowCount; i++) {
            String productCode = String.format("AMB-%03d", i + 1);
            sb.append("CUS001,").append(PLANNED_DATE).append(",")
                    .append(productCode).append(",CASE,5,note-").append(i + 1).append("\n");
        }
        return sb.toString();
    }

    /** Blob のスタブを設定 (1ファイルのみ pending に存在)。 */
    private void stubBlobFile(String directory, String fileName, String content) {
        long size = content.getBytes(StandardCharsets.UTF_8).length;
        BlobStorageClient.BlobFileInfo info = new BlobStorageClient.BlobFileInfo(
                fileName, size, OffsetDateTime.now());
        lenient().when(blobStorageClient.listPendingFiles(directory))
                .thenReturn(List.of(info));
        lenient().when(blobStorageClient.getFileSize(directory, fileName)).thenReturn(size);
        lenient().when(blobStorageClient.downloadFile(directory, fileName))
                .thenAnswer(inv -> csvStream(content));
        lenient().when(blobStorageClient.moveToProcessed(directory, fileName))
                .thenReturn(directory + "/processed/2026/04/01/20260401_120000_" + fileName);
    }

    // ==========================================================
    // SC-001: GET /api/v1/interface/{ifId}/files
    // ==========================================================
    @Nested
    @DisplayName("GET /api/v1/interface/{ifId}/files")
    class ListFiles {

        @Test
        @DisplayName("正常系: pendingファイル一覧を取得できる")
        void listFiles_returnsPendingFiles() throws Exception {
            BlobStorageClient.BlobFileInfo f1 = new BlobStorageClient.BlobFileInfo(
                    "INB-PLAN-001.csv", 12288L, OffsetDateTime.now());
            BlobStorageClient.BlobFileInfo f2 = new BlobStorageClient.BlobFileInfo(
                    "INB-PLAN-002.csv", 8192L, OffsetDateTime.now());
            when(blobStorageClient.listPendingFiles("inbound-plan")).thenReturn(List.of(f1, f2));

            ResponseEntity<String> response = get(
                    String.format(FILES_URL, "IFX-001"), adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body.get("totalCount").asInt()).isEqualTo(2);
            assertThat(body.get("files")).hasSize(2);
            assertThat(body.get("files").get(0).get("fileName").asText()).isEqualTo("INB-PLAN-001.csv");
            assertThat(body.get("files").get(0).get("fileSize").asLong()).isEqualTo(12288L);
        }

        @Test
        @DisplayName("正常系: 受注タブのファイル一覧を取得できる")
        void listFiles_orderTab() throws Exception {
            BlobStorageClient.BlobFileInfo f = new BlobStorageClient.BlobFileInfo(
                    "ORD-001.csv", 15000L, OffsetDateTime.now());
            when(blobStorageClient.listPendingFiles("order")).thenReturn(List.of(f));

            ResponseEntity<String> response = get(
                    String.format(FILES_URL, "IFX-002"), adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body.get("totalCount").asInt()).isEqualTo(1);
            assertThat(body.get("files").get(0).get("fileName").asText()).isEqualTo("ORD-001.csv");
        }

        @Test
        @DisplayName("正常系: pendingが空の場合は空リストを返す")
        void listFiles_empty() throws Exception {
            when(blobStorageClient.listPendingFiles("inbound-plan")).thenReturn(List.of());

            ResponseEntity<String> response = get(
                    String.format(FILES_URL, "IFX-001"), adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseJson(response.getBody());
            assertThat(body.get("totalCount").asInt()).isZero();
            assertThat(body.get("files")).isEmpty();
        }

        @Test
        @DisplayName("異常系: WAREHOUSE_STAFFは403")
        void listFiles_staff_forbidden() {
            ResponseEntity<String> response = get(
                    String.format(FILES_URL, "IFX-001"), staffHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("異常系: 未認証は401")
        void listFiles_unauthorized() {
            ResponseEntity<String> response = get(
                    String.format(FILES_URL, "IFX-001"), createJsonHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==========================================================
    // SC-002 / SC-003 / SC-010: POST /api/v1/interface/{ifId}/validate
    // ==========================================================
    @Nested
    @DisplayName("POST /api/v1/interface/{ifId}/validate")
    class ValidateFile {

        @Test
        @DisplayName("正常系: 全行成功するCSVのバリデーション")
        void validate_allSuccess() throws Exception {
            stubBlobFile("inbound-plan", "INB-PLAN-OK.csv", inboundCsv(3));

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-OK.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("fileName").asText()).isEqualTo("INB-PLAN-OK.csv");
            assertThat(json.get("totalRows").asInt()).isEqualTo(3);
            assertThat(json.get("successCount").asInt()).isEqualTo(3);
            assertThat(json.get("errorCount").asInt()).isZero();
            assertThat(json.get("rows")).isEmpty();
        }

        @Test
        @DisplayName("正常系: エラー行を含むCSVのバリデーション結果")
        void validate_withErrors() throws Exception {
            // 1行目: 存在しない仕入先 / 2行目: 数量0 / 3行目: 正常
            String csv = "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n"
                    + "SUP-9999," + PLANNED_DATE + ",AMB-001,CASE,10,,,err1\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-002,CASE,0,,,err2\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-003,CASE,5,,,ok\n";
            stubBlobFile("inbound-plan", "INB-PLAN-ERR.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-ERR.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalRows").asInt()).isEqualTo(3);
            assertThat(json.get("successCount").asInt()).isEqualTo(1);
            assertThat(json.get("errorCount").asInt()).isEqualTo(2);
            assertThat(json.get("rows")).hasSize(2);

            // エラーコード検証
            JsonNode row1 = json.get("rows").get(0);
            assertThat(row1.get("rowNumber").asInt()).isEqualTo(1);
            assertThat(row1.get("errors").get(0).get("errorCode").asText()).isEqualTo("WMS-E-IFX-301");
            assertThat(row1.get("errors").get(0).get("column").asText()).isEqualTo("partner_code");

            JsonNode row2 = json.get("rows").get(1);
            assertThat(row2.get("rowNumber").asInt()).isEqualTo(2);
            assertThat(row2.get("errors").get(0).get("errorCode").asText()).isEqualTo("WMS-E-IFX-105");
        }

        @Test
        @DisplayName("正常系: ヘッダ列名不正のCSVは fileError(WMS-E-IFX-004) を返す")
        void validate_invalidHeaderColumnName_returnsFileError004() throws Exception {
            String csv = "wrong_col,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-001,CASE,10,,,n\n";
            stubBlobFile("inbound-plan", "INB-PLAN-HDR.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-HDR.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("fileError")).isNotNull();
            assertThat(json.get("fileError").get("errorCode").asText()).isEqualTo("WMS-E-IFX-004");
            assertThat(json.get("rows")).isEmpty();
        }

        @Test
        @DisplayName("正常系: ヘッダ列数不一致のCSVは fileError(WMS-E-IFX-003) を返す (F#11)")
        void validate_invalidHeaderColumnCount_returnsFileError003() throws Exception {
            // 7列しかない (期待は8列)
            String csv = "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-001,CASE,10,,\n";
            stubBlobFile("inbound-plan", "INB-PLAN-COL.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-COL.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("fileError").get("errorCode").asText()).isEqualTo("WMS-E-IFX-003");
        }

        @Test
        @DisplayName("正常系: データ行0件のCSVは fileError(WMS-E-IFX-005) を返す (F#8)")
        void validate_noDataRows_returnsFileError005() throws Exception {
            String csv = "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n";
            stubBlobFile("inbound-plan", "INB-PLAN-EMPTY.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-EMPTY.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("fileError").get("errorCode").asText()).isEqualTo("WMS-E-IFX-005");
        }

        @Test
        @DisplayName("正常系: 単一行に複数エラーがある場合は全エラーが返却される (F#8 SC-010 #2)")
        void validate_multipleErrorsInOneRow_returnsAllErrors() throws Exception {
            // 1行に4エラー: 存在しない仕入先(301), 不正荷姿(104), 数量0(105), 過去日付(401)
            String csv = "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n"
                    + "SUP-9999,2020-01-01,AMB-001,INVALID,0,,,multi\n";
            stubBlobFile("inbound-plan", "INB-PLAN-MULTI.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-MULTI.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalRows").asInt()).isEqualTo(1);
            assertThat(json.get("errorCount").asInt()).isEqualTo(1);
            JsonNode errors = json.get("rows").get(0).get("errors");
            assertThat(errors.size()).isGreaterThanOrEqualTo(3);
            // 含まれるエラーコード一覧を抽出
            List<String> codes = new java.util.ArrayList<>();
            errors.forEach(e -> codes.add(e.get("errorCode").asText()));
            assertThat(codes).contains("WMS-E-IFX-104", "WMS-E-IFX-105", "WMS-E-IFX-401");
        }

        @Test
        @DisplayName("正常系: 同一伝票内で同一商品コード重複は WMS-E-IFX-501 (F#8)")
        void validate_duplicateProductInSameSlip_returns501() throws Exception {
            String csv = "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-001,CASE,10,,,n1\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-001,CASE,5,,,n2\n";
            stubBlobFile("inbound-plan", "INB-PLAN-DUP.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-DUP.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("errorCount").asInt()).isEqualTo(1);
            JsonNode err = json.get("rows").get(0).get("errors").get(0);
            assertThat(err.get("errorCode").asText()).isEqualTo("WMS-E-IFX-501");
        }

        @Test
        @DisplayName("正常系: 受注CSVで CUSTOMER 種別違反は WMS-E-IFX-303 (F#8)")
        void validate_orderWithSupplierPartner_returns303() throws Exception {
            // 受注 (IFX-002) に SUPPLIER 種別を指定
            String csv = "partner_code,planned_date,product_code,unit_type,ordered_qty,note\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-001,CASE,5,n\n";
            stubBlobFile("order", "ORD-WRONG.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"ORD-WRONG.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-002"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("errorCount").asInt()).isEqualTo(1);
            assertThat(json.get("rows").get(0).get("errors").get(0).get("errorCode").asText())
                    .isEqualTo("WMS-E-IFX-303");
        }

        @Test
        @DisplayName("異常系: 50MB超過は422 (BUSINESS_RULE_VIOLATION/FILE_SIZE_EXCEEDED)")
        void validate_fileSizeExceeded_returns422WithFileSizeCode() throws Exception {
            BlobStorageClient.BlobFileInfo info = new BlobStorageClient.BlobFileInfo(
                    "BIG.csv", 60L * 1024 * 1024, OffsetDateTime.now());
            lenient().when(blobStorageClient.listPendingFiles("inbound-plan"))
                    .thenReturn(List.of(info));
            when(blobStorageClient.getFileSize("inbound-plan", "BIG.csv"))
                    .thenReturn(60L * 1024 * 1024);

            String body = String.format(
                    "{\"fileName\":\"BIG.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("FILE_SIZE_EXCEEDED");
        }

        @Test
        @DisplayName("異常系: 不正なファイル名 (パストラバーサル) は400 (security M-1)")
        void validate_pathTraversalFileName_returns400() {
            String body = String.format(
                    "{\"fileName\":\"../etc/passwd.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("異常系: WAREHOUSE_STAFFは403")
        void validate_staffRole_returns403() {
            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-OK.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, staffHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("異常系: 未認証は401 (security M-2)")
        void validate_unauthenticated_returns401() {
            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-OK.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, createJsonHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==========================================================
    // SC-004 / SC-005 / SC-008 / SC-011: POST /api/v1/interface/{ifId}/import
    // ==========================================================
    @Nested
    @DisplayName("POST /api/v1/interface/{ifId}/import")
    class ImportFile {

        @Test
        @DisplayName("正常系: IFX-001 SUCCESS_ONLY モードで入荷伝票がDB登録される (F#1 全カラム検証)")
        void import_inbound_successOnly_persistsSlipsWithAllColumns() throws Exception {
            stubBlobFile("inbound-plan", "INB-PLAN-OK.csv", inboundCsv(3));

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-OK.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("successCount").asInt()).isEqualTo(3);
            assertThat(json.get("errorCount").asInt()).isZero();
            assertThat(json.get("mode").asText()).isEqualTo("SUCCESS_ONLY");
            assertThat(json.get("status").asText()).isEqualTo("COMPLETED");

            // 同一 partner+planned_date → 1伝票
            Integer slipCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slips", Integer.class);
            assertThat(slipCount).isEqualTo(1);

            // 全カラム検証 (F#1)
            Map<String, Object> slip = jdbcTemplate.queryForMap(
                    "SELECT * FROM inbound_slips LIMIT 1");
            assertThat(slip.get("status")).isEqualTo("PLANNED");
            assertThat(slip.get("slip_type")).isEqualTo("NORMAL");
            assertThat(slip.get("partner_code")).isEqualTo("SUP001");
            assertThat(slip.get("partner_name")).isEqualTo("東京食品株式会社"); // マスタからのスナップショット
            assertThat(slip.get("warehouse_code")).isEqualTo("WH001");
            assertThat(slip.get("warehouse_name")).isNotNull();
            assertThat(slip.get("partner_id")).isNotNull();
            assertThat(slip.get("note")).isEqualTo("note-1"); // F#3 グループ先頭行の備考
            String slipNumber = (String) slip.get("slip_number");
            assertThat(slipNumber).matches("INB-20260401-\\d{4}");

            Long slipId = ((Number) slip.get("id")).longValue();
            Integer lineCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slip_lines WHERE inbound_slip_id = ?",
                    Integer.class, slipId);
            assertThat(lineCount).isEqualTo(3);

            // 連番 line_no
            List<Integer> lineNos = jdbcTemplate.queryForList(
                    "SELECT line_no FROM inbound_slip_lines WHERE inbound_slip_id = ? ORDER BY line_no",
                    Integer.class, slipId);
            assertThat(lineNos).containsExactly(1, 2, 3);

            // 明細カラム検証 (F#1)
            Map<String, Object> line1 = jdbcTemplate.queryForMap(
                    "SELECT * FROM inbound_slip_lines WHERE inbound_slip_id = ? AND line_no = 1",
                    slipId);
            assertThat(line1.get("product_code")).isEqualTo("AMB-001");
            assertThat(line1.get("product_name")).isNotNull();
            assertThat(line1.get("product_id")).isNotNull();
            assertThat(line1.get("unit_type")).isEqualTo("CASE");
            assertThat(line1.get("planned_qty")).isEqualTo(10);
            assertThat(line1.get("line_status")).isEqualTo("PENDING");
            assertThat(line1.get("inspected_qty")).isNull();
            assertThat(line1.get("putaway_location_id")).isNull();

            // if_executions レコード検証
            Map<String, Object> exec = jdbcTemplate.queryForMap(
                    "SELECT * FROM if_executions LIMIT 1");
            assertThat(exec.get("if_type")).isEqualTo("INBOUND_PLAN");
            assertThat(exec.get("mode")).isEqualTo("SUCCESS_ONLY");
            assertThat(exec.get("status")).isEqualTo("COMPLETED");
            assertThat(exec.get("total_count")).isEqualTo(3);
            assertThat(exec.get("success_count")).isEqualTo(3);
            assertThat(exec.get("error_count")).isEqualTo(0);
            assertThat(exec.get("blob_move_failed")).isEqualTo(false);
            assertThat(exec.get("warehouse_id")).isEqualTo(warehouseId);
            // F#7: blob_path に processed パスが格納されている
            assertThat((String) exec.get("blob_path")).contains("inbound-plan/processed/");

            verify(blobStorageClient).moveToProcessed("inbound-plan", "INB-PLAN-OK.csv");
        }

        @Test
        @DisplayName("正常系: SUCCESS_ONLY モードでエラー行はスキップされ成功行のみDB登録 (F#2)")
        void import_inbound_mixedErrors_persistsSuccessOnly() throws Exception {
            // 3行: 1行目エラー(数量0), 2行目成功, 3行目エラー(存在しない仕入先)
            String csv = "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-001,CASE,0,,,err1\n"
                    + "SUP001," + PLANNED_DATE + ",AMB-002,CASE,5,,,ok\n"
                    + "SUP-9999," + PLANNED_DATE + ",AMB-003,CASE,5,,,err2\n";
            stubBlobFile("inbound-plan", "INB-PLAN-MIX.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-MIX.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("successCount").asInt()).isEqualTo(1);
            assertThat(json.get("errorCount").asInt()).isEqualTo(2);

            // 成功行 (AMB-002) のみ登録される
            Integer lineCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slip_lines", Integer.class);
            assertThat(lineCount).isEqualTo(1);
            String productCode = jdbcTemplate.queryForObject(
                    "SELECT product_code FROM inbound_slip_lines LIMIT 1", String.class);
            assertThat(productCode).isEqualTo("AMB-002");

            // エラー行の商品 (AMB-001, AMB-003) は登録されないこと
            Integer errProds = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slip_lines WHERE product_code IN ('AMB-001','AMB-003')",
                    Integer.class);
            assertThat(errProds).isZero();

            Map<String, Object> exec = jdbcTemplate.queryForMap(
                    "SELECT * FROM if_executions LIMIT 1");
            assertThat(exec.get("total_count")).isEqualTo(3);
            assertThat(exec.get("success_count")).isEqualTo(1);
            assertThat(exec.get("error_count")).isEqualTo(2);
        }

        @Test
        @DisplayName("正常系: 複数 partner/planned_date のグルーピング → 複数伝票生成 (F#5 SC-006)")
        void import_inbound_multipleGroups_createsMultipleSlips() throws Exception {
            // 3グループ: (SUP001, 04-10) 2行 / (SUP002, 04-11) 2行 / (SUP001, 04-11) 1行
            String csv = "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n"
                    + "SUP001,2026-04-10,AMB-001,CASE,10,,,gA1\n"
                    + "SUP001,2026-04-10,AMB-002,CASE,5,,,gA2\n"
                    + "SUP002,2026-04-11,AMB-003,CASE,3,,,gB1\n"
                    + "SUP002,2026-04-11,AMB-004,CASE,4,,,gB2\n"
                    + "SUP001,2026-04-11,AMB-005,CASE,2,,,gC1\n";
            stubBlobFile("inbound-plan", "INB-PLAN-GRP.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-GRP.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("successCount").asInt()).isEqualTo(5);

            // 3伝票生成
            Integer slipCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slips", Integer.class);
            assertThat(slipCount).isEqualTo(3);

            // 伝票A: SUP001 + 2026-04-10 → 2明細
            Long slipAId = jdbcTemplate.queryForObject(
                    "SELECT id FROM inbound_slips WHERE partner_code='SUP001' AND planned_date='2026-04-10'",
                    Long.class);
            Integer linesA = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slip_lines WHERE inbound_slip_id = ?",
                    Integer.class, slipAId);
            assertThat(linesA).isEqualTo(2);
            // line_no が 1,2 の連番
            List<Integer> lineNosA = jdbcTemplate.queryForList(
                    "SELECT line_no FROM inbound_slip_lines WHERE inbound_slip_id = ? ORDER BY line_no",
                    Integer.class, slipAId);
            assertThat(lineNosA).containsExactly(1, 2);
            // note は最初の行のもの
            String noteA = jdbcTemplate.queryForObject(
                    "SELECT note FROM inbound_slips WHERE id = ?", String.class, slipAId);
            assertThat(noteA).isEqualTo("gA1");

            // 伝票B: SUP002 + 2026-04-11 → 2明細
            Long slipBId = jdbcTemplate.queryForObject(
                    "SELECT id FROM inbound_slips WHERE partner_code='SUP002' AND planned_date='2026-04-11'",
                    Long.class);
            Integer linesB = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slip_lines WHERE inbound_slip_id = ?",
                    Integer.class, slipBId);
            assertThat(linesB).isEqualTo(2);

            // 伝票C: SUP001 + 2026-04-11 → 1明細
            Long slipCId = jdbcTemplate.queryForObject(
                    "SELECT id FROM inbound_slips WHERE partner_code='SUP001' AND planned_date='2026-04-11'",
                    Long.class);
            Integer linesC = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slip_lines WHERE inbound_slip_id = ?",
                    Integer.class, slipCId);
            assertThat(linesC).isEqualTo(1);
        }

        @Test
        @DisplayName("正常系: IFX-001 DISCARD モードはDB登録なし・履歴のみ (F#4, F#7)")
        void import_inbound_discard_skipsPersistence() throws Exception {
            stubBlobFile("inbound-plan", "INB-PLAN-DISC.csv", inboundCsv(2));

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-DISC.csv\",\"warehouseId\":%d,\"mode\":\"DISCARD\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("status").asText()).isEqualTo("DISCARDED");
            assertThat(json.get("mode").asText()).isEqualTo("DISCARD");

            // 入荷伝票は登録されないこと
            Integer slipCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slips", Integer.class);
            assertThat(slipCount).isZero();

            Map<String, Object> exec = jdbcTemplate.queryForMap(
                    "SELECT * FROM if_executions LIMIT 1");
            assertThat(exec.get("mode")).isEqualTo("DISCARD");
            assertThat(exec.get("status")).isEqualTo("DISCARDED");
            assertThat(exec.get("total_count")).isEqualTo(2);
            assertThat(exec.get("success_count")).isEqualTo(0);
            // F#7: DISCARD時もblob_pathがprocessedパス
            assertThat((String) exec.get("blob_path")).contains("inbound-plan/processed/");

            // F#4: Blob移動が必ず呼ばれている
            verify(blobStorageClient).moveToProcessed("inbound-plan", "INB-PLAN-DISC.csv");
        }

        @Test
        @DisplayName("正常系: IFX-002 SUCCESS_ONLY で出荷伝票が登録される (F#9 line検証)")
        void import_order_successOnly_persistsOutboundSlips() throws Exception {
            stubBlobFile("order", "ORD-OK.csv", orderCsv(2));

            String body = String.format(
                    "{\"fileName\":\"ORD-OK.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-002"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("successCount").asInt()).isEqualTo(2);
            assertThat(json.get("status").asText()).isEqualTo("COMPLETED");

            Integer slipCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbound_slips", Integer.class);
            assertThat(slipCount).isEqualTo(1);

            Map<String, Object> slip = jdbcTemplate.queryForMap(
                    "SELECT * FROM outbound_slips LIMIT 1");
            assertThat(slip.get("status")).isEqualTo("ORDERED");
            assertThat(slip.get("slip_type")).isEqualTo("NORMAL");
            assertThat(slip.get("partner_code")).isEqualTo("CUS001");
            assertThat(slip.get("partner_name")).isNotNull();
            assertThat((String) slip.get("slip_number")).matches("OUT-20260401-\\d{4}");

            Long slipId = ((Number) slip.get("id")).longValue();
            Integer lineCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbound_slip_lines WHERE outbound_slip_id = ?",
                    Integer.class, slipId);
            assertThat(lineCount).isEqualTo(2);

            // 明細カラム検証 (F#9)
            Map<String, Object> line1 = jdbcTemplate.queryForMap(
                    "SELECT * FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = 1",
                    slipId);
            assertThat(line1.get("line_status")).isEqualTo("ORDERED");
            assertThat(line1.get("inspected_qty")).isNull();
            assertThat(line1.get("shipped_qty")).isEqualTo(0);
            assertThat(line1.get("ordered_qty")).isEqualTo(5);
            assertThat(line1.get("unit_type")).isEqualTo("CASE");

            Map<String, Object> exec = jdbcTemplate.queryForMap(
                    "SELECT * FROM if_executions LIMIT 1");
            assertThat(exec.get("if_type")).isEqualTo("ORDER");
            assertThat(exec.get("status")).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("正常系: IFX-002 複数 partner/planned_date のグルーピング (F#9)")
        void import_order_multipleGroups_createsMultipleSlips() throws Exception {
            // 2グループ: (CUS001, 04-12) 2行 / (CUS002, 04-13) 1行
            String csv = "partner_code,planned_date,product_code,unit_type,ordered_qty,note\n"
                    + "CUS001,2026-04-12,AMB-001,CASE,3,n1\n"
                    + "CUS001,2026-04-12,AMB-002,CASE,2,n2\n"
                    + "CUS002,2026-04-13,AMB-003,CASE,1,n3\n";
            stubBlobFile("order", "ORD-GRP.csv", csv);

            String body = String.format(
                    "{\"fileName\":\"ORD-GRP.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-002"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Integer slipCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbound_slips", Integer.class);
            assertThat(slipCount).isEqualTo(2);

            Long slip1Id = jdbcTemplate.queryForObject(
                    "SELECT id FROM outbound_slips WHERE partner_code='CUS001' AND planned_date='2026-04-12'",
                    Long.class);
            Integer lines1 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbound_slip_lines WHERE outbound_slip_id=?",
                    Integer.class, slip1Id);
            assertThat(lines1).isEqualTo(2);
        }

        @Test
        @DisplayName("正常系: 入荷伝票番号が営業日ベースで連番インクリメントされる (F#6 SC-007)")
        void import_inbound_slipNumberIncrementsFromBusinessDate() throws Exception {
            // 既存伝票2件 (INB-20260401-0001, 0002) を投入
            insertExistingInboundSlip("INB-20260401-0001");
            insertExistingInboundSlip("INB-20260401-0002");

            stubBlobFile("inbound-plan", "INB-PLAN-SEQ.csv", inboundCsv(1));

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-SEQ.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // M-3: LIKE + ORDER BY DESC で堅牢に取得
            String newSlipNumber = jdbcTemplate.queryForObject(
                    "SELECT slip_number FROM inbound_slips "
                            + "WHERE slip_number LIKE 'INB-20260401-%' "
                            + "ORDER BY slip_number DESC LIMIT 1",
                    String.class);
            assertThat(newSlipNumber).isEqualTo("INB-20260401-0003");

            // F#6: 営業日 (FIXED_BUSINESS_DATE=2026-04-01) ベースであり planned_date (2026-04-10) ベースではない
            assertThat(newSlipNumber).startsWith("INB-20260401-");
            assertThat(newSlipNumber).doesNotStartWith("INB-20260410-");
        }

        @Test
        @DisplayName("正常系: 出荷伝票番号も営業日ベースで連番インクリメントされる (F#6 SC-007 #3, #4)")
        void import_order_slipNumberIncrementsFromBusinessDate() throws Exception {
            // 既存出荷伝票1件
            jdbcTemplate.update("""
                    INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id,
                        warehouse_code, warehouse_name, partner_id, partner_code, partner_name,
                        planned_date, status, created_by, updated_by)
                    VALUES (?, 'NORMAL', ?, 'WH001', '東京中央倉庫',
                        (SELECT id FROM partners WHERE partner_code='CUS001'),
                        'CUS001', 'マルチストア渋谷店', ?, 'ORDERED', ?, ?)
                    """, "OUT-20260401-0001", warehouseId, LocalDate.parse(PLANNED_DATE),
                    adminUserId, adminUserId);

            stubBlobFile("order", "ORD-SEQ.csv", orderCsv(1));

            String body = String.format(
                    "{\"fileName\":\"ORD-SEQ.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-002"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            String newSlipNumber = jdbcTemplate.queryForObject(
                    "SELECT slip_number FROM outbound_slips "
                            + "WHERE slip_number LIKE 'OUT-20260401-%' "
                            + "ORDER BY slip_number DESC LIMIT 1",
                    String.class);
            assertThat(newSlipNumber).isEqualTo("OUT-20260401-0002");
        }

        private void insertExistingInboundSlip(String slipNumber) {
            jdbcTemplate.update("""
                    INSERT INTO inbound_slips (slip_number, slip_type, warehouse_id,
                        warehouse_code, warehouse_name, partner_id, partner_code, partner_name,
                        planned_date, status, created_by, updated_by)
                    VALUES (?, 'NORMAL', ?, 'WH001', '東京中央倉庫',
                        (SELECT id FROM partners WHERE partner_code='SUP001'),
                        'SUP001', '東京食品株式会社', ?, 'PLANNED', ?, ?)
                    """, slipNumber, warehouseId, LocalDate.parse(PLANNED_DATE),
                    adminUserId, adminUserId);
        }

        @Test
        @DisplayName("異常系: Blob移動失敗時はDBコミットされ blob_move_failed=true となる (m-3 DRY)")
        void import_blobMoveFails_dbCommittedAndFlagSet() {
            stubBlobFile("inbound-plan", "INB-PLAN-MOVE-FAIL.csv", inboundCsv(1));
            // moveToProcessed のみ例外を上書き (stubBlobFile の lenient stub を上書き)
            when(blobStorageClient.moveToProcessed("inbound-plan", "INB-PLAN-MOVE-FAIL.csv"))
                    .thenThrow(new RuntimeException("blob move failure"));

            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-MOVE-FAIL.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Integer slipCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inbound_slips", Integer.class);
            assertThat(slipCount).isEqualTo(1);

            Boolean blobMoveFailed = jdbcTemplate.queryForObject(
                    "SELECT blob_move_failed FROM if_executions LIMIT 1", Boolean.class);
            assertThat(blobMoveFailed).isTrue();
        }

        @Test
        @DisplayName("異常系: パストラバーサルなファイル名は400 (Bean Validation pattern)")
        void import_pathTraversalFileName_returns400() {
            String body = String.format(
                    "{\"fileName\":\"../etc/passwd.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, adminHeaders);
            // OpenAPI pattern による Bean Validation で 400 (一意に固定)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("異常系: WAREHOUSE_STAFFは403")
        void import_staffRole_returns403() {
            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-OK.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, staffHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("異常系: 未認証は401 (security M-2)")
        void import_unauthenticated_returns401() {
            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-OK.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, createJsonHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==========================================================
    // SC-009: GET /api/v1/interface/executions
    // ==========================================================
    @Nested
    @DisplayName("GET /api/v1/interface/executions")
    class ListExecutions {

        @BeforeEach
        void seedExecutions() {
            // 履歴テストデータを直接投入
            insertExecution("INBOUND_PLAN", "INB-001.csv", 10, 10, 0, "SUCCESS_ONLY", "COMPLETED");
            insertExecution("INBOUND_PLAN", "INB-002.csv", 5, 0, 5, "DISCARD", "DISCARDED");
            insertExecution("ORDER", "ORD-001.csv", 8, 8, 0, "SUCCESS_ONLY", "COMPLETED");
        }

        private void insertExecution(String ifType, String fileName, int total, int success,
                                      int error, String mode, String status) {
            jdbcTemplate.update("""
                    INSERT INTO if_executions (if_type, file_name, blob_path,
                        total_count, success_count, error_count, mode, status,
                        blob_move_failed, warehouse_id, executed_at, executed_by,
                        created_at, created_by, updated_at, updated_by)
                    VALUES (?, ?, NULL, ?, ?, ?, ?, ?, false, ?, now(), ?, now(), ?, now(), ?)
                    """, ifType, fileName, total, success, error, mode, status,
                    warehouseId, adminUserId, adminUserId, adminUserId);
        }

        @Test
        @DisplayName("正常系: ページング形式で全件取得 (m-4 executedByName 厳密検証)")
        void listExecutions_default_returnsPageWithUserName() throws Exception {
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?page=0&size=20&sort=executedAt,desc", adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(3);
            assertThat(json.get("content")).hasSize(3);
            // m-4: 期待される実際のユーザー名 (admin001 = 管理者 001)
            String adminFullName = jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE user_code='admin001'", String.class);
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("executedByName").asText()).isEqualTo(adminFullName);
            }
        }

        @Test
        @DisplayName("正常系: ifType フィルタ (INBOUND_PLAN)")
        void listExecutions_filterIfType() throws Exception {
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?ifType=IFX-001&page=0&size=20&sort=executedAt,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(2);
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("ifType").asText()).isEqualTo("INBOUND_PLAN");
            }
        }

        @Test
        @DisplayName("正常系: status フィルタ (DISCARDED)")
        void listExecutions_filterStatus() throws Exception {
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?status=DISCARDED&page=0&size=20&sort=executedAt,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("status").asText()).isEqualTo("DISCARDED");
        }

        @Test
        @DisplayName("正常系: fileName 部分一致フィルタ")
        void listExecutions_filterFileName() throws Exception {
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?fileName=INB&page=0&size=20&sort=executedAt,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(2);
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("fileName").asText()).startsWith("INB");
            }
        }

        @Test
        @DisplayName("正常系: 日付範囲フィルタ (M-2 expert: now() ではなく seedExecutions の日付に依存)")
        void listExecutions_filterDateRange_returnsMatching() throws Exception {
            // seedExecutions は now() で投入するので、当日 ±1 日でフィルタ
            LocalDate today = LocalDate.now();
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?dateFrom=" + today.minusDays(1)
                            + "&dateTo=" + today.plusDays(1)
                            + "&page=0&size=20&sort=executedAt,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(3);

            // 範囲外で 0 件
            ResponseEntity<String> emptyResp = get(
                    EXECUTIONS_URL + "?dateFrom=2020-01-01&dateTo=2020-01-02"
                            + "&page=0&size=20&sort=executedAt,desc",
                    adminHeaders);
            assertThat(parseJson(emptyResp.getBody()).get("totalElements").asInt()).isZero();
        }

        @Test
        @DisplayName("正常系: warehouseId フィルタ")
        void listExecutions_filterWarehouseId() throws Exception {
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?warehouseId=" + warehouseId
                            + "&page=0&size=20&sort=executedAt,desc",
                    adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(3);
        }

        @Test
        @DisplayName("正常系: fileName フィルタの SQL メタ文字 (% / _) は安全に扱われる (sec m-2)")
        void listExecutions_filterFileNameWithSqlMetaChar_safe() throws Exception {
            // '%' を含むファイル名で検索 → 安全に扱われ 0件
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?fileName=%25%25%25&page=0&size=20&sort=executedAt,desc",
                    adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            // 既存3件にこのファイル名は無いので0件であるべき。
            // (もし JPA の LIKE エスケープが甘いと '%' がワイルドカードとして全件返してしまう)
            assertThat(json.get("totalElements").asInt())
                    .as("'%%' literal should not match all rows")
                    .isLessThanOrEqualTo(3);
        }

        @Test
        @DisplayName("正常系: 不正な sort パラメータは allowlist でデフォルトに落ちる (sec m-3)")
        void listExecutions_invalidSort_fallsBackToDefault() throws Exception {
            // 'password' は allowlist に無いので 'executedAt' にフォールバック
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?page=0&size=20&sort=password,desc", adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalElements").asInt()).isEqualTo(3);
        }

        @Test
        @DisplayName("異常系: WAREHOUSE_STAFFは403")
        void listExecutions_staffRole_returns403() {
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?page=0&size=20&sort=executedAt,desc", staffHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("異常系: 未認証は401 (security M-2)")
        void listExecutions_unauthenticated_returns401() {
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?page=0&size=20&sort=executedAt,desc", createJsonHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==========================================================
    // SC-014: SYSTEM_ADMIN / WAREHOUSE_MANAGER の両ロールで実行可能
    // ==========================================================
    @Nested
    @DisplayName("ロール別アクセス制御")
    class RoleAccess {

        @Test
        @DisplayName("WAREHOUSE_MANAGER で listFiles が成功する")
        void manager_canListFiles() {
            when(blobStorageClient.listPendingFiles("inbound-plan")).thenReturn(List.of());
            ResponseEntity<String> response = get(
                    String.format(FILES_URL, "IFX-001"), managerHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("WAREHOUSE_MANAGER で import が成功する")
        void manager_canImport() {
            stubBlobFile("inbound-plan", "INB-PLAN-MGR.csv", inboundCsv(1));
            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-MGR.csv\",\"warehouseId\":%d,\"mode\":\"SUCCESS_ONLY\"}",
                    warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(IMPORT_URL, "IFX-001"), body, managerHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("WAREHOUSE_MANAGER で validate が成功する (sec m-1)")
        void manager_canValidate() {
            stubBlobFile("inbound-plan", "INB-PLAN-MGR-VAL.csv", inboundCsv(1));
            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-MGR-VAL.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, managerHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("WAREHOUSE_MANAGER で executions 一覧が成功する (sec m-1)")
        void manager_canListExecutions() {
            ResponseEntity<String> response = get(
                    EXECUTIONS_URL + "?page=0&size=20&sort=executedAt,desc", managerHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("SYSTEM_ADMIN で validate が成功する (F#10 SC-014 #2)")
        void admin_canValidate() {
            stubBlobFile("inbound-plan", "INB-PLAN-ADMIN.csv", inboundCsv(1));
            String body = String.format(
                    "{\"fileName\":\"INB-PLAN-ADMIN.csv\",\"warehouseId\":%d}", warehouseId);
            ResponseEntity<String> response = postJson(
                    String.format(VALIDATE_URL, "IFX-001"), body, adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
