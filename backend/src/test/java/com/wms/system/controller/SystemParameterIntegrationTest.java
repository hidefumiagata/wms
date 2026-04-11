package com.wms.system.controller;

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
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("結合テスト: システムパラメータ管理")
class SystemParameterIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/system/parameters";
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
        // テストで変更したパラメータ値・バージョンを初期値に戻す
        jdbcTemplate.update(
                "UPDATE system_parameters SET param_value = default_value, version = 0 WHERE param_key IN ('LOCATION_CAPACITY_CASE', 'SESSION_TIMEOUT_MINUTES', 'DEFAULT_WAREHOUSE_CODE', 'AUTO_ALLOCATE_ON_OUTBOUND')");
        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    // ========================================================
    // GET /api/v1/system/parameters — パラメータ一覧取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/system/parameters — パラメータ一覧取得")
    class GetSystemParameters {

        @Test
        @DisplayName("SC-001: SYSTEM_ADMINでパラメータ一覧が取得できる")
        void getAll_asAdmin_returns200WithAllParameters() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.isArray()).isTrue();
            assertThat(json.size()).isGreaterThanOrEqualTo(6);

            // DB検証: 件数一致
            Integer dbCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system_parameters", Integer.class);
            assertThat(json.size()).isEqualTo(dbCount);

            // 各パラメータの必須フィールドが含まれていること
            JsonNode first = json.get(0);
            assertThat(first.has("paramKey")).isTrue();
            assertThat(first.has("paramValue")).isTrue();
            assertThat(first.has("defaultValue")).isTrue();
            assertThat(first.has("displayName")).isTrue();
            assertThat(first.has("category")).isTrue();
            assertThat(first.has("valueType")).isTrue();
            assertThat(first.has("version")).isTrue();
        }

        @Test
        @DisplayName("SC-001: カテゴリとパラメータの値がDB値と一致する")
        void getAll_parameterValues_matchDatabase() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            JsonNode json = parseJson(response.getBody());

            // LOCATION_CAPACITY_CASE を探す
            JsonNode capacityCase = findByParamKey(json, "LOCATION_CAPACITY_CASE");
            assertThat(capacityCase.get("paramValue").asText()).isEqualTo("1");
            assertThat(capacityCase.get("defaultValue").asText()).isEqualTo("1");
            assertThat(capacityCase.get("category").asText()).isEqualTo("INVENTORY");
            assertThat(capacityCase.get("valueType").asText()).isEqualTo("INTEGER");

            // SESSION_TIMEOUT_MINUTES を探す
            JsonNode sessionTimeout = findByParamKey(json, "SESSION_TIMEOUT_MINUTES");
            assertThat(sessionTimeout.get("paramValue").asText()).isEqualTo("60");
            assertThat(sessionTimeout.get("category").asText()).isEqualTo("SECURITY");
        }

        @Test
        @DisplayName("SC-001: レスポンスがcategory+displayOrder順にソートされている")
        void getAll_sortedByCategoryAndDisplayOrder() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            JsonNode json = parseJson(response.getBody());

            // category + displayOrder の順でソートされていることを検証
            String prevCategory = "";
            int prevDisplayOrder = Integer.MIN_VALUE;
            for (JsonNode node : json) {
                String category = node.get("category").asText();
                int displayOrder = node.get("displayOrder").asInt();
                if (category.equals(prevCategory)) {
                    assertThat(displayOrder).as("displayOrder within category '%s'", category)
                            .isGreaterThanOrEqualTo(prevDisplayOrder);
                }
                prevCategory = category;
                prevDisplayOrder = displayOrder;
            }
        }

        @Test
        @DisplayName("SC-007: VIEWERはアクセス不可 → 403")
        void getAll_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL, HttpMethod.GET,
                    new HttpEntity<>(viewerHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("SC-005: WAREHOUSE_MANAGERはアクセス不可 → 403")
        void getAll_asManager_returns403() throws Exception {
            HttpHeaders managerHeaders = loginAndGetHeaders(MANAGER_CODE, MANAGER_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL, HttpMethod.GET,
                    new HttpEntity<>(managerHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("SC-006: WAREHOUSE_STAFFはアクセス不可 → 403")
        void getAll_asStaff_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL, HttpMethod.GET,
                    new HttpEntity<>(staffHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("SC-008: 未認証でアクセス → 401")
        void getAll_unauthenticated_returns401() throws Exception {
            HttpHeaders headers = createJsonHeaders();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ========================================================
    // PUT /api/v1/system/parameters/{paramKey} — パラメータ更新
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/system/parameters/{paramKey} — パラメータ更新")
    class UpdateSystemParameter {

        @Test
        @DisplayName("SC-002: INTEGER型パラメータの値を正常に更新できる")
        void update_integerParam_returns200() throws Exception {
            Integer version = getVersion("LOCATION_CAPACITY_CASE");
            String body = String.format("""
                    {
                        "paramValue": "10",
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("paramKey").asText()).isEqualTo("LOCATION_CAPACITY_CASE");
            assertThat(json.get("paramValue").asText()).isEqualTo("10");
            assertThat(json.get("version").asInt()).isEqualTo(version + 1);

            // DB検証
            var dbRow = jdbcTemplate.queryForMap(
                    "SELECT param_value, updated_by FROM system_parameters WHERE param_key = 'LOCATION_CAPACITY_CASE'");
            assertThat(dbRow.get("param_value")).isEqualTo("10");
            assertThat(dbRow.get("updated_by")).isNotNull();
        }

        @Test
        @DisplayName("SC-002: 更新後に一覧取得で値が反映されている")
        void update_thenGet_reflectsNewValue() throws Exception {
            Integer version = getVersion("LOCATION_CAPACITY_CASE");
            String body = String.format("""
                    { "paramValue": "25", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> putResponse = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);
            assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 一覧取得で値を確認
            ResponseEntity<String> getResponse = restTemplate.exchange(
                    BASE_URL, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            JsonNode json = parseJson(getResponse.getBody());
            JsonNode updated = findByParamKey(json, "LOCATION_CAPACITY_CASE");
            assertThat(updated.get("paramValue").asText()).isEqualTo("25");
        }

        @Test
        @DisplayName("SC-009: INTEGER型に不正な値（文字列）→ 422")
        void update_invalidIntegerValue_returns422() throws Exception {
            Integer version = getVersion("LOCATION_CAPACITY_CASE");
            String body = String.format("""
                    { "paramValue": "abc", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVALID_PARAM_VALUE");

            // DB検証: 値が変わっていない
            String dbValue = jdbcTemplate.queryForObject(
                    "SELECT param_value FROM system_parameters WHERE param_key = 'LOCATION_CAPACITY_CASE'",
                    String.class);
            assertThat(dbValue).isEqualTo("1");
        }

        @Test
        @DisplayName("SC-009: INTEGER型に小数値 → 422")
        void update_decimalValue_returns422() throws Exception {
            Integer version = getVersion("LOCATION_CAPACITY_CASE");
            String body = String.format("""
                    { "paramValue": "3.14", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("SC-009: INTEGER型に負の値 → 422")
        void update_negativeValue_returns422() throws Exception {
            Integer version = getVersion("LOCATION_CAPACITY_CASE");
            String body = String.format("""
                    { "paramValue": "-5", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("SC-009: INTEGER型に空文字 → 422")
        void update_emptyValue_returns422() throws Exception {
            Integer version = getVersion("LOCATION_CAPACITY_CASE");
            String body = String.format("""
                    { "paramValue": "", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("SC-011: 存在しないparamKey → 404")
        void update_nonExistentKey_returns404() throws Exception {
            String body = """
                    { "paramValue": "100", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/NON_EXISTENT_PARAM", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("SYSTEM_PARAMETER_NOT_FOUND");
        }

        @Test
        @DisplayName("楽観ロック競合 → 409")
        void update_versionMismatch_returns409() throws Exception {
            String body = """
                    { "paramValue": "50", "version": 999 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }

        @Test
        @DisplayName("SC-005: WAREHOUSE_MANAGERは更新不可 → 403")
        void update_asManager_returns403() throws Exception {
            HttpHeaders managerHeaders = loginAndGetHeaders(MANAGER_CODE, MANAGER_PASSWORD);

            String body = """
                    { "paramValue": "99", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, managerHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // DB検証: 値が変わっていない
            String dbValue = jdbcTemplate.queryForObject(
                    "SELECT param_value FROM system_parameters WHERE param_key = 'LOCATION_CAPACITY_CASE'",
                    String.class);
            assertThat(dbValue).isEqualTo("1");
        }

        @Test
        @DisplayName("SC-006: WAREHOUSE_STAFFは更新不可 → 403")
        void update_asStaff_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            String body = """
                    { "paramValue": "99", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, staffHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("SC-007: VIEWERは更新不可 → 403")
        void update_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders(VIEWER_CODE, VIEWER_PASSWORD);

            String body = """
                    { "paramValue": "99", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, viewerHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("SC-008: 未認証で更新 → 401")
        void update_unauthenticated_returns401() throws Exception {
            HttpHeaders headers = createJsonHeaders();
            String body = """
                    { "paramValue": "99", "version": 0 }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/LOCATION_CAPACITY_CASE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("SC-003: STRING型パラメータの値を正常に更新できる")
        void update_stringParam_returns200() throws Exception {
            Integer version = getVersion("DEFAULT_WAREHOUSE_CODE");
            String body = String.format("""
                    { "paramValue": "updated-string-value", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/DEFAULT_WAREHOUSE_CODE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("paramKey").asText()).isEqualTo("DEFAULT_WAREHOUSE_CODE");
            assertThat(json.get("paramValue").asText()).isEqualTo("updated-string-value");
            assertThat(json.get("valueType").asText()).isEqualTo("STRING");

            // DB検証
            var dbRow = jdbcTemplate.queryForMap(
                    "SELECT param_value, updated_by FROM system_parameters WHERE param_key = 'DEFAULT_WAREHOUSE_CODE'");
            assertThat(dbRow.get("param_value")).isEqualTo("updated-string-value");
            assertThat(dbRow.get("updated_by")).isNotNull();
        }

        @Test
        @DisplayName("SC-004: BOOLEAN型パラメータの値を正常に更新できる")
        void update_booleanParam_returns200() throws Exception {
            Integer version = getVersion("AUTO_ALLOCATE_ON_OUTBOUND");
            String body = String.format("""
                    { "paramValue": "false", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/AUTO_ALLOCATE_ON_OUTBOUND", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("paramKey").asText()).isEqualTo("AUTO_ALLOCATE_ON_OUTBOUND");
            assertThat(json.get("paramValue").asText()).isEqualTo("false");
            assertThat(json.get("valueType").asText()).isEqualTo("BOOLEAN");

            // DB検証
            var dbRow = jdbcTemplate.queryForMap(
                    "SELECT param_value, updated_by FROM system_parameters WHERE param_key = 'AUTO_ALLOCATE_ON_OUTBOUND'");
            assertThat(dbRow.get("param_value")).isEqualTo("false");
            assertThat(dbRow.get("updated_by")).isNotNull();
        }

        @Test
        @DisplayName("SC-004: BOOLEAN型パラメータ — 大文字FALSEが小文字正規化される")
        void update_booleanParam_uppercaseNormalized() throws Exception {
            Integer version = getVersion("AUTO_ALLOCATE_ON_OUTBOUND");
            String body = String.format("""
                    { "paramValue": "FALSE", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/AUTO_ALLOCATE_ON_OUTBOUND", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("paramValue").asText()).isEqualTo("false"); // 小文字正規化

            // DB検証
            String dbValue = jdbcTemplate.queryForObject(
                    "SELECT param_value FROM system_parameters WHERE param_key = 'AUTO_ALLOCATE_ON_OUTBOUND'",
                    String.class);
            assertThat(dbValue).isEqualTo("false");
        }

        @Test
        @DisplayName("SC-010: STRING型に501文字 → 422")
        void update_stringOver500chars_returns422() throws Exception {
            Integer version = getVersion("DEFAULT_WAREHOUSE_CODE");
            String longValue = "a".repeat(501);
            String body = String.format("""
                    { "paramValue": "%s", "version": %d }
                    """, longValue, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/DEFAULT_WAREHOUSE_CODE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVALID_PARAM_VALUE");

            // DB検証: 値が変わっていない
            String dbValue = jdbcTemplate.queryForObject(
                    "SELECT param_value FROM system_parameters WHERE param_key = 'DEFAULT_WAREHOUSE_CODE'",
                    String.class);
            assertThat(dbValue).isEqualTo("WH001");
        }

        @Test
        @DisplayName("BOOLEAN型に不正値 → 422")
        void update_booleanInvalidValue_returns422() throws Exception {
            Integer version = getVersion("AUTO_ALLOCATE_ON_OUTBOUND");
            String body = String.format("""
                    { "paramValue": "yes", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/AUTO_ALLOCATE_ON_OUTBOUND", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVALID_PARAM_VALUE");

            // DB検証: 値が変わっていない
            String dbValue = jdbcTemplate.queryForObject(
                    "SELECT param_value FROM system_parameters WHERE param_key = 'AUTO_ALLOCATE_ON_OUTBOUND'",
                    String.class);
            assertThat(dbValue).isEqualTo("true");
        }

        @Test
        @DisplayName("SC-010: STRING型に空文字 → 422")
        void update_stringEmptyValue_returns422() throws Exception {
            Integer version = getVersion("DEFAULT_WAREHOUSE_CODE");
            String body = String.format("""
                    { "paramValue": "", "version": %d }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/DEFAULT_WAREHOUSE_CODE", HttpMethod.PUT,
                    request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("INVALID_PARAM_VALUE");

            // DB検証: 値が変わっていない
            String dbValue = jdbcTemplate.queryForObject(
                    "SELECT param_value FROM system_parameters WHERE param_key = 'DEFAULT_WAREHOUSE_CODE'",
                    String.class);
            assertThat(dbValue).isEqualTo("WH001");
        }
    }

    // ========================================================
    // Helper methods
    // ========================================================

    private Integer getVersion(String paramKey) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM system_parameters WHERE param_key = ?",
                Integer.class, paramKey);
    }

    private JsonNode findByParamKey(JsonNode array, String paramKey) {
        for (JsonNode node : array) {
            if (paramKey.equals(node.get("paramKey").asText())) {
                return node;
            }
        }
        fail("paramKey not found in response: " + paramKey);
        return null; // unreachable
    }
}
