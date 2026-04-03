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

@DisplayName("マスタ結合テスト: ロケーション管理")
class LocationIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/master/locations";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";

    private HttpHeaders adminHeaders;
    private Long testWarehouseId;
    private Long testBuildingId;
    private Long testStockAreaId;
    private Long testInboundAreaId;

    @BeforeEach
    void setUp() throws Exception {
        // テスト用データのクリーンアップ
        jdbcTemplate.update("DELETE FROM locations WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM areas WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM buildings WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM warehouses WHERE warehouse_code LIKE 'IT%'");

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);

        // テスト用階層を作成
        testWarehouseId = createWarehouse("ITLW", "ロケーションテスト用倉庫");
        testBuildingId = createBuilding(testWarehouseId, "A", "A棟");
        testStockAreaId = createArea(testBuildingId, "A01", "在庫エリア", "AMBIENT", "STOCK");
        testInboundAreaId = createArea(testBuildingId, "INB", "入荷エリア", "AMBIENT", "INBOUND");
    }

    // ========================================================
    // POST /api/v1/master/locations — ロケーション作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/master/locations — ロケーション作成")
    class CreateLocation {

        @Test
        @DisplayName("SC-FAC07: 在庫エリアにコード形式で登録できる")
        void create_stockArea_validFormat_returns201() throws Exception {
            String body = String.format("""
                    {
                        "areaId": %d,
                        "locationCode": "A-01-A01-01-01-01",
                        "locationName": "テストロケーション"
                    }
                    """, testStockAreaId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("locationCode").asText()).isEqualTo("A-01-A01-01-01-01");
            assertThat(json.get("locationName").asText()).isEqualTo("テストロケーション");
            assertThat(json.get("isActive").asBoolean()).isTrue();
            assertThat(json.has("version")).isTrue();

            // DB検証: warehouseId が自動設定されていること
            Long dbWarehouseId = jdbcTemplate.queryForObject(
                    "SELECT warehouse_id FROM locations WHERE location_code = 'A-01-A01-01-01-01' AND warehouse_id = ?",
                    Long.class, testWarehouseId);
            assertThat(dbWarehouseId).isEqualTo(testWarehouseId);
        }

        @Test
        @DisplayName("在庫エリアに不正コード形式 → 422 INVALID_LOCATION_CODE_FORMAT")
        void create_stockArea_invalidFormat_returns422() throws Exception {
            String body = String.format("""
                    {
                        "areaId": %d,
                        "locationCode": "INVALID-CODE",
                        "locationName": "不正コード"
                    }
                    """, testStockAreaId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVALID_LOCATION_CODE_FORMAT");
        }

        @Test
        @DisplayName("入荷エリアに1件目のロケーションを作成できる")
        void create_inboundArea_firstLocation_returns201() throws Exception {
            String body = String.format("""
                    {
                        "areaId": %d,
                        "locationCode": "A-01-INB-01"
                    }
                    """, testInboundAreaId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("SC-FAC09: 入荷エリアに2件目のロケーション → 422 AREA_LOCATION_LIMIT_EXCEEDED")
        void create_inboundArea_secondLocation_returns422() throws Exception {
            // 1件目作成
            createLocation(testInboundAreaId, "A-01-INB-01", null);

            // 2件目
            String body = String.format("""
                    {
                        "areaId": %d,
                        "locationCode": "A-01-INB-02"
                    }
                    """, testInboundAreaId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("AREA_LOCATION_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("SC-FAC08: 倉庫内コード重複 → 409 DUPLICATE_CODE")
        void create_duplicateCodeInWarehouse_returns409() throws Exception {
            createLocation(testStockAreaId, "A-01-A01-01-01-01", "元ロケーション");

            // 同じ倉庫内で同じコード
            String body = String.format("""
                    {
                        "areaId": %d,
                        "locationCode": "A-01-A01-01-01-01",
                        "locationName": "重複ロケーション"
                    }
                    """, testStockAreaId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DUPLICATE_CODE");
        }

        @Test
        @DisplayName("存在しないエリアID → 404")
        void create_invalidAreaId_returns404() throws Exception {
            String body = """
                    {
                        "areaId": 999999,
                        "locationCode": "A-01-X01-01-01-01"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("必須フィールド未入力 → 400")
        void create_missingFields_returns400() throws Exception {
            String body = String.format("""
                    {
                        "areaId": %d
                    }
                    """, testStockAreaId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ========================================================
    // GET /api/v1/master/locations/{id} — 詳細取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/locations/{id} — ロケーション詳細取得")
    class GetLocation {

        @Test
        @DisplayName("正常系: ロケーションの詳細（階層情報含む）を取得できる")
        void get_existingLocation_returns200() throws Exception {
            Long locationId = createLocation(testStockAreaId, "A-01-A01-02-01-01", "テスト詳細");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + locationId, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("locationCode").asText()).isEqualTo("A-01-A01-02-01-01");
            assertThat(json.get("locationName").asText()).isEqualTo("テスト詳細");
            assertThat(json.has("version")).isTrue();
        }

        @Test
        @DisplayName("存在しないID → 404 LOCATION_NOT_FOUND")
        void get_nonExistentId_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("LOCATION_NOT_FOUND");
        }
    }

    // ========================================================
    // PUT /api/v1/master/locations/{id} — ロケーション更新
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/master/locations/{id} — ロケーション更新")
    class UpdateLocation {

        @Test
        @DisplayName("正常系: ロケーション名を変更できる")
        void update_validInput_returns200() throws Exception {
            Long locationId = createLocation(testStockAreaId, "A-01-A01-03-01-01", "更新前");
            Integer version = getLocationVersion(locationId);

            String body = String.format("""
                    {
                        "locationName": "更新後ロケーション",
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + locationId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("locationName").asText()).isEqualTo("更新後ロケーション");
        }

        @Test
        @DisplayName("locationNameをnullで更新（クリア）できる")
        void update_clearName_returns200() throws Exception {
            Long locationId = createLocation(testStockAreaId, "A-01-A01-03-02-01", "クリア前");
            Integer version = getLocationVersion(locationId);

            String body = String.format("""
                    {
                        "locationName": null,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + locationId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("楽観ロック競合 → 409")
        void update_versionMismatch_returns409() throws Exception {
            Long locationId = createLocation(testStockAreaId, "A-01-A01-03-03-01", "ロック競合");

            String body = """
                    {
                        "locationName": "競合更新",
                        "version": 999
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + locationId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }
    }

    // ========================================================
    // PATCH /api/v1/master/locations/{id}/toggle-active
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/locations/{id}/toggle-active — 有効/無効切替")
    class ToggleActive {

        @Test
        @DisplayName("ロケーションを無効化できる")
        void toggle_deactivate_returns200() throws Exception {
            Long locationId = createLocation(testStockAreaId, "A-01-A01-04-01-01", "無効化テスト");
            Integer version = getLocationVersion(locationId);

            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + locationId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isFalse();

            // DB検証
            Boolean isActive = jdbcTemplate.queryForObject(
                    "SELECT is_active FROM locations WHERE id = ?", Boolean.class, locationId);
            assertThat(isActive).isFalse();
        }

        @Test
        @DisplayName("無効なロケーションを有効化できる")
        void toggle_activate_returns200() throws Exception {
            Long locationId = createLocation(testStockAreaId, "A-01-A01-04-02-01", "有効化テスト");
            Integer version = getLocationVersion(locationId);

            // 無効化
            String deactivateBody = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            restTemplate.exchange(BASE_URL + "/" + locationId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(deactivateBody, adminHeaders), String.class);

            // 再有効化
            Integer newVersion = getLocationVersion(locationId);
            String activateBody = String.format("""
                    { "isActive": true, "version": %d }
                    """, newVersion);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + locationId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(activateBody, adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isTrue();
        }
    }

    // ========================================================
    // GET /api/v1/master/locations — 一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/locations — ロケーション一覧")
    class ListLocations {

        @Test
        @DisplayName("倉庫IDで絞り込み — 正常系")
        void list_byWarehouseId_returnsFiltered() throws Exception {
            createLocation(testStockAreaId, "A-01-A01-05-01-01", "一覧テスト1");
            createLocation(testStockAreaId, "A-01-A01-05-01-02", "一覧テスト2");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("SC-FAC13: コード前方一致で絞り込みできる")
        void list_byCodePrefix_returnsFiltered() throws Exception {
            createLocation(testStockAreaId, "A-01-A01-06-01-01", "プレフィックス1");
            createLocation(testStockAreaId, "A-01-A01-06-01-02", "プレフィックス2");
            createLocation(testStockAreaId, "A-01-A01-07-01-01", "別プレフィックス");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId
                            + "&codePrefix=A-01-A01-06&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isEqualTo(2);
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("locationCode").asText()).startsWith("A-01-A01-06");
            }
        }

        @Test
        @DisplayName("エリアIDで絞り込み")
        void list_byAreaId_returnsFiltered() throws Exception {
            createLocation(testStockAreaId, "A-01-A01-08-01-01", "エリア絞込");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?areaId=" + testStockAreaId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("ページネーション — size=1で2ページ以上")
        void list_pagination_works() throws Exception {
            createLocation(testStockAreaId, "A-01-A01-09-01-01", "ページ1");
            createLocation(testStockAreaId, "A-01-A01-09-01-02", "ページ2");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId + "&page=0&size=1",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isEqualTo(1);
            assertThat(json.get("totalPages").asInt()).isGreaterThan(1);
        }
    }

    // ========================================================
    // GET /api/v1/master/locations/count — 件数取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/locations/count — 件数取得")
    class CountLocations {

        @Test
        @DisplayName("倉庫IDで件数取得")
        void count_byWarehouseId_returnsCount() throws Exception {
            createLocation(testStockAreaId, "A-01-A01-10-01-01", "カウント1");
            createLocation(testStockAreaId, "A-01-A01-10-01-02", "カウント2");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/count?warehouseId=" + testWarehouseId,
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("count").asInt()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("エリアIDで件数取得")
        void count_byAreaId_returnsCount() throws Exception {
            createLocation(testStockAreaId, "A-01-A01-11-01-01", "エリアカウント");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/count?areaId=" + testStockAreaId,
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("count").asInt()).isGreaterThanOrEqualTo(1);
        }
    }

    // ========================================================
    // アクセス制御テスト
    // ========================================================

    @Nested
    @DisplayName("アクセス制御")
    class AccessControl {

        @Test
        @DisplayName("WAREHOUSE_STAFFはロケーション作成不可 → 403")
        void create_asStaff_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = String.format("""
                    {
                        "areaId": %d,
                        "locationCode": "A-01-A01-99-01-01"
                    }
                    """, testStockAreaId);

            ResponseEntity<String> response = postJson(BASE_URL, body, staffHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは一覧取得可能")
        void list_asStaff_returns200() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(staffHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ========================================================
    // ヘルパーメソッド
    // ========================================================

    private Long createWarehouse(String code, String name) throws Exception {
        String body = String.format("""
                { "warehouseCode": "%s", "warehouseName": "%s" }
                """, code, name);
        ResponseEntity<String> response = postJson("/api/v1/master/warehouses", body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Long createBuilding(Long warehouseId, String code, String name) throws Exception {
        String body = String.format("""
                { "warehouseId": %d, "buildingCode": "%s", "buildingName": "%s" }
                """, warehouseId, code, name);
        ResponseEntity<String> response = postJson("/api/v1/master/buildings", body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Long createArea(Long buildingId, String code, String name,
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

    private Long createLocation(Long areaId, String code, String name) throws Exception {
        String bodyTemplate = name != null
                ? """
                { "areaId": %d, "locationCode": "%s", "locationName": "%s" }
                """
                : """
                { "areaId": %d, "locationCode": "%s" }
                """;
        String body = name != null
                ? String.format(bodyTemplate, areaId, code, name)
                : String.format(bodyTemplate, areaId, code);
        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Integer getLocationVersion(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM locations WHERE id = ?", Integer.class, id);
    }
}
