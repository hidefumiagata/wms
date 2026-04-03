package com.wms.master.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared helper methods for facility integration tests.
 * Reduces code duplication across Warehouse/Building/Area/Location tests.
 */
final class FacilityIntegrationTestHelper {

    static final String WAREHOUSE_URL = "/api/v1/master/warehouses";
    static final String BUILDING_URL = "/api/v1/master/buildings";
    static final String AREA_URL = "/api/v1/master/areas";
    static final String LOCATION_URL = "/api/v1/master/locations";

    static final String ADMIN_CODE = "admin001";
    static final String ADMIN_PASSWORD = "Admin@1234";
    static final String MANAGER_CODE = "wh_manager01";
    static final String MANAGER_PASSWORD = "Manager@1234";
    static final String STAFF_CODE = "wh_staff01";
    static final String STAFF_PASSWORD = "Staff@1234";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FacilityIntegrationTestHelper() {
    }

    static Long createWarehouse(String code, String name,
                                BiFunction<String, String, ResponseEntity<String>> postJson,
                                HttpHeaders headers) throws Exception {
        String body = String.format("""
                { "warehouseCode": "%s", "warehouseName": "%s" }
                """, code, name);
        ResponseEntity<String> response = postJson.apply(WAREHOUSE_URL, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return OBJECT_MAPPER.readTree(response.getBody()).get("id").asLong();
    }

    static Long createBuilding(Long warehouseId, String code, String name,
                                BiFunction<String, String, ResponseEntity<String>> postJson) throws Exception {
        String body = String.format("""
                { "warehouseId": %d, "buildingCode": "%s", "buildingName": "%s" }
                """, warehouseId, code, name);
        ResponseEntity<String> response = postJson.apply(BUILDING_URL, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return OBJECT_MAPPER.readTree(response.getBody()).get("id").asLong();
    }

    static Long createArea(Long buildingId, String code, String name,
                            String storageCondition, String areaType,
                            BiFunction<String, String, ResponseEntity<String>> postJson) throws Exception {
        String body = String.format("""
                {
                    "buildingId": %d, "areaCode": "%s", "areaName": "%s",
                    "storageCondition": "%s", "areaType": "%s"
                }
                """, buildingId, code, name, storageCondition, areaType);
        ResponseEntity<String> response = postJson.apply(AREA_URL, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return OBJECT_MAPPER.readTree(response.getBody()).get("id").asLong();
    }

    static Long createLocation(Long areaId, String code, String name,
                                BiFunction<String, String, ResponseEntity<String>> postJson) throws Exception {
        String body = name != null
                ? String.format("""
                { "areaId": %d, "locationCode": "%s", "locationName": "%s" }
                """, areaId, code, name)
                : String.format("""
                { "areaId": %d, "locationCode": "%s" }
                """, areaId, code);
        ResponseEntity<String> response = postJson.apply(LOCATION_URL, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return OBJECT_MAPPER.readTree(response.getBody()).get("id").asLong();
    }

    static JsonNode parseJson(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body);
    }
}
