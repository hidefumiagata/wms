package com.wms.master.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.shared.test.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("マスタ結合テスト: 取引先管理")
class PartnerIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/master/partners";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        // テスト用データのクリーンアップ
        jdbcTemplate.update("DELETE FROM partners WHERE partner_code LIKE 'IT-%'");

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
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
    // ヘルパーメソッド
    // ========================================================

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
