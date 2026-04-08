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

@DisplayName("マスタ結合テスト: 倉庫管理")
class WarehouseIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/master/warehouses";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String MANAGER_CODE = "wh_manager01";
    private static final String MANAGER_PASSWORD = "Manager@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";
    private static final String VIEWER_CODE = "viewer01";
    private static final String VIEWER_PASSWORD = "Test@1234";

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        // テスト用に作成した倉庫をクリーンアップ（テストデータのWH001は残す）
        // 在庫 → ロケーション → エリア → 棟 → 倉庫 の順で FK 制約を守る
        jdbcTemplate.update("DELETE FROM inventories WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM locations WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM areas WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM buildings WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM warehouses WHERE warehouse_code LIKE 'IT%'");

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    // ========================================================
    // POST /api/v1/master/warehouses — 倉庫作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/master/warehouses — 倉庫作成")
    class CreateWarehouse {

        @Test
        @DisplayName("SC-C05: 正常系 — 必須項目を入力して倉庫を登録できる")
        void create_validInput_returns201() throws Exception {
            String body = """
                    {
                        "warehouseCode": "ITWA",
                        "warehouseName": "結合テスト倉庫A",
                        "warehouseNameKana": "ケツゴウテストソウコエー",
                        "address": "東京都千代田区1-1-1"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();
            assertThat(response.getHeaders().getLocation().getPath())
                    .startsWith("/api/v1/master/warehouses/");

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("warehouseCode").asText()).isEqualTo("ITWA");
            assertThat(json.get("warehouseName").asText()).isEqualTo("結合テスト倉庫A");
            assertThat(json.get("warehouseNameKana").asText()).isEqualTo("ケツゴウテストソウコエー");
            assertThat(json.get("address").asText()).isEqualTo("東京都千代田区1-1-1");
            assertThat(json.get("isActive").asBoolean()).isTrue();
            assertThat(json.has("version")).isTrue();
            assertThat(json.has("id")).isTrue();

            // DB検証
            var dbRow = jdbcTemplate.queryForMap(
                    "SELECT warehouse_code, warehouse_name, is_active FROM warehouses WHERE warehouse_code = 'ITWA'");
            assertThat(dbRow.get("warehouse_code")).isEqualTo("ITWA");
            assertThat(dbRow.get("warehouse_name")).isEqualTo("結合テスト倉庫A");
            assertThat(dbRow.get("is_active")).isEqualTo(true);
        }

        @Test
        @DisplayName("SC-C06: コード重複 → 409 DUPLICATE_CODE")
        void create_duplicateCode_returns409() throws Exception {
            String body = """
                    {
                        "warehouseCode": "WH001",
                        "warehouseName": "重複テスト倉庫"
                    }
                    """;

            // WH001 is pre-loaded test data
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
                        "warehouseCode": "ITMA"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("code")).isTrue();
        }

        @Test
        @DisplayName("不正なコード形式 → 400")
        void create_invalidCodePattern_returns400() throws Exception {
            String body = """
                    {
                        "warehouseCode": "toolong",
                        "warehouseName": "不正コードテスト"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("code")).isTrue();
        }

        @Test
        @DisplayName("WAREHOUSE_MANAGERでも作成できる")
        void create_asManager_returns201() throws Exception {
            HttpHeaders managerHeaders = loginAndGetHeaders(MANAGER_CODE, MANAGER_PASSWORD);
            String body = """
                    {
                        "warehouseCode": "ITMB",
                        "warehouseName": "マネージャー作成テスト"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, managerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // ========================================================
    // GET /api/v1/master/warehouses/{id} — 詳細取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/warehouses/{id} — 倉庫詳細取得")
    class GetWarehouse {

        @Test
        @DisplayName("正常系: 存在する倉庫の詳細を取得できる")
        void get_existingWarehouse_returns200() throws Exception {
            Long warehouseId = jdbcTemplate.queryForObject(
                    "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + warehouseId, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("warehouseCode").asText()).isEqualTo("WH001");
            assertThat(json.get("warehouseName").asText()).isEqualTo("メイン倉庫");
            assertThat(json.get("isActive").asBoolean()).isTrue();
            assertThat(json.has("version")).isTrue();
            assertThat(json.has("createdAt")).isTrue();
            assertThat(json.has("updatedAt")).isTrue();
        }

        @Test
        @DisplayName("存在しないID → 404 WAREHOUSE_NOT_FOUND")
        void get_nonExistentId_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("WAREHOUSE_NOT_FOUND");
        }
    }

    // ========================================================
    // PUT /api/v1/master/warehouses/{id} — 倉庫更新
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/master/warehouses/{id} — 倉庫更新")
    class UpdateWarehouse {

        @Test
        @DisplayName("SC-C08: 正常系 — 名称を変更して更新できる")
        void update_validInput_returns200() throws Exception {
            Long whId = createTestWarehouse("ITUA", "更新前倉庫");
            Integer version = getWarehouseVersion(whId);

            String body = String.format("""
                    {
                        "warehouseName": "更新後倉庫",
                        "warehouseNameKana": "コウシンゴソウコ",
                        "address": "大阪府大阪市1-1-1",
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("warehouseName").asText()).isEqualTo("更新後倉庫");
            assertThat(json.get("warehouseNameKana").asText()).isEqualTo("コウシンゴソウコ");
            assertThat(json.get("address").asText()).isEqualTo("大阪府大阪市1-1-1");
            assertThat(json.get("version").asInt()).isEqualTo(version + 1);
        }

        @Test
        @DisplayName("SC-C09: 楽観ロック競合 → 409 OPTIMISTIC_LOCK_CONFLICT")
        void update_versionMismatch_returns409() throws Exception {
            Long whId = createTestWarehouse("ITUB", "ロック競合テスト");

            String body = """
                    {
                        "warehouseName": "競合更新",
                        "version": 999
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }

        @Test
        @DisplayName("存在しないID → 404")
        void update_nonExistentId_returns404() throws Exception {
            String body = """
                    {
                        "warehouseName": "更新テスト",
                        "version": 0
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ========================================================
    // PATCH /api/v1/master/warehouses/{id}/toggle-active
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/warehouses/{id}/toggle-active — 有効/無効切替")
    class ToggleActive {

        @Test
        @DisplayName("SC-C11: 有効な倉庫を無効化できる")
        void toggle_deactivate_returns200() throws Exception {
            Long whId = createTestWarehouse("ITTA", "無効化テスト");
            Integer version = getWarehouseVersion(whId);

            String body = String.format("""
                    {
                        "isActive": false,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId + "/toggle-active", HttpMethod.PATCH,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isFalse();

            // DB検証
            Boolean isActive = jdbcTemplate.queryForObject(
                    "SELECT is_active FROM warehouses WHERE id = ?", Boolean.class, whId);
            assertThat(isActive).isFalse();
        }

        @Test
        @DisplayName("SC-C12: 無効な倉庫を有効化できる")
        void toggle_activate_returns200() throws Exception {
            Long whId = createTestWarehouse("ITTB", "有効化テスト");
            Integer version = getWarehouseVersion(whId);

            // まず無効化
            String deactivateBody = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            HttpEntity<String> deactivateReq = new HttpEntity<>(deactivateBody, adminHeaders);
            restTemplate.exchange(BASE_URL + "/" + whId + "/toggle-active",
                    HttpMethod.PATCH, deactivateReq, String.class);

            // 再有効化
            Integer newVersion = getWarehouseVersion(whId);
            String activateBody = String.format("""
                    { "isActive": true, "version": %d }
                    """, newVersion);
            HttpEntity<String> activateReq = new HttpEntity<>(activateBody, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId + "/toggle-active",
                    HttpMethod.PATCH, activateReq, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("SC-WH01: 在庫が存在する倉庫の無効化 → 422 CANNOT_DEACTIVATE_HAS_INVENTORY")
        void toggle_deactivate_withInventory_returns422() throws Exception {
            // 倉庫/棟/エリア/ロケーション を作成
            Long whId = createTestWarehouse("ITWH", "在庫あり無効化テスト");
            Long buildingId = createTestBuilding(whId, "A", "A棟");
            Long areaId = createTestArea(buildingId, "A01", "在庫エリア", "AMBIENT", "STOCK");
            Long locationId = createTestLocation(areaId, "A-01-A01-01-01-01", "在庫保管ロケ");

            // 在庫を直接 INSERT
            Long productId = jdbcTemplate.queryForObject(
                    "SELECT id FROM products ORDER BY id LIMIT 1", Long.class);
            jdbcTemplate.update(
                    "INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type, " +
                            "quantity, allocated_qty, updated_at) " +
                            "VALUES (?, ?, ?, 'PIECE', 10, 0, now())",
                    whId, locationId, productId);

            // toggle-active で無効化リクエスト
            Integer version = getWarehouseVersion(whId);
            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            // 422 + CANNOT_DEACTIVATE_HAS_INVENTORY
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("CANNOT_DEACTIVATE_HAS_INVENTORY");

            // DB検証: is_active は true のまま
            Boolean isActive = jdbcTemplate.queryForObject(
                    "SELECT is_active FROM warehouses WHERE id = ?", Boolean.class, whId);
            assertThat(isActive).isTrue();
        }

        @Test
        @DisplayName("BR-001境界値: 在庫レコードはあるが quantity=0 の倉庫は無効化成功 (200)")
        void toggle_deactivate_withZeroQtyInventory_returns200() throws Exception {
            // 倉庫/棟/エリア/ロケーション を作成
            Long whId = createTestWarehouse("ITWZ", "ゼロ在庫無効化テスト");
            Long buildingId = createTestBuilding(whId, "Z", "Z棟");
            Long areaId = createTestArea(buildingId, "Z01", "ゼロ在庫エリア", "AMBIENT", "STOCK");
            Long locationId = createTestLocation(areaId, "Z-01-Z01-01-01-01", "ゼロ在庫ロケ");

            // quantity=0 の在庫レコードを INSERT（レコードは存在するが数量0）
            Long productId = jdbcTemplate.queryForObject(
                    "SELECT id FROM products ORDER BY id LIMIT 1", Long.class);
            jdbcTemplate.update(
                    "INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type, " +
                            "quantity, allocated_qty, updated_at) " +
                            "VALUES (?, ?, ?, 'PIECE', 0, 0, now())",
                    whId, locationId, productId);

            // toggle-active で無効化リクエスト
            Integer version = getWarehouseVersion(whId);
            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            // 200 + is_active=false（quantity=0 は在庫なしと判定される）
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isFalse();

            // DB検証
            Boolean isActive = jdbcTemplate.queryForObject(
                    "SELECT is_active FROM warehouses WHERE id = ?", Boolean.class, whId);
            assertThat(isActive).isFalse();
        }

        @Test
        @DisplayName("楽観ロック競合 → 409")
        void toggle_versionMismatch_returns409() throws Exception {
            Long whId = createTestWarehouse("ITTC", "ロック競合テスト");

            String body = """
                    { "isActive": false, "version": 999 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ========================================================
    // GET /api/v1/master/warehouses — 一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/warehouses — 倉庫一覧")
    class ListWarehouses {

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
            assertThat(json.has("totalPages")).isTrue();
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("SC-C02: 倉庫コードで絞り込みができる")
        void list_filterByCode_returnsFiltered() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseCode=WH001&page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isEqualTo(1);
            assertThat(json.get("content").get(0).get("warehouseCode").asText()).isEqualTo("WH001");
        }

        @Test
        @DisplayName("all=trueでプルダウン用簡易一覧を取得できる")
        void list_allTrue_returnsSimpleList() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?all=true", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            // all=true returns array directly (not paginated)
            assertThat(json.isArray()).isTrue();
            assertThat(json.size()).isGreaterThanOrEqualTo(1);

            // 簡易フィールドのみ
            JsonNode first = json.get(0);
            assertThat(first.has("id")).isTrue();
            assertThat(first.has("warehouseCode")).isTrue();
            assertThat(first.has("warehouseName")).isTrue();
            assertThat(first.has("isActive")).isTrue();
        }

        @Test
        @DisplayName("isActiveフィルタで有効のみ取得")
        void list_filterByActive_returnsOnlyActive() throws Exception {
            // 無効倉庫を作成
            Long whId = createTestWarehouse("ITLA", "無効倉庫テスト");
            Integer version = getWarehouseVersion(whId);
            String toggleBody = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            HttpEntity<String> toggleReq = new HttpEntity<>(toggleBody, adminHeaders);
            restTemplate.exchange(BASE_URL + "/" + whId + "/toggle-active",
                    HttpMethod.PATCH, toggleReq, String.class);

            // isActive=true でフィルタ
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?isActive=true&page=0&size=100", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());

            // 全件が isActive=true であること
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("isActive").asBoolean()).isTrue();
            }
        }

        @Test
        @DisplayName("SC-C04: ページネーションでページ切替ができる")
        void list_pagination_works() throws Exception {
            // 追加で倉庫を作成してページネーションテスト
            createTestWarehouse("ITP1", "ページネーション1");
            createTestWarehouse("ITP2", "ページネーション2");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=1", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isEqualTo(1);
            assertThat(json.get("size").asInt()).isEqualTo(1);
            assertThat(json.get("totalPages").asInt()).isGreaterThan(1);
        }
    }

    // ========================================================
    // GET /api/v1/master/warehouses/exists — 重複チェック
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/warehouses/exists — 重複チェック")
    class CheckExists {

        @Test
        @DisplayName("既存コードで重複チェック → exists=true")
        void exists_existingCode_returnsTrue() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/exists?warehouseCode=WH001", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("exists").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("未使用コードで重複チェック → exists=false")
        void exists_newCode_returnsFalse() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/exists?warehouseCode=ZZZZ", HttpMethod.GET,
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
                        "warehouseCode": "ITSF",
                        "warehouseName": "スタッフ作成テスト"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, staffHeaders);

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
        @DisplayName("WAREHOUSE_STAFFは更新不可 → 403")
        void update_asStaff_returns403() throws Exception {
            Long whId = createTestWarehouse("ITSU", "スタッフ更新テスト");
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    { "warehouseName": "スタッフ更新", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは有効/無効切替不可 → 403")
        void toggle_asStaff_returns403() throws Exception {
            Long whId = createTestWarehouse("ITST", "スタッフ切替テスト");
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    { "isActive": false, "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("VIEWERは一覧取得可能")
        void list_asViewer_returns200() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(viewerHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("VIEWERは詳細取得可能")
        void get_asViewer_returns200() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);
            Long warehouseId = jdbcTemplate.queryForObject(
                    "SELECT id FROM warehouses WHERE warehouse_code = 'WH001'", Long.class);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + warehouseId, HttpMethod.GET,
                    new HttpEntity<>(viewerHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("VIEWERは作成不可 → 403")
        void create_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);
            String body = """
                    {
                        "warehouseCode": "ITVW",
                        "warehouseName": "ビューワー作成テスト"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, viewerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("VIEWERは更新不可 → 403")
        void update_asViewer_returns403() throws Exception {
            Long whId = createTestWarehouse("ITVU", "ビューワー更新テスト");
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);
            String body = """
                    { "warehouseName": "ビューワー更新", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, viewerHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("VIEWERは有効/無効切替不可 → 403")
        void toggle_asViewer_returns403() throws Exception {
            Long whId = createTestWarehouse("ITVT", "ビューワー切替テスト");
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);
            String body = """
                    { "isActive": false, "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, viewerHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + whId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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

    private Long createTestWarehouse(String code, String name) throws Exception {
        String body = String.format("""
                {
                    "warehouseCode": "%s",
                    "warehouseName": "%s"
                }
                """, code, name);
        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = parseJson(response.getBody());
        return json.get("id").asLong();
    }

    private Integer getWarehouseVersion(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM warehouses WHERE id = ?", Integer.class, id);
    }

    private Long createTestBuilding(Long warehouseId, String code, String name) throws Exception {
        String body = String.format("""
                { "warehouseId": %d, "buildingCode": "%s", "buildingName": "%s" }
                """, warehouseId, code, name);
        ResponseEntity<String> response = postJson("/api/v1/master/buildings", body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Long createTestArea(Long buildingId, String code, String name,
                                String storageCondition, String areaType) throws Exception {
        String body = String.format("""
                {
                    "buildingId": %d, "areaCode": "%s", "areaName": "%s",
                    "storageCondition": "%s", "areaType": "%s"
                }
                """, buildingId, code, name, storageCondition, areaType);
        ResponseEntity<String> response = postJson("/api/v1/master/areas", body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Long createTestLocation(Long areaId, String code, String name) throws Exception {
        String body = String.format("""
                { "areaId": %d, "locationCode": "%s", "locationName": "%s" }
                """, areaId, code, name);
        ResponseEntity<String> response = postJson("/api/v1/master/locations", body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }
}
