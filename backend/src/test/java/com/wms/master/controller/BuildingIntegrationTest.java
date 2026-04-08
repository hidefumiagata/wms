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

@DisplayName("マスタ結合テスト: 棟管理")
class BuildingIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/master/buildings";
    private static final String WAREHOUSE_URL = "/api/v1/master/warehouses";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";
    private static final String VIEWER_CODE = "viewer01";
    private static final String VIEWER_PASSWORD = "Test@1234";

    private HttpHeaders adminHeaders;
    private Long testWarehouseId;

    @BeforeEach
    void setUp() throws Exception {
        // テスト用データのクリーンアップ
        jdbcTemplate.update("DELETE FROM locations WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM areas WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM buildings WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM warehouses WHERE warehouse_code LIKE 'IT%'");

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);

        // テスト用倉庫を作成
        testWarehouseId = createWarehouse("ITBW", "棟テスト用倉庫");
    }

    // ========================================================
    // POST /api/v1/master/buildings — 棟作成
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/master/buildings — 棟作成")
    class CreateBuilding {

        @Test
        @DisplayName("SC-FAC01: 倉庫内で一意のコードで棟を登録できる")
        void create_validInput_returns201() throws Exception {
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingCode": "C",
                        "buildingName": "C棟（テスト）"
                    }
                    """, testWarehouseId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("buildingCode").asText()).isEqualTo("C");
            assertThat(json.get("buildingName").asText()).isEqualTo("C棟（テスト）");
            assertThat(json.get("warehouseId").asLong()).isEqualTo(testWarehouseId);
            assertThat(json.get("isActive").asBoolean()).isTrue();
            assertThat(json.has("version")).isTrue();

            // DB検証
            var dbRow = jdbcTemplate.queryForMap(
                    "SELECT building_code, building_name, warehouse_id, is_active FROM buildings WHERE building_code = 'C' AND warehouse_id = ?",
                    testWarehouseId);
            assertThat(dbRow.get("building_code")).isEqualTo("C");
            assertThat(dbRow.get("is_active")).isEqualTo(true);
        }

        @Test
        @DisplayName("SC-FAC02: 倉庫内コード重複 → 409 DUPLICATE_CODE")
        void create_duplicateCodeInSameWarehouse_returns409() throws Exception {
            // まず棟を作成
            createBuilding(testWarehouseId, "D", "D棟");

            // 同じ倉庫に同じコードで再作成
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingCode": "D",
                        "buildingName": "D棟（重複）"
                    }
                    """, testWarehouseId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DUPLICATE_CODE");
        }

        @Test
        @DisplayName("存在しない倉庫ID → 404")
        void create_invalidWarehouseId_returns404() throws Exception {
            String body = """
                    {
                        "warehouseId": 999999,
                        "buildingCode": "E",
                        "buildingName": "E棟"
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
                        "warehouseId": %d
                    }
                    """, testWarehouseId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("異なる倉庫なら同じコードで作成可能")
        void create_sameCodeDifferentWarehouse_returns201() throws Exception {
            Long otherWarehouseId = createWarehouse("ITBX", "別倉庫");
            createBuilding(testWarehouseId, "F", "F棟（倉庫1）");

            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingCode": "F",
                        "buildingName": "F棟（倉庫2）"
                    }
                    """, otherWarehouseId);

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // ========================================================
    // GET /api/v1/master/buildings/{id} — 詳細取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/buildings/{id} — 棟詳細取得")
    class GetBuilding {

        @Test
        @DisplayName("正常系: 棟の詳細を取得できる")
        void get_existingBuilding_returns200() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "G", "G棟");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("buildingCode").asText()).isEqualTo("G");
            assertThat(json.get("buildingName").asText()).isEqualTo("G棟");
            assertThat(json.get("warehouseId").asLong()).isEqualTo(testWarehouseId);
            assertThat(json.has("version")).isTrue();
        }

        @Test
        @DisplayName("存在しないID → 404 BUILDING_NOT_FOUND")
        void get_nonExistentId_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("BUILDING_NOT_FOUND");
        }
    }

    // ========================================================
    // PUT /api/v1/master/buildings/{id} — 棟更新
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/master/buildings/{id} — 棟更新")
    class UpdateBuilding {

        @Test
        @DisplayName("正常系: 棟名を変更して更新できる")
        void update_validInput_returns200() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "H", "H棟（更新前）");
            Integer version = getBuildingVersion(buildingId);

            String body = String.format("""
                    {
                        "buildingName": "H棟（更新後）",
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("buildingName").asText()).isEqualTo("H棟（更新後）");
            assertThat(json.get("version").asInt()).isEqualTo(version + 1);
        }

        @Test
        @DisplayName("楽観ロック競合 → 409")
        void update_versionMismatch_returns409() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "I", "I棟");

            String body = """
                    {
                        "buildingName": "I棟（競合）",
                        "version": 999
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }
    }

    // ========================================================
    // PATCH /api/v1/master/buildings/{id}/toggle-active
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/buildings/{id}/toggle-active — 有効/無効切替")
    class ToggleActive {

        @Test
        @DisplayName("子エリアなし: 棟を無効化できる")
        void toggle_noChildren_deactivateOk() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "J", "J棟");
            Integer version = getBuildingVersion(buildingId);

            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("SC-FAC03: 配下にエリアが存在する棟は無効化不可 → 422")
        void toggle_hasChildAreas_returns422() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "K", "K棟");

            // 子エリアを作成
            createArea(buildingId, "K01", "K01エリア", "AMBIENT", "STOCK");

            Integer version = getBuildingVersion(buildingId);
            String body = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("CANNOT_DEACTIVATE_HAS_CHILDREN");
        }

        @Test
        @DisplayName("無効な棟を有効化できる")
        void toggle_activate_returns200() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "L", "L棟");
            Integer version = getBuildingVersion(buildingId);

            // 無効化
            String deactivateBody = String.format("""
                    { "isActive": false, "version": %d }
                    """, version);
            restTemplate.exchange(BASE_URL + "/" + buildingId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(deactivateBody, adminHeaders), String.class);

            // 再有効化
            Integer newVersion = getBuildingVersion(buildingId);
            String activateBody = String.format("""
                    { "isActive": true, "version": %d }
                    """, newVersion);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId + "/toggle-active",
                    HttpMethod.PATCH, new HttpEntity<>(activateBody, adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isTrue();
        }
    }

    // ========================================================
    // GET /api/v1/master/buildings — 一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/buildings — 棟一覧")
    class ListBuildings {

        @Test
        @DisplayName("倉庫IDで絞り込み — 正常系")
        void list_byWarehouseId_returnsFiltered() throws Exception {
            createBuilding(testWarehouseId, "M", "M棟");
            createBuilding(testWarehouseId, "N", "N棟");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("棟コードで絞り込み")
        void list_filterByCode_returnsFiltered() throws Exception {
            createBuilding(testWarehouseId, "P", "P棟");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId + "&buildingCode=P&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").size()).isEqualTo(1);
        }
    }

    // ========================================================
    // アクセス制御テスト
    // ========================================================

    @Nested
    @DisplayName("アクセス制御")
    class AccessControl {

        @Test
        @DisplayName("SC-C13: WAREHOUSE_STAFFは棟作成不可 → 403")
        void create_asStaff_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingCode": "Q",
                        "buildingName": "Q棟"
                    }
                    """, testWarehouseId);

            ResponseEntity<String> response = postJson(BASE_URL, body, staffHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは棟更新不可 → 403")
        void update_asStaff_returns403() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "R", "R棟");
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    { "buildingName": "スタッフ更新", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFFは棟有効/無効切替不可 → 403")
        void toggle_asStaff_returns403() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "S", "S棟");
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);
            String body = """
                    { "isActive": false, "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

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

        @Test
        @DisplayName("VIEWERは棟一覧取得可能")
        void list_asViewer_returns200() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?warehouseId=" + testWarehouseId + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(viewerHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("VIEWERは棟詳細取得可能")
        void get_asViewer_returns200() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "V", "V棟");
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId, HttpMethod.GET,
                    new HttpEntity<>(viewerHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("VIEWERは棟作成不可 → 403")
        void create_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);
            String body = String.format("""
                    {
                        "warehouseId": %d,
                        "buildingCode": "W",
                        "buildingName": "W棟"
                    }
                    """, testWarehouseId);

            ResponseEntity<String> response = postJson(BASE_URL, body, viewerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("VIEWERは棟更新不可 → 403")
        void update_asViewer_returns403() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "X", "X棟");
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);
            String body = """
                    { "buildingName": "ビューワー更新", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, viewerHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("VIEWERは棟有効/無効切替不可 → 403")
        void toggle_asViewer_returns403() throws Exception {
            Long buildingId = createBuilding(testWarehouseId, "Y", "Y棟");
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);
            String body = """
                    { "isActive": false, "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, viewerHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + buildingId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ========================================================
    // ヘルパーメソッド
    // ========================================================

    private Long createWarehouse(String code, String name) throws Exception {
        String body = String.format("""
                {
                    "warehouseCode": "%s",
                    "warehouseName": "%s"
                }
                """, code, name);
        ResponseEntity<String> response = postJson(WAREHOUSE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Long createBuilding(Long warehouseId, String code, String name) throws Exception {
        String body = String.format("""
                {
                    "warehouseId": %d,
                    "buildingCode": "%s",
                    "buildingName": "%s"
                }
                """, warehouseId, code, name);
        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Long createArea(Long buildingId, String code, String name,
                            String storageCondition, String areaType) throws Exception {
        String body = String.format("""
                {
                    "buildingId": %d,
                    "areaCode": "%s",
                    "areaName": "%s",
                    "storageCondition": "%s",
                    "areaType": "%s"
                }
                """, buildingId, code, name, storageCondition, areaType);
        ResponseEntity<String> response = postJson("/api/v1/master/areas", body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(response.getBody()).get("id").asLong();
    }

    private Integer getBuildingVersion(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM buildings WHERE id = ?", Integer.class, id);
    }
}
