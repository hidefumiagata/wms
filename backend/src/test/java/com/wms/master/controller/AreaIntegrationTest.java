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

@DisplayName("マスタ結合テスト: エリア管理")
class AreaIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/master/areas";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";

    private HttpHeaders adminHeaders;
    private Long testWarehouseId;
    private Long testBuildingId;

    @BeforeEach
    void setUp() throws Exception {
        // テスト用データのクリーンアップ
        jdbcTemplate.update("DELETE FROM locations WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM areas WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM buildings WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM warehouses WHERE warehouse_code LIKE 'IT%'");

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);

        // テスト用倉庫・棟を作成
        testWarehouseId = createWarehouse("ITAW", "エリアテスト用倉庫");
        testBuildingId = createBuilding(testWarehouseId, "A", "A棟");
    }

    // ========================================================
    // POST /api/v1/master/areas — エリア作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/master/areas — エリア作成")
    class CreateArea {

        @Test
        @DisplayName("SC-FAC04: 棟内で一意のコードでエリアを登録できる")
        void create_validInput_returns201() throws Exception {
            String body = String.format("""
                    {
                        "buildingId": %d,
                        "areaCode": "A01",
                        "areaName": "A棟 常温在庫1",
                        "storageCondition": "AMBIENT",
                        "areaType": "STOCK"
                    }
                    """, testBuildingId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("areaCode").asText()).isEqualTo("A01");
            assertThat(json.get("areaName").asText()).isEqualTo("A棟 常温在庫1");
            assertThat(json.get("storageCondition").asText()).isEqualTo("AMBIENT");
            assertThat(json.get("areaType").asText()).isEqualTo("STOCK");
            assertThat(json.get("isActive").asBoolean()).isTrue();
            assertThat(json.has("version")).isTrue();

            // DB検証: warehouseId が自動設定されていること
            Long dbWarehouseId = jdbcTemplate.queryForObject(
                    "SELECT warehouse_id FROM areas WHERE area_code = 'A01' AND building_id = ?",
                    Long.class, testBuildingId);
            assertThat(dbWarehouseId).isEqualTo(testWarehouseId);
        }

        @Test
        @DisplayName("冷蔵エリアを作成できる")
        void create_refrigerated_returns201() throws Exception {
            String body = String.format("""
                    {
                        "buildingId": %d,
                        "areaCode": "R01",
                        "areaName": "冷蔵エリア",
                        "storageCondition": "REFRIGERATED",
                        "areaType": "STOCK"
                    }
                    """, testBuildingId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("storageCondition").asText()).isEqualTo("REFRIGERATED");
        }

        @Test
        @DisplayName("冷凍エリアを作成できる")
        void create_frozen_returns201() throws Exception {
            String body = String.format("""
                    {
                        "buildingId": %d,
                        "areaCode": "F01",
                        "areaName": "冷凍エリア",
                        "storageCondition": "FROZEN",
                        "areaType": "STOCK"
                    }
                    """, testBuildingId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("storageCondition").asText()).isEqualTo("FROZEN");
        }

        @Test
        @DisplayName("入荷エリアを作成できる")
        void create_inboundArea_returns201() throws Exception {
            String body = String.format("""
                    {
                        "buildingId": %d,
                        "areaCode": "INB",
                        "areaName": "入荷エリア",
                        "storageCondition": "AMBIENT",
                        "areaType": "INBOUND"
                    }
                    """, testBuildingId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("areaType").asText()).isEqualTo("INBOUND");
        }

        @Test
        @DisplayName("SC-FAC05: 棟内コード重複 → 409 DUPLICATE_CODE")
        void create_duplicateCodeInSameBuilding_returns409() throws Exception {
            createArea(testBuildingId, "DUP", "重複元", "AMBIENT", "STOCK");

            String body = String.format("""
                    {
                        "buildingId": %d,
                        "areaCode": "DUP",
                        "areaName": "重複先",
                        "storageCondition": "AMBIENT",
                        "areaType": "STOCK"
                    }
                    """, testBuildingId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DUPLICATE_CODE");
        }

        @Test
        @DisplayName("存在しない棟ID → 404")
        void create_invalidBuildingId_returns404() throws Exception {
            String body = """
                    {
                        "buildingId": 999999,
                        "areaCode": "X01",
                        "areaName": "不正棟ID",
                        "storageCondition": "AMBIENT",
                        "areaType": "STOCK"
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
                        "buildingId": %d,
                        "areaCode": "M01"
                    }
                    """, testBuildingId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("異なる棟なら同じコードで作成可能")
        void create_sameCodeDifferentBuilding_returns201() throws Exception {
            Long otherBuildingId = createBuilding(testWarehouseId, "B", "B棟");
            createArea(testBuildingId, "SRC", "同一コード元", "AMBIENT", "STOCK");

            String body = String.format("""
                    {
                        "buildingId": %d,
                        "areaCode": "SRC",
                        "areaName": "同一コード先",
                        "storageCondition": "AMBIENT",
                        "areaType": "STOCK"
                    }
                    """, otherBuildingId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // ========================================================
    // GET /api/v1/master/areas/{id} — 詳細取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/areas/{id} — エリア詳細取得")
    class GetArea {

        @Test
        @DisplayName("正常系: エリアの詳細を取得できる（階層情報含む）")
        void get_existingArea_returns200() throws Exception {
            Long areaId = createArea(testBuildingId, "G01", "G01エリア", "AMBIENT", "STOCK");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + areaId, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("areaCode").asText()).isEqualTo("G01");
            assertThat(json.get("areaName").asText()).isEqualTo("G01エリア");
            assertThat(json.get("storageCondition").asText()).isEqualTo("AMBIENT");
            assertThat(json.get("areaType").asText()).isEqualTo("STOCK");
            assertThat(json.has("version")).isTrue();
        }

        @Test
        @DisplayName("存在しないID → 404 AREA_NOT_FOUND")
        void get_nonExistentId_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("AREA_NOT_FOUND");
        }
    }

    // ========================================================
    // PUT /api/v1/master/areas/{id} — エリア更新
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/master/areas/{id} — エリア更新")
    class UpdateArea {

        @Test
        @DisplayName("正常系: エリア名と保管条件を変更できる")
        void update_validInput_returns200() throws Exception {
            Long areaId = createArea(testBuildingId, "U01", "更新前エリア", "AMBIENT", "STOCK");
            Integer version = getAreaVersion(areaId);

            String body = String.format("""
                    {
                        "areaName": "更新後エリア",
                        "storageCondition": "REFRIGERATED",
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + areaId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("areaName").asText()).isEqualTo("更新後エリア");
            assertThat(json.get("storageCondition").asText()).isEqualTo("REFRIGERATED");
        }

        @Test
        @DisplayName("楽観ロック競合 → 409")
        void update_versionMismatch_returns409() throws Exception {
            Long areaId = createArea(testBuildingId, "U02", "ロック競合", "AMBIENT", "STOCK");

            String body = """
                    {
                        "areaName": "競合更新",
                        "storageCondition": "AMBIENT",
                        "version": 999
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + areaId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }
    }

    // ========================================================
    // PATCH /api/v1/master/areas/{id}/toggle-active
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/areas/{id}/toggle-active — 有効/無効切替")
    class ToggleActive {

        @Test
        @DisplayName("子ロケーションなし: エリアを無効化できる")
        void toggle_noChildren_deactivateOk() throws Exception {
            Long areaId = createArea(testBuildingId, "T01", "無効化テスト", "AMBIENT", "STOCK");
            Integer version = getAreaVersion(areaId);

            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + areaId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("SC-FAC06: 配下にロケーションが存在するエリアは無効化不可 → 422")
        void toggle_hasChildLocations_returns422() throws Exception {
            Long areaId = createArea(testBuildingId, "T02", "子ありエリア", "AMBIENT", "STOCK");

            // 子ロケーションを作成
            createLocation(areaId, "A-01-T02-01-01-01", "テストロケーション");

            Integer version = getAreaVersion(areaId);
            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + areaId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("CANNOT_DEACTIVATE_HAS_CHILDREN");
        }

        @Test
        @DisplayName("無効なエリアを有効化できる")
        void toggle_activate_returns200() throws Exception {
            Long areaId = createArea(testBuildingId, "T03", "有効化テスト", "AMBIENT", "STOCK");
            Integer version = getAreaVersion(areaId);

            // 無効化
            String deactivateBody = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            restTemplate.exchange(BASE_URL + "/" + areaId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(deactivateBody, adminHeaders), String.class);

            // 再有効化
            Integer newVersion = getAreaVersion(areaId);
            String activateBody = String.format("""
                    { "isActive": true, "version": %d }
                    """, newVersion);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + areaId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(activateBody, adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isTrue();
        }
    }

    // ========================================================
    // GET /api/v1/master/areas — 一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/areas — エリア一覧")
    class ListAreas {

        @Test
        @DisplayName("倉庫IDで絞り込み")
        void list_byWarehouseId_returnsFiltered() throws Exception {
            createArea(testBuildingId, "L01", "一覧テスト1", "AMBIENT", "STOCK");
            createArea(testBuildingId, "L02", "一覧テスト2", "REFRIGERATED", "STOCK");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("棟IDで絞り込み")
        void list_byBuildingId_returnsFiltered() throws Exception {
            createArea(testBuildingId, "L03", "棟絞り込み", "AMBIENT", "STOCK");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?buildingId=" + testBuildingId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("保管条件で絞り込み")
        void list_byStorageCondition_returnsFiltered() throws Exception {
            createArea(testBuildingId, "L04", "冷蔵テスト", "REFRIGERATED", "STOCK");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId
                            + "&storageCondition=REFRIGERATED&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("storageCondition").asText()).isEqualTo("REFRIGERATED");
            }
        }

        @Test
        @DisplayName("エリアタイプで絞り込み")
        void list_byAreaType_returnsFiltered() throws Exception {
            createArea(testBuildingId, "INB2", "入荷テスト", "AMBIENT", "INBOUND");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId
                            + "&areaType=INBOUND&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            for (JsonNode item : json.get("content")) {
                assertThat(item.get("areaType").asText()).isEqualTo("INBOUND");
            }
        }
    }

    // ========================================================
    // アクセス制御テスト
    // ========================================================

    @Nested
    @DisplayName("アクセス制御")
    class AccessControl {

        @Test
        @DisplayName("WAREHOUSE_STAFFはエリア作成不可 → 403")
        void create_asStaff_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = String.format("""
                    {
                        "buildingId": %d,
                        "areaCode": "S01",
                        "areaName": "スタッフ作成テスト",
                        "storageCondition": "AMBIENT",
                        "areaType": "STOCK"
                    }
                    """, testBuildingId);

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
        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Long createLocation(Long areaId, String code, String name) throws Exception {
        String body = String.format("""
                { "areaId": %d, "locationCode": "%s", "locationName": "%s" }
                """, areaId, code, name);
        ResponseEntity<String> response = postJson("/api/v1/master/locations", body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Integer getAreaVersion(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM areas WHERE id = ?", Integer.class, id);
    }
}
