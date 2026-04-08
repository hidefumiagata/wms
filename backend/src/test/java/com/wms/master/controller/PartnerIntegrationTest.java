package com.wms.master.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.shared.test.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("マスタ結合テスト: 取引先管理")
class PartnerIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/master/partners";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";

    private HttpHeaders adminHeaders;

    // M-2: insertInboundSlip/insertOutboundSlip から参照する共通データを
    //      setUp で一度だけ取得してキャッシュする (N+1 クエリ解消)
    private Long defaultWarehouseId;
    private String defaultWarehouseCode;
    private String defaultWarehouseName;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        // テスト用データのクリーンアップ
        // FK制約のため、伝票 → 取引先の順で削除する
        // m-3: slip_number の LIKE 条件に加え、IT-% 取引先に紐付く行も掃除する
        jdbcTemplate.update(
                "DELETE FROM inbound_slips WHERE slip_number LIKE 'IT-%' "
                        + "OR partner_id IN (SELECT id FROM partners WHERE partner_code LIKE 'IT-%')");
        jdbcTemplate.update(
                "DELETE FROM outbound_slips WHERE slip_number LIKE 'IT-%' "
                        + "OR partner_id IN (SELECT id FROM partners WHERE partner_code LIKE 'IT-%')");
        jdbcTemplate.update("DELETE FROM partners WHERE partner_code LIKE 'IT-%'");

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);

        // M-2: 伝票 INSERT ヘルパーが参照する共通値をキャッシュ
        Map<String, Object> wh = jdbcTemplate.queryForMap(
                "SELECT id, warehouse_code, warehouse_name FROM warehouses ORDER BY id LIMIT 1");
        this.defaultWarehouseId = ((Number) wh.get("id")).longValue();
        this.defaultWarehouseCode = (String) wh.get("warehouse_code");
        this.defaultWarehouseName = (String) wh.get("warehouse_name");
        this.adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);
    }

    // ========================================================
    // POST /api/v1/master/partners — 取引先作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/master/partners — 取引先作成")
    class CreatePartner {

        @Test
        @DisplayName("SC-C05: 仕入先を正常に登録できる")
        void create_supplier_returns201() throws Exception {
            String body = """
                    {
                        "partnerCode": "IT-SUP01",
                        "partnerName": "テスト仕入先",
                        "partnerNameKana": "テストシイレサキ",
                        "partnerType": "SUPPLIER",
                        "address": "東京都千代田区1-1-1",
                        "phone": "03-1234-5678",
                        "contactPerson": "担当太郎",
                        "email": "test@example.com"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("partnerCode").asText()).isEqualTo("IT-SUP01");
            assertThat(json.get("partnerName").asText()).isEqualTo("テスト仕入先");
            assertThat(json.get("partnerType").asText()).isEqualTo("SUPPLIER");
            assertThat(json.get("isActive").asBoolean()).isTrue();
            assertThat(json.has("version")).isTrue();
            assertThat(json.has("id")).isTrue();

            // DB検証
            var dbRow = jdbcTemplate.queryForMap(
                    "SELECT partner_code, partner_name, partner_type, is_active FROM partners WHERE partner_code = 'IT-SUP01'");
            assertThat(dbRow.get("partner_code")).isEqualTo("IT-SUP01");
            assertThat(dbRow.get("partner_type")).isEqualTo("SUPPLIER");
            assertThat(dbRow.get("is_active")).isEqualTo(true);
        }

        @Test
        @DisplayName("出荷先を正常に登録できる")
        void create_customer_returns201() throws Exception {
            String body = """
                    {
                        "partnerCode": "IT-CUS01",
                        "partnerName": "テスト出荷先",
                        "partnerType": "CUSTOMER"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("partnerType").asText()).isEqualTo("CUSTOMER");
        }

        @Test
        @DisplayName("BOTH種別で登録できる")
        void create_both_returns201() throws Exception {
            String body = """
                    {
                        "partnerCode": "IT-BTH01",
                        "partnerName": "テスト兼用取引先",
                        "partnerType": "BOTH"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("partnerType").asText()).isEqualTo("BOTH");
        }

        @Test
        @DisplayName("SC-C06: コード重複 → 409 DUPLICATE_CODE")
        void create_duplicateCode_returns409() throws Exception {
            String body = """
                    {
                        "partnerCode": "SUP001",
                        "partnerName": "重複テスト",
                        "partnerType": "SUPPLIER"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DUPLICATE_CODE");
        }

        @Test
        @DisplayName("SC-C07: 必須フィールド未入力 → 400")
        void create_missingFields_returns400() throws Exception {
            String body = """
                    {
                        "partnerCode": "IT-MIS01"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("code")).isTrue();
        }
    }

    // ========================================================
    // GET /api/v1/master/partners/{id} — 詳細取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/partners/{id} — 取引先詳細取得")
    class GetPartner {

        @Test
        @DisplayName("正常系: 取引先の詳細を取得できる")
        void get_existingPartner_returns200() throws Exception {
            Long partnerId = jdbcTemplate.queryForObject(
                    "SELECT id FROM partners WHERE partner_code = 'SUP001'", Long.class);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + partnerId, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("partnerCode").asText()).isEqualTo("SUP001");
            assertThat(json.get("partnerType").asText()).isEqualTo("SUPPLIER");
            assertThat(json.has("version")).isTrue();
            assertThat(json.has("createdAt")).isTrue();
        }

        @Test
        @DisplayName("存在しないID → 404 PARTNER_NOT_FOUND")
        void get_nonExistentId_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("PARTNER_NOT_FOUND");
        }
    }

    // ========================================================
    // PUT /api/v1/master/partners/{id} — 取引先更新
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/master/partners/{id} — 取引先更新")
    class UpdatePartner {

        @Test
        @DisplayName("SC-C08: 正常系 — 名称を変更して更新できる")
        void update_validInput_returns200() throws Exception {
            Long partnerId = createTestPartner("IT-UPD01", "更新前取引先", "SUPPLIER");
            Integer version = getVersion(partnerId);

            String body = String.format("""
                    {
                        "partnerName": "更新後取引先",
                        "partnerNameKana": "コウシンゴトリヒキサキ",
                        "address": "大阪府大阪市2-2-2",
                        "phone": "06-9876-5432",
                        "contactPerson": "更新太郎",
                        "email": "updated@example.com",
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + partnerId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("partnerName").asText()).isEqualTo("更新後取引先");
            assertThat(json.get("address").asText()).isEqualTo("大阪府大阪市2-2-2");
            assertThat(json.get("version").asInt()).isEqualTo(version + 1);
        }

        @Test
        @DisplayName("SC-C09: 楽観ロック競合 → 409 OPTIMISTIC_LOCK_CONFLICT")
        void update_versionMismatch_returns409() throws Exception {
            Long partnerId = createTestPartner("IT-UPD02", "ロック競合テスト", "SUPPLIER");

            String body = """
                    {
                        "partnerName": "競合更新",
                        "version": 999
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + partnerId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }

        @Test
        @DisplayName("存在しないID → 404")
        void update_nonExistentId_returns404() throws Exception {
            String body = """
                    { "partnerName": "不存在", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ========================================================
    // PATCH /api/v1/master/partners/{id}/toggle-active
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/partners/{id}/toggle-active — 有効/無効切替")
    class ToggleActive {

        @Test
        @DisplayName("SC-C11: 取引先を無効化できる（伝票紐付けなし）")
        void toggle_deactivate_returns200() throws Exception {
            Long partnerId = createTestPartner("IT-TGL01", "無効化テスト", "SUPPLIER");
            Integer version = getVersion(partnerId);

            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + partnerId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isFalse();

            // DB検証
            Boolean isActive = jdbcTemplate.queryForObject(
                    "SELECT is_active FROM partners WHERE id = ?", Boolean.class, partnerId);
            assertThat(isActive).isFalse();
        }

        @Test
        @DisplayName("SC-C12: 無効な取引先を有効化できる")
        void toggle_activate_returns200() throws Exception {
            Long partnerId = createTestPartner("IT-TGL02", "有効化テスト", "SUPPLIER");
            Integer version = getVersion(partnerId);

            // 無効化（M-3: 中間ステップの結果を検証）
            String deactivateBody = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            ResponseEntity<String> deactivateResp = restTemplate.exchange(
                    BASE_URL + "/" + partnerId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(deactivateBody, adminHeaders), String.class);
            assertThat(deactivateResp.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 再有効化
            Integer newVersion = getVersion(partnerId);
            String activateBody = String.format("""
                    { "isActive": true, "version": %d }
                    """, newVersion);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + partnerId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(activateBody, adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isTrue();

            // M-1: DB検証
            Boolean isActive = jdbcTemplate.queryForObject(
                    "SELECT is_active FROM partners WHERE id = ?", Boolean.class, partnerId);
            assertThat(isActive).isTrue();
        }

        @Test
        @DisplayName("楽観ロック競合 → 409")
        void toggle_versionMismatch_returns409() throws Exception {
            Long partnerId = createTestPartner("IT-TGL03", "ロック競合", "SUPPLIER");

            String body = """
                    { "isActive": false, "version": 999 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + partnerId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ========================================================
    // GET /api/v1/master/partners — 一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/partners — 取引先一覧")
    class ListPartners {

        @Test
        @DisplayName("SC-C01: 初期表示でデータが一覧に表示される")
        void list_default_returnsPagedResults() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("content")).isTrue();
            assertThat(json.has("totalElements")).isTrue();
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("SC-PAR01: partnerType=SUPPLIERで仕入先+BOTH取引先が取得できる")
        void list_filterBySupplier_returnsSupplierAndBoth() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?partnerType=SUPPLIER&page=0&size=100", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                String type = item.get("partnerType").asText();
                assertThat(type).isIn("SUPPLIER", "BOTH");
            }
        }

        @Test
        @DisplayName("SC-PAR02: partnerType=CUSTOMERで出荷先+BOTH取引先が取得できる")
        void list_filterByCustomer_returnsCustomerAndBoth() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?partnerType=CUSTOMER&page=0&size=100", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                String type = item.get("partnerType").asText();
                assertThat(type).isIn("CUSTOMER", "BOTH");
            }
        }

        @Test
        @DisplayName("SC-C02: 取引先コードで絞り込みができる")
        void list_filterByCode_returnsFiltered() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?partnerCode=SUP001&page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isEqualTo(1);
        }

        @Test
        @DisplayName("all=trueでプルダウン用簡易一覧を取得（PII除外）")
        void list_allTrue_returnsSimpleList() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?all=true", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.isArray()).isTrue();
            assertThat(json.size()).isGreaterThanOrEqualTo(1);

            // PII除外確認
            JsonNode first = json.get(0);
            assertThat(first.has("id")).isTrue();
            assertThat(first.has("partnerCode")).isTrue();
            assertThat(first.has("partnerName")).isTrue();
            assertThat(first.has("partnerType")).isTrue();
        }

        @Test
        @DisplayName("SC-C04: ページネーションでページ切替ができる")
        void list_pagination_works() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=1", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isEqualTo(1);
            assertThat(json.get("totalPages").asInt()).isGreaterThan(1);
        }
    }

    // ========================================================
    // GET /api/v1/master/partners/exists — 重複チェック
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/partners/exists — 重複チェック")
    class CheckExists {

        @Test
        @DisplayName("既存コード → exists=true")
        void exists_existingCode_returnsTrue() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/exists?partnerCode=SUP001", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("exists").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("未使用コード → exists=false")
        void exists_newCode_returnsFalse() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/exists?partnerCode=ZZZZZ", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("exists").asBoolean()).isFalse();
        }
    }

    // ========================================================
    // アクセス制御テスト
    // ========================================================

    @Nested
    @DisplayName("アクセス制御")
    class AccessControl {

        @Test
        @DisplayName("SC-C13: WAREHOUSE_STAFFは作成不可 → 403")
        void create_asStaff_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    {
                        "partnerCode": "IT-STF01",
                        "partnerName": "スタッフ作成テスト",
                        "partnerType": "SUPPLIER"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, staffHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは更新不可 → 403")
        void update_asStaff_returns403() throws Exception {
            Long partnerId = createTestPartner("IT-STF02", "スタッフ更新テスト", "SUPPLIER");
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    { "partnerName": "スタッフ更新", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + partnerId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは有効/無効切替不可 → 403")
        void toggle_asStaff_returns403() throws Exception {
            Long partnerId = createTestPartner("IT-STF03", "スタッフ切替テスト", "SUPPLIER");
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    { "isActive": false, "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + partnerId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは一覧取得可能")
        void list_asStaff_returns200() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(staffHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("認証なしでアクセス → 401")
        void access_unauthenticated_returns401() {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(createJsonHeaders()), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ========================================================
    // 取引先無効化の業務制約 (API-03 BR-001)
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/partners/{id}/toggle-active — 業務制約 (BR-001)")
    class DeactivationBusinessRules {

        @ParameterizedTest(name = "SC-PAR03: 入荷伝票 status={0} の取引先は無効化不可")
        @ValueSource(strings = {"PLANNED", "CONFIRMED", "INSPECTING"})
        @DisplayName("SC-PAR03: 処理中入荷伝票がある取引先は無効化不可 → 422 CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND")
        void deactivate_withActiveInbound_returns422(String status) throws Exception {
            // M-4: partner_code は VARCHAR(50) のためステータス名をそのまま suffix に使い命名衝突を防ぐ
            String code = "IT-PAR03-" + status;
            Long partnerId = createTestPartner(code, "PAR03_" + status, "SUPPLIER");
            insertInboundSlip(code, partnerId, status);

            // M-5 / m-7: DRY 化した共通ヘルパ経由で version を動的取得しつつ 422 を検証
            assertDeactivationRejected(partnerId, "CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND");
        }

        // NOTE: 設計書 (API-03) では「処理中受注」を PENDING/ALLOCATED/PICKING/INSPECTING と
        //       記載しているが、実装/DB制約 (outbound_slips.status) には PENDING/PICKING が
        //       存在しないため、それぞれ ORDERED/PICKING_COMPLETED が対応する。
        //       チェック対象は SHIPPED/CANCELLED 以外の全ステータス (5種)。
        @ParameterizedTest(name = "SC-PAR04: 出荷伝票 status={0} の取引先は無効化不可")
        @ValueSource(strings = {"ORDERED", "PARTIAL_ALLOCATED", "ALLOCATED", "PICKING_COMPLETED", "INSPECTING"})
        @DisplayName("SC-PAR04: 処理中受注伝票がある取引先は無効化不可 → 422 CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND")
        void deactivate_withActiveOutbound_returns422(String status) throws Exception {
            // M-4: partner_code は VARCHAR(50) のためステータス名をそのまま suffix に使い命名衝突を防ぐ
            String code = "IT-PAR04-" + status;
            Long partnerId = createTestPartner(code, "PAR04_" + status, "CUSTOMER");
            insertOutboundSlip(code, partnerId, status);

            // M-5 / m-7: DRY 化した共通ヘルパ経由で version を動的取得しつつ 422 を検証
            assertDeactivationRejected(partnerId, "CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND");
        }

        @Test
        @DisplayName("SC-PAR05a: 完了済み入荷伝票 (STORED/CANCELLED) のみの取引先は無効化可能")
        void deactivate_withOnlyCompletedInbound_returns200() throws Exception {
            Long partnerId = createTestPartner("IT-PAR05A", "PAR05A完了", "SUPPLIER");
            insertInboundSlip("IT-PAR05A-S", partnerId, "STORED");
            insertInboundSlip("IT-PAR05A-C", partnerId, "CANCELLED");

            // C-R2-6: 対称な共通ヘルパ経由で 200 OK / version+1 / DB を検証
            assertDeactivationAccepted(partnerId);
        }

        @Test
        @DisplayName("SC-PAR05b: 完了済み受注伝票 (SHIPPED/CANCELLED) のみの取引先は無効化可能")
        void deactivate_withOnlyCompletedOutbound_returns200() throws Exception {
            Long partnerId = createTestPartner("IT-PAR05B", "PAR05B完了", "CUSTOMER");
            insertOutboundSlip("IT-PAR05B-S", partnerId, "SHIPPED");
            insertOutboundSlip("IT-PAR05B-C", partnerId, "CANCELLED");

            // C-R2-6: 対称な共通ヘルパ経由で 200 OK / version+1 / DB を検証
            assertDeactivationAccepted(partnerId);
        }

        @Test
        @DisplayName("SC-PAR05c: SUPPLIER 取引先に STORED 5件 + PLANNED 1件 (混在) → 422")
        void deactivate_withMixedInboundBoundary_returns422() throws Exception {
            // M-2: 完了系が多数あっても処理中が1件でも残っていれば無効化不可
            Long partnerId = createTestPartner("IT-PAR05C", "PAR05C混在", "SUPPLIER");
            for (int i = 1; i <= 5; i++) {
                insertInboundSlip("IT-PAR05C-S" + i, partnerId, "STORED");
            }
            insertInboundSlip("IT-PAR05C-P1", partnerId, "PLANNED");

            // m-7: DRY 化した共通ヘルパ経由で検証
            assertDeactivationRejected(partnerId, "CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND");
        }

        @Test
        @DisplayName("SC-PAR05d: CUSTOMER 取引先に SHIPPED 5件 + ORDERED 1件 (混在) → 422")
        void deactivate_withMixedOutboundBoundary_returns422() throws Exception {
            // M-2: 完了系が多数あっても処理中が1件でも残っていれば無効化不可
            Long partnerId = createTestPartner("IT-PAR05D", "PAR05D混在", "CUSTOMER");
            for (int i = 1; i <= 5; i++) {
                insertOutboundSlip("IT-PAR05D-S" + i, partnerId, "SHIPPED");
            }
            insertOutboundSlip("IT-PAR05D-O1", partnerId, "ORDERED");

            // m-7: DRY 化した共通ヘルパ経由で検証
            assertDeactivationRejected(partnerId, "CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND");
        }

        @Test
        @DisplayName("BR-001 BOTH取引先で入荷+受注両方処理中の場合は入荷エラーを優先")
        void deactivate_bothActive_inboundTakesPrecedence_returns422() throws Exception {
            // M-1: BOTH 取引先に入荷と受注の両方処理中伝票があるケース
            Long partnerId = createTestPartner("IT-PAR-BOTH", "BOTH取引先", "BOTH");
            insertInboundSlip("IT-PAR-BOTH-IN", partnerId, "PLANNED");
            insertOutboundSlip("IT-PAR-BOTH-OUT", partnerId, "ORDERED");

            // 入荷チェックが先に評価されるため、inbound エラーが返る
            assertDeactivationRejected(partnerId, "CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND");
        }
    }

    // ========================================================
    // ヘルパーメソッド
    // ========================================================

    /**
     * テスト用入荷伝票を直接INSERTする（業務ロジックを経由しない）。
     *
     * <p>warehouse / 作成ユーザは setUp でキャッシュ済みのデフォルト値を使用し、
     * N+1 を避けている (M-2)。</p>
     *
     * <p>created_at / updated_at は DB スキーマ
     * (V9__create_inbound_slips_table.sql) の DEFAULT now() に依存しており、
     * ここでは明示的に指定していない。テスト側で時刻ロジックを扱わないための
     * 意図的な単純化である (M-3)。</p>
     */
    private void insertInboundSlip(String slipNumber, Long partnerId, String status) {
        jdbcTemplate.update("""
                INSERT INTO inbound_slips
                  (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name,
                   partner_id, planned_date, status, created_by, updated_by)
                VALUES (?, 'NORMAL', ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slipNumber, defaultWarehouseId, defaultWarehouseCode, defaultWarehouseName,
                partnerId, LocalDate.now(), status, adminUserId, adminUserId);
    }

    /**
     * テスト用出荷伝票を直接INSERTする（業務ロジックを経由しない）。
     *
     * <p>warehouse / 作成ユーザは setUp でキャッシュ済みのデフォルト値を使用し、
     * N+1 を避けている (M-2)。</p>
     *
     * <p>created_at / updated_at は DB スキーマ
     * (V13__create_outbound_tables.sql) の DEFAULT now() に依存しており、
     * ここでは明示的に指定していない。テスト側で時刻ロジックを扱わないための
     * 意図的な単純化である (M-3 / D-R2-2)。</p>
     */
    private void insertOutboundSlip(String slipNumber, Long partnerId, String status) {
        jdbcTemplate.update("""
                INSERT INTO outbound_slips
                  (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name,
                   partner_id, planned_date, status, created_by, updated_by)
                VALUES (?, 'NORMAL', ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slipNumber, defaultWarehouseId, defaultWarehouseCode, defaultWarehouseName,
                partnerId, LocalDate.now(), status, adminUserId, adminUserId);
    }

    /**
     * 取引先の無効化リクエストが業務制約によって拒否されることを検証する共通ヘルパ (m-7)。
     * 現行 version を取得してリクエストを組み立て、422 とエラーコードおよび
     * DB 上で is_active が維持されていることを一括検証する。
     */
    private void assertDeactivationRejected(Long partnerId, String expectedCode) throws Exception {
        Integer version = getVersion(partnerId);
        String body = String.format("""
                { "isActive": false, "version": %d }
                """, version);
        HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/" + partnerId + "/toggle-active",
                HttpMethod.PATCH, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("code").asText()).isEqualTo(expectedCode);

        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM partners WHERE id = ?", Boolean.class, partnerId);
        assertThat(isActive).isTrue();
    }

    /**
     * 取引先の無効化リクエストが正常に受理されることを検証する共通ヘルパ (C-R2-6)。
     * {@link #assertDeactivationRejected} と対称な成功系。
     * 現行 version を取得してリクエストを組み立て、200 OK / response の isActive=false /
     * version+1 / DB 上の is_active=false を一括検証する。
     */
    private void assertDeactivationAccepted(Long partnerId) throws Exception {
        Integer version = getVersion(partnerId);
        String body = String.format("""
                { "isActive": false, "version": %d }
                """, version);
        HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/" + partnerId + "/toggle-active",
                HttpMethod.PATCH, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("isActive").asBoolean()).isFalse();
        assertThat(json.get("version").asInt()).isEqualTo(version + 1);

        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM partners WHERE id = ?", Boolean.class, partnerId);
        assertThat(isActive).isFalse();
    }

    private Long createTestPartner(String code, String name, String type) throws Exception {
        String body = String.format("""
                {
                    "partnerCode": "%s",
                    "partnerName": "%s",
                    "partnerType": "%s"
                }
                """, code, name, type);
        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Integer getVersion(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM partners WHERE id = ?", Integer.class, id);
    }
}
