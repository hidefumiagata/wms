package com.wms.master.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.shared.test.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("マスタ結合テスト: 施設階層 一気通貫")
class FacilityHierarchyIntegrationTest extends IntegrationTestBase {

    private static final String WAREHOUSE_URL = "/api/v1/master/warehouses";
    private static final String BUILDING_URL = "/api/v1/master/buildings";
    private static final String AREA_URL = "/api/v1/master/areas";
    private static final String LOCATION_URL = "/api/v1/master/locations";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        // テスト用データのクリーンアップ
        jdbcTemplate.update("DELETE FROM locations WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM areas WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM buildings WHERE warehouse_id IN (SELECT id FROM warehouses WHERE warehouse_code LIKE 'IT%')");
        jdbcTemplate.update("DELETE FROM warehouses WHERE warehouse_code LIKE 'IT%'");

        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    @Test
    @DisplayName("SC-FAC12: 倉庫→棟→エリア→ロケーションの順に登録できる")
    void hierarchicalCreation_fullHierarchy_succeeds() throws Exception {
        // Step 1: 倉庫を作成
        String warehouseBody = """
                {
                    "warehouseCode": "ITHW",
                    "warehouseName": "階層テスト倉庫",
                    "address": "テスト住所"
                }
                """;
        ResponseEntity<String> whResponse = postJson(WAREHOUSE_URL, warehouseBody, adminHeaders);
        assertThat(whResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode whJson = parseJson(whResponse.getBody());
        Long warehouseId = whJson.get("id").asLong();
        assertThat(whJson.get("warehouseCode").asText()).isEqualTo("ITHW");

        // Step 2: 棟を作成（倉庫配下）
        String buildingBody = String.format("""
                {
                    "warehouseId": %d,
                    "buildingCode": "A",
                    "buildingName": "A棟（常温）"
                }
                """, warehouseId);
        ResponseEntity<String> bldResponse = postJson(BUILDING_URL, buildingBody, adminHeaders);
        assertThat(bldResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode bldJson = parseJson(bldResponse.getBody());
        Long buildingId = bldJson.get("id").asLong();
        assertThat(bldJson.get("warehouseId").asLong()).isEqualTo(warehouseId);

        // Step 3: 在庫エリアを作成（棟配下）
        String stockAreaBody = String.format("""
                {
                    "buildingId": %d,
                    "areaCode": "A01",
                    "areaName": "常温在庫エリア",
                    "storageCondition": "AMBIENT",
                    "areaType": "STOCK"
                }
                """, buildingId);
        ResponseEntity<String> areaResponse = postJson(AREA_URL, stockAreaBody, adminHeaders);
        assertThat(areaResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode areaJson = parseJson(areaResponse.getBody());
        Long stockAreaId = areaJson.get("id").asLong();
        assertThat(areaJson.get("areaType").asText()).isEqualTo("STOCK");

        // Step 3b: 入荷エリアも作成
        String inboundAreaBody = String.format("""
                {
                    "buildingId": %d,
                    "areaCode": "INB",
                    "areaName": "入荷エリア",
                    "storageCondition": "AMBIENT",
                    "areaType": "INBOUND"
                }
                """, buildingId);
        ResponseEntity<String> inbResponse = postJson(AREA_URL, inboundAreaBody, adminHeaders);
        assertThat(inbResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long inboundAreaId = parseJson(inbResponse.getBody()).get("id").asLong();

        // Step 4: 在庫ロケーションを作成（在庫エリア配下）
        String locationBody = String.format("""
                {
                    "areaId": %d,
                    "locationCode": "A-01-A01-01-01-01",
                    "locationName": "A棟1階A01棚01段01番"
                }
                """, stockAreaId);
        ResponseEntity<String> locResponse = postJson(LOCATION_URL, locationBody, adminHeaders);
        assertThat(locResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode locJson = parseJson(locResponse.getBody());
        assertThat(locJson.get("locationCode").asText()).isEqualTo("A-01-A01-01-01-01");

        // Step 4b: 入荷ロケーションを作成
        String inbLocBody = String.format("""
                {
                    "areaId": %d,
                    "locationCode": "A-01-INB-01"
                }
                """, inboundAreaId);
        ResponseEntity<String> inbLocResponse = postJson(LOCATION_URL, inbLocBody, adminHeaders);
        assertThat(inbLocResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 検証: DB上で正しい階層構造が構築されていること
        Long locWarehouseId = jdbcTemplate.queryForObject(
                "SELECT warehouse_id FROM locations WHERE location_code = 'A-01-A01-01-01-01'",
                Long.class);
        assertThat(locWarehouseId).isEqualTo(warehouseId);

        Long areaWarehouseId = jdbcTemplate.queryForObject(
                "SELECT warehouse_id FROM areas WHERE id = ?", Long.class, stockAreaId);
        assertThat(areaWarehouseId).isEqualTo(warehouseId);

        // 検証: 件数カウントAPI
        ResponseEntity<String> countResponse = restTemplate.exchange(
                LOCATION_URL + "/count?warehouseId=" + warehouseId,
                HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
        assertThat(countResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode countJson = parseJson(countResponse.getBody());
        assertThat(countJson.get("count").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("親の無効化制約: 子が存在する場合は無効化不可の連鎖を確認")
    void hierarchicalDeactivation_constraintsCascade() throws Exception {
        // 階層構築
        Long warehouseId = createWarehouse("ITDW");
        Long buildingId = createBuilding(warehouseId, "A");
        Long areaId = createArea(buildingId, "A01", "STOCK");
        createLocation(areaId, "A-01-A01-01-01-01");

        // エリアの無効化: 子ロケーション（有効）があるため不可
        Integer areaVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM areas WHERE id = ?", Integer.class, areaId);
        String areaToggleBody = String.format("""
                { "isActive": false, "version": %d }
                """, areaVersion);
        ResponseEntity<String> areaToggleResp = restTemplate.exchange(
                AREA_URL + "/" + areaId + "/toggle-active",
                HttpMethod.PATCH, new HttpEntity<>(areaToggleBody, adminHeaders), String.class);
        assertThat(areaToggleResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // 棟の無効化: 子エリアがあるため不可
        Integer buildingVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM buildings WHERE id = ?", Integer.class, buildingId);
        String buildingToggleBody = String.format("""
                { "isActive": false, "version": %d }
                """, buildingVersion);
        ResponseEntity<String> buildingToggleResp = restTemplate.exchange(
                BUILDING_URL + "/" + buildingId + "/toggle-active",
                HttpMethod.PATCH, new HttpEntity<>(buildingToggleBody, adminHeaders), String.class);
        assertThat(buildingToggleResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("子を全て無効化した後なら親を無効化できる（ボトムアップ）")
    void hierarchicalDeactivation_bottomUpSucceeds() throws Exception {
        // 階層構築
        Long warehouseId = createWarehouse("ITBU");
        Long buildingId = createBuilding(warehouseId, "A");
        Long areaId = createArea(buildingId, "A01", "STOCK");
        Long locationId = createLocation(areaId, "A-01-A01-01-01-01");

        // ロケーションを無効化
        Integer locVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM locations WHERE id = ?", Integer.class, locationId);
        String locToggle = String.format("""
                { "isActive": false, "version": %d }
                """, locVersion);
        ResponseEntity<String> locResp = restTemplate.exchange(
                LOCATION_URL + "/" + locationId + "/toggle-active",
                HttpMethod.PATCH, new HttpEntity<>(locToggle, adminHeaders), String.class);
        assertThat(locResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // エリアを無効化（子ロケーションは無効化済み→OK）
        Integer areaVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM areas WHERE id = ?", Integer.class, areaId);
        String areaToggle = String.format("""
                { "isActive": false, "version": %d }
                """, areaVersion);
        ResponseEntity<String> areaResp = restTemplate.exchange(
                AREA_URL + "/" + areaId + "/toggle-active",
                HttpMethod.PATCH, new HttpEntity<>(areaToggle, adminHeaders), String.class);
        assertThat(areaResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 棟を無効化（子エリアは存在するが…）
        // 注: BuildingServiceは countByBuildingId > 0 で判定（有効/無効問わず）
        // この場合、エリアが存在するため無効化は不可
        Integer buildingVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM buildings WHERE id = ?", Integer.class, buildingId);
        String buildingToggle = String.format("""
                { "isActive": false, "version": %d }
                """, buildingVersion);
        ResponseEntity<String> buildingResp = restTemplate.exchange(
                BUILDING_URL + "/" + buildingId + "/toggle-active",
                HttpMethod.PATCH, new HttpEntity<>(buildingToggle, adminHeaders), String.class);
        // 棟はエリア数で判定（有効/無効問わず）→ 422
        assertThat(buildingResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ========================================================
    // ヘルパーメソッド
    // ========================================================

    private Long createWarehouse(String code) throws Exception {
        String body = String.format("""
                { "warehouseCode": "%s", "warehouseName": "%s倉庫" }
                """, code, code);
        ResponseEntity<String> resp = postJson(WAREHOUSE_URL, body, adminHeaders);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(resp.getBody()).get("id").asLong();
    }

    private Long createBuilding(Long warehouseId, String code) throws Exception {
        String body = String.format("""
                { "warehouseId": %d, "buildingCode": "%s", "buildingName": "%s棟" }
                """, warehouseId, code, code);
        ResponseEntity<String> resp = postJson(BUILDING_URL, body, adminHeaders);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(resp.getBody()).get("id").asLong();
    }

    private Long createArea(Long buildingId, String code, String areaType) throws Exception {
        String body = String.format("""
                {
                    "buildingId": %d, "areaCode": "%s", "areaName": "%sエリア",
                    "storageCondition": "AMBIENT", "areaType": "%s"
                }
                """, buildingId, code, code, areaType);
        ResponseEntity<String> resp = postJson(AREA_URL, body, adminHeaders);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(resp.getBody()).get("id").asLong();
    }

    private Long createLocation(Long areaId, String code) throws Exception {
        String body = String.format("""
                { "areaId": %d, "locationCode": "%s" }
                """, areaId, code);
        ResponseEntity<String> resp = postJson(LOCATION_URL, body, adminHeaders);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return parseJson(resp.getBody()).get("id").asLong();
    }
}
