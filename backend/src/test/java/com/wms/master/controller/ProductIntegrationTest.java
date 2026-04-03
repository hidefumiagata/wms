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

@DisplayName("マスタ結合テスト: 商品管理")
class ProductIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/master/products";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        // テスト用データのクリーンアップ
        jdbcTemplate.update("DELETE FROM products WHERE product_code LIKE 'IT-%'");

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    // ========================================================
    // POST /api/v1/master/products — 商品作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/master/products — 商品作成")
    class CreateProduct {

        @Test
        @DisplayName("SC-C05: 常温商品を正常に登録できる")
        void create_ambientProduct_returns201() throws Exception {
            String body = """
                    {
                        "productCode": "IT-AMB01",
                        "productName": "テスト常温商品",
                        "productNameKana": "テストジョウオンショウヒン",
                        "caseQuantity": 12,
                        "ballQuantity": 6,
                        "barcode": "4901234567890",
                        "storageCondition": "AMBIENT",
                        "isHazardous": false,
                        "lotManageFlag": false,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": false
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("productCode").asText()).isEqualTo("IT-AMB01");
            assertThat(json.get("productName").asText()).isEqualTo("テスト常温商品");
            assertThat(json.get("storageCondition").asText()).isEqualTo("AMBIENT");
            assertThat(json.get("caseQuantity").asInt()).isEqualTo(12);
            assertThat(json.get("ballQuantity").asInt()).isEqualTo(6);
            assertThat(json.get("lotManageFlag").asBoolean()).isFalse();
            assertThat(json.get("expiryManageFlag").asBoolean()).isFalse();
            assertThat(json.get("isActive").asBoolean()).isTrue();
            assertThat(json.has("version")).isTrue();

            // DB検証
            var dbRow = jdbcTemplate.queryForMap(
                    "SELECT product_code, storage_condition, is_active FROM products WHERE product_code = 'IT-AMB01'");
            assertThat(dbRow.get("product_code")).isEqualTo("IT-AMB01");
            assertThat(dbRow.get("storage_condition")).isEqualTo("AMBIENT");
            assertThat(dbRow.get("is_active")).isEqualTo(true);
        }

        @Test
        @DisplayName("冷蔵商品（ロット・賞味期限管理ON）を登録できる")
        void create_refrigeratedWithLotExpiry_returns201() throws Exception {
            String body = """
                    {
                        "productCode": "IT-REF01",
                        "productName": "テスト冷蔵商品",
                        "caseQuantity": 6,
                        "ballQuantity": 3,
                        "storageCondition": "REFRIGERATED",
                        "isHazardous": false,
                        "lotManageFlag": true,
                        "expiryManageFlag": true,
                        "shipmentStopFlag": false
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("storageCondition").asText()).isEqualTo("REFRIGERATED");
            assertThat(json.get("lotManageFlag").asBoolean()).isTrue();
            assertThat(json.get("expiryManageFlag").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("冷凍商品を登録できる")
        void create_frozenProduct_returns201() throws Exception {
            String body = """
                    {
                        "productCode": "IT-FRZ01",
                        "productName": "テスト冷凍商品",
                        "caseQuantity": 4,
                        "ballQuantity": 2,
                        "storageCondition": "FROZEN",
                        "isHazardous": false,
                        "lotManageFlag": true,
                        "expiryManageFlag": true,
                        "shipmentStopFlag": false
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("storageCondition").asText()).isEqualTo("FROZEN");
        }

        @Test
        @DisplayName("SC-C06: コード重複 → 409 DUPLICATE_CODE")
        void create_duplicateCode_returns409() throws Exception {
            String body = """
                    {
                        "productCode": "AMB-001",
                        "productName": "重複テスト",
                        "caseQuantity": 1,
                        "ballQuantity": 1,
                        "storageCondition": "AMBIENT",
                        "isHazardous": false,
                        "lotManageFlag": false,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": false
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
                        "productCode": "IT-MIS01"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("code")).isTrue();
        }
    }

    // ========================================================
    // GET /api/v1/master/products/{id} — 詳細取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/products/{id} — 商品詳細取得")
    class GetProduct {

        @Test
        @DisplayName("正常系: 商品の詳細（hasInventory含む）を取得できる")
        void get_existingProduct_returns200() throws Exception {
            Long productId = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = 'AMB-001'", Long.class);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("productCode").asText()).isEqualTo("AMB-001");
            assertThat(json.has("version")).isTrue();
            assertThat(json.has("hasInventory")).isTrue();
        }

        @Test
        @DisplayName("存在しないID → 404 PRODUCT_NOT_FOUND")
        void get_nonExistentId_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
        }
    }

    // ========================================================
    // PUT /api/v1/master/products/{id} — 商品更新
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/master/products/{id} — 商品更新")
    class UpdateProduct {

        @Test
        @DisplayName("SC-C08: 正常系 — 名称等を変更して更新できる")
        void update_validInput_returns200() throws Exception {
            Long productId = createTestProduct("IT-UPD01", "更新前商品", "AMBIENT",
                    false, false);
            Integer version = getVersion(productId);

            String body = String.format("""
                    {
                        "productName": "更新後商品",
                        "productNameKana": "コウシンゴショウヒン",
                        "caseQuantity": 24,
                        "ballQuantity": 12,
                        "storageCondition": "AMBIENT",
                        "isHazardous": true,
                        "lotManageFlag": false,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": false,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("productName").asText()).isEqualTo("更新後商品");
            assertThat(json.get("caseQuantity").asInt()).isEqualTo(24);
            assertThat(json.get("version").asInt()).isEqualTo(version + 1);
        }

        @Test
        @DisplayName("SC-PRD03: 在庫なしの商品のロット管理フラグを変更できる")
        void update_changeLotFlag_noInventory_returns200() throws Exception {
            Long productId = createTestProduct("IT-UPD02", "ロットフラグ変更", "AMBIENT",
                    false, false);
            Integer version = getVersion(productId);

            String body = String.format("""
                    {
                        "productName": "ロットフラグ変更",
                        "caseQuantity": 1,
                        "ballQuantity": 1,
                        "storageCondition": "AMBIENT",
                        "isHazardous": false,
                        "lotManageFlag": true,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": false,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("lotManageFlag").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("SC-PRD05: 出荷禁止フラグをON/OFFに変更できる")
        void update_toggleShipmentStopFlag_returns200() throws Exception {
            Long productId = createTestProduct("IT-UPD03", "出荷禁止テスト", "AMBIENT",
                    false, false);
            Integer version = getVersion(productId);

            // ON
            String bodyOn = String.format("""
                    {
                        "productName": "出荷禁止テスト",
                        "caseQuantity": 1,
                        "ballQuantity": 1,
                        "storageCondition": "AMBIENT",
                        "isHazardous": false,
                        "lotManageFlag": false,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": true,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> requestOn = new HttpEntity<>(bodyOn, adminHeaders);
            ResponseEntity<String> responseOn = restTemplate.exchange(
                    BASE_URL + "/" + productId, HttpMethod.PUT, requestOn, String.class);

            assertThat(responseOn.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode jsonOn = parseJson(responseOn.getBody());
            assertThat(jsonOn.get("shipmentStopFlag").asBoolean()).isTrue();

            // OFF
            Integer newVersion = getVersion(productId);
            String bodyOff = String.format("""
                    {
                        "productName": "出荷禁止テスト",
                        "caseQuantity": 1,
                        "ballQuantity": 1,
                        "storageCondition": "AMBIENT",
                        "isHazardous": false,
                        "lotManageFlag": false,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": false,
                        "version": %d
                    }
                    """, newVersion);

            HttpEntity<String> requestOff = new HttpEntity<>(bodyOff, adminHeaders);
            ResponseEntity<String> responseOff = restTemplate.exchange(
                    BASE_URL + "/" + productId, HttpMethod.PUT, requestOff, String.class);

            assertThat(responseOff.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode jsonOff = parseJson(responseOff.getBody());
            assertThat(jsonOff.get("shipmentStopFlag").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("SC-PRD03: 在庫なしの商品の賞味期限管理フラグを変更できる")
        void update_changeExpiryFlag_noInventory_returns200() throws Exception {
            Long productId = createTestProduct("IT-UPD05", "賞味期限フラグ変更", "REFRIGERATED",
                    false, false);
            Integer version = getVersion(productId);

            String body = String.format("""
                    {
                        "productName": "賞味期限フラグ変更",
                        "caseQuantity": 1,
                        "ballQuantity": 1,
                        "storageCondition": "REFRIGERATED",
                        "isHazardous": false,
                        "lotManageFlag": false,
                        "expiryManageFlag": true,
                        "shipmentStopFlag": false,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("expiryManageFlag").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("SC-C09: 楽観ロック競合 → 409")
        void update_versionMismatch_returns409() throws Exception {
            Long productId = createTestProduct("IT-UPD04", "ロック競合", "AMBIENT",
                    false, false);

            String body = """
                    {
                        "productName": "競合更新",
                        "caseQuantity": 1,
                        "ballQuantity": 1,
                        "storageCondition": "AMBIENT",
                        "isHazardous": false,
                        "lotManageFlag": false,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": false,
                        "version": 999
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }
    }

    // ========================================================
    // PATCH /api/v1/master/products/{id}/toggle-active
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/products/{id}/toggle-active — 有効/無効切替")
    class ToggleActive {

        @Test
        @DisplayName("SC-C11: 在庫なしの商品を無効化できる")
        void toggle_deactivate_noInventory_returns200() throws Exception {
            Long productId = createTestProduct("IT-TGL01", "無効化テスト", "AMBIENT",
                    false, false);
            Integer version = getVersion(productId);

            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("SC-C12: 無効な商品を有効化できる")
        void toggle_activate_returns200() throws Exception {
            Long productId = createTestProduct("IT-TGL02", "有効化テスト", "AMBIENT",
                    false, false);
            Integer version = getVersion(productId);

            // 無効化（M-3: 中間ステップの結果を検証）
            String deactivateBody = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            ResponseEntity<String> deactivateResp = restTemplate.exchange(
                    BASE_URL + "/" + productId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(deactivateBody, adminHeaders), String.class);
            assertThat(deactivateResp.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 再有効化
            Integer newVersion = getVersion(productId);
            String activateBody = String.format("""
                    { "isActive": true, "version": %d }
                    """, newVersion);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(activateBody, adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isTrue();

            // M-1: DB検証
            Boolean isActive = jdbcTemplate.queryForObject(
                    "SELECT is_active FROM products WHERE id = ?", Boolean.class, productId);
            assertThat(isActive).isTrue();
        }

        @Test
        @DisplayName("M-2: 楽観ロック競合 → 409")
        void toggle_versionMismatch_returns409() throws Exception {
            Long productId = createTestProduct("IT-TGL03", "ロック競合", "AMBIENT",
                    false, false);

            String body = """
                    { "isActive": false, "version": 999 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ========================================================
    // GET /api/v1/master/products — 一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/products — 商品一覧")
    class ListProducts {

        @Test
        @DisplayName("SC-C01: 初期表示でデータが一覧に表示される")
        void list_default_returnsPagedResults() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("content")).isTrue();
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("保管条件で絞り込み")
        void list_filterByStorageCondition_returnsFiltered() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?storageCondition=FROZEN&page=0&size=100", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("storageCondition").asText()).isEqualTo("FROZEN");
            }
        }

        @Test
        @DisplayName("商品コードで前方一致検索")
        void list_filterByCode_returnsFiltered() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?productCode=AMB-001&page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("all=trueでプルダウン用簡易一覧を取得")
        void list_allTrue_returnsSimpleList() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?all=true", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.isArray()).isTrue();
            assertThat(json.size()).isGreaterThanOrEqualTo(1);

            // M-4: フィールド検証
            JsonNode first = json.get(0);
            assertThat(first.has("id")).isTrue();
            assertThat(first.has("productCode")).isTrue();
            assertThat(first.has("productName")).isTrue();
            assertThat(first.has("isActive")).isTrue();
        }

        @Test
        @DisplayName("SC-C04: ページネーション")
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
    // GET /api/v1/master/products/exists — 重複チェック
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/products/exists — 重複チェック")
    class CheckExists {

        @Test
        @DisplayName("既存コード → exists=true")
        void exists_existingCode_returnsTrue() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/exists?productCode=AMB-001", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("exists").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("未使用コード → exists=false")
        void exists_newCode_returnsFalse() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/exists?productCode=ZZZZZ", HttpMethod.GET,
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
                        "productCode": "IT-STF01",
                        "productName": "スタッフ作成テスト",
                        "caseQuantity": 1,
                        "ballQuantity": 1,
                        "storageCondition": "AMBIENT",
                        "isHazardous": false,
                        "lotManageFlag": false,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": false
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, staffHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは更新不可 → 403")
        void update_asStaff_returns403() throws Exception {
            Long productId = createTestProduct("IT-STF02", "スタッフ更新テスト", "AMBIENT",
                    false, false);
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    {
                        "productName": "スタッフ更新",
                        "caseQuantity": 1,
                        "ballQuantity": 1,
                        "storageCondition": "AMBIENT",
                        "isHazardous": false,
                        "lotManageFlag": false,
                        "expiryManageFlag": false,
                        "shipmentStopFlag": false,
                        "version": 0
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは有効/無効切替不可 → 403")
        void toggle_asStaff_returns403() throws Exception {
            Long productId = createTestProduct("IT-STF03", "スタッフ切替テスト", "AMBIENT",
                    false, false);
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    { "isActive": false, "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + productId + "/toggle-active",
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

    private Long createTestProduct(String code, String name, String storageCondition,
                                   boolean lotManage, boolean expiryManage) throws Exception {
        String body = String.format("""
                {
                    "productCode": "%s",
                    "productName": "%s",
                    "caseQuantity": 1,
                    "ballQuantity": 1,
                    "storageCondition": "%s",
                    "isHazardous": false,
                    "lotManageFlag": %s,
                    "expiryManageFlag": %s,
                    "shipmentStopFlag": false
                }
                """, code, name, storageCondition, lotManage, expiryManage);
        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Integer getVersion(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM products WHERE id = ?", Integer.class, id);
    }
}
