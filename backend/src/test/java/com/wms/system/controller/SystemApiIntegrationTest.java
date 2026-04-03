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

@DisplayName("結合テスト: システム共通API（営業日・セッション設定）")
class SystemApiIntegrationTest extends IntegrationTestBase {

    private static final String BUSINESS_DATE_URL = "/api/v1/system/business-date";
    private static final String SESSION_CONFIG_URL = "/api/v1/system/session-config";
    private static final String ADMIN_CODE = "admin001";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String STAFF_CODE = "wh_staff01";
    private static final String STAFF_PASSWORD = "Staff@1234";

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        // テストで変更したパラメータ値・バージョンを初期値に戻す（テスト分離）
        jdbcTemplate.update(
                "UPDATE system_parameters SET param_value = default_value, version = 0 WHERE param_key = 'SESSION_TIMEOUT_MINUTES'");
        adminHeaders = loginAndGetHeaders(ADMIN_CODE, ADMIN_PASSWORD);
    }

    // ========================================================
    // GET /api/v1/system/business-date — 営業日取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/system/business-date — 営業日取得")
    class GetBusinessDate {

        @Test
        @DisplayName("正常系: SYSTEM_ADMINで営業日が取得できる")
        void getBusinessDate_asAdmin_returns200() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BUSINESS_DATE_URL, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("businessDate")).isTrue();
            // 日付形式の検証（yyyy-MM-dd）
            assertThat(json.get("businessDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        }

        @Test
        @DisplayName("正常系: 営業日がDB値と一致する")
        void getBusinessDate_matchesDatabase() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BUSINESS_DATE_URL, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            JsonNode json = parseJson(response.getBody());
            String apiDate = json.get("businessDate").asText();

            // DB値と照合
            String dbDate = jdbcTemplate.queryForObject(
                    "SELECT TO_CHAR(business_date, 'YYYY-MM-DD') FROM business_date WHERE id = 1",
                    String.class);
            assertThat(apiDate).isEqualTo(dbDate);
        }

        @Test
        @DisplayName("正常系: WAREHOUSE_STAFFでも営業日が取得できる（isAuthenticated）")
        void getBusinessDate_asStaff_returns200() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    BUSINESS_DATE_URL, HttpMethod.GET,
                    new HttpEntity<>(staffHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("businessDate")).isTrue();
        }

        @Test
        @DisplayName("異常系: 未認証でアクセス → 401")
        void getBusinessDate_unauthenticated_returns401() throws Exception {
            HttpHeaders headers = createJsonHeaders();

            ResponseEntity<String> response = restTemplate.exchange(
                    BUSINESS_DATE_URL, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ========================================================
    // GET /api/v1/system/session-config — セッション設定取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/system/session-config — セッション設定取得")
    class GetSessionConfig {

        @Test
        @DisplayName("正常系: SYSTEM_ADMINでセッション設定が取得できる")
        void getSessionConfig_asAdmin_returns200() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    SESSION_CONFIG_URL, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.has("timeoutMinutes")).isTrue();
            assertThat(json.has("warningMinutes")).isTrue();

            // SESSION_TIMEOUT_MINUTES=60 なので timeout=60, warning=55
            assertThat(json.get("timeoutMinutes").asInt()).isEqualTo(60);
            assertThat(json.get("warningMinutes").asInt()).isEqualTo(55);
        }

        @Test
        @DisplayName("正常系: WAREHOUSE_STAFFでもセッション設定が取得できる（isAuthenticated）")
        void getSessionConfig_asStaff_returns200() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders(STAFF_CODE, STAFF_PASSWORD);

            ResponseEntity<String> response = restTemplate.exchange(
                    SESSION_CONFIG_URL, HttpMethod.GET,
                    new HttpEntity<>(staffHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("timeoutMinutes").asInt()).isEqualTo(60);
        }

        @Test
        @DisplayName("正常系: パラメータ更新後にセッション設定に反映される")
        void getSessionConfig_afterParamUpdate_reflectsNewValue() throws Exception {
            // SESSION_TIMEOUT_MINUTES を 30 に更新
            Integer version = getVersion("SESSION_TIMEOUT_MINUTES");
            String updateBody = String.format("""
                    { "paramValue": "30", "version": %d }
                    """, version);

            HttpEntity<String> updateRequest = new HttpEntity<>(updateBody, adminHeaders);
            ResponseEntity<String> updateResponse = restTemplate.exchange(
                    "/api/v1/system/parameters/SESSION_TIMEOUT_MINUTES", HttpMethod.PUT,
                    updateRequest, String.class);
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // セッション設定取得で反映を確認
            ResponseEntity<String> response = restTemplate.exchange(
                    SESSION_CONFIG_URL, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("timeoutMinutes").asInt()).isEqualTo(30);
            assertThat(json.get("warningMinutes").asInt()).isEqualTo(25);
            // クリーンアップは @BeforeEach のSQL resetで行われる
        }

        @Test
        @DisplayName("異常系: 未認証でアクセス → 401")
        void getSessionConfig_unauthenticated_returns401() throws Exception {
            HttpHeaders headers = createJsonHeaders();

            ResponseEntity<String> response = restTemplate.exchange(
                    SESSION_CONFIG_URL, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
}
