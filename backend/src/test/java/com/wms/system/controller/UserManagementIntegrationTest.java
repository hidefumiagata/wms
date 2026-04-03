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

@DisplayName("マスタ結合テスト: ユーザー管理")
class UserManagementIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/v1/master/users";

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        // Reset test data state
        jdbcTemplate.update(
                "DELETE FROM users WHERE user_code LIKE 'intg_test_%'");
        jdbcTemplate.update(
                "UPDATE users SET failed_login_count = 0, locked = false, locked_at = NULL WHERE user_code = 'locked_user'");
        // Ensure locked_user is locked for unlock tests
        jdbcTemplate.update(
                "UPDATE users SET failed_login_count = 5, locked = true, locked_at = now() WHERE user_code = 'locked_user'");

        adminHeaders = loginAndGetHeaders("admin001", "Admin@1234");
    }

    // ========================================================
    // SC-USR01: ユーザー登録
    // ========================================================

    @Nested
    @DisplayName("POST /api/v1/master/users — ユーザー作成")
    class CreateUser {

        @Test
        @DisplayName("SC-USR01: SYSTEM_ADMINが初期パスワード付きで新規ユーザーを登録できる")
        void create_validInput_returns201() throws Exception {
            String body = """
                    {
                        "userCode": "intg_test_01",
                        "fullName": "テスト太郎",
                        "email": "intg_test_01@example.com",
                        "role": "WAREHOUSE_STAFF",
                        "initialPassword": "Initial@123"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();
            assertThat(response.getHeaders().getLocation().getPath())
                    .startsWith("/api/v1/master/users/");

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("userCode").asText()).isEqualTo("intg_test_01");
            assertThat(json.get("fullName").asText()).isEqualTo("テスト太郎");
            assertThat(json.get("email").asText()).isEqualTo("intg_test_01@example.com");
            assertThat(json.get("role").asText()).isEqualTo("WAREHOUSE_STAFF");
            assertThat(json.get("isActive").asBoolean()).isTrue();
            assertThat(json.get("locked").asBoolean()).isFalse();
            assertThat(json.get("passwordChangeRequired").asBoolean()).isTrue();

            // DB検証: BCryptハッシュ形式
            String hash = jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM users WHERE user_code = 'intg_test_01'",
                    String.class);
            assertThat(hash).startsWith("$2a$");
            assertThat(hash).isNotEqualTo("Initial@123");

            // DB検証: フラグ値
            Boolean pwChangeRequired = jdbcTemplate.queryForObject(
                    "SELECT password_change_required FROM users WHERE user_code = 'intg_test_01'",
                    Boolean.class);
            assertThat(pwChangeRequired).isTrue();
        }

        @Test
        @DisplayName("重複するユーザーコードで作成 → 409")
        void create_duplicateCode_returns409() throws Exception {
            String body = """
                    {
                        "userCode": "admin001",
                        "fullName": "重複テスト",
                        "email": "dup@example.com",
                        "role": "WAREHOUSE_STAFF",
                        "initialPassword": "Password@123"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("DUPLICATE_CODE");
        }

        @Test
        @DisplayName("SC-USR02: パスワード8文字未満 → 400")
        void create_shortPassword_returns400() throws Exception {
            String body = """
                    {
                        "userCode": "intg_test_02",
                        "fullName": "短パスワードテスト",
                        "email": "short@example.com",
                        "role": "WAREHOUSE_STAFF",
                        "initialPassword": "short"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("必須フィールドなし → 400")
        void create_missingFields_returns400() throws Exception {
            String body = """
                    {
                        "userCode": "intg_test_03"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("不正なユーザーコードパターン → 400")
        void create_invalidUserCodePattern_returns400() throws Exception {
            String body = """
                    {
                        "userCode": "invalid user!",
                        "fullName": "不正コードテスト",
                        "email": "invalid@example.com",
                        "role": "WAREHOUSE_STAFF",
                        "initialPassword": "Password@123"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ========================================================
    // GET /api/v1/master/users/{id} — 詳細取得
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/users/{id} — ユーザー詳細取得")
    class GetUser {

        @Test
        @DisplayName("正常系: 存在するユーザーの詳細を取得できる")
        void get_existingUser_returns200() throws Exception {
            Long adminId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + adminId, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("userCode").asText()).isEqualTo("admin001");
            assertThat(json.get("role").asText()).isEqualTo("SYSTEM_ADMIN");
            assertThat(json.get("id").asLong()).isEqualTo(adminId);
            assertThat(json.has("version")).isTrue();
            assertThat(json.has("createdAt")).isTrue();
            assertThat(json.has("updatedAt")).isTrue();
        }

        @Test
        @DisplayName("存在しないID → 404")
        void get_nonExistentId_returns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("USER_NOT_FOUND");
        }
    }

    // ========================================================
    // PUT /api/v1/master/users/{id} — ユーザー更新
    // ========================================================

    @Nested
    @DisplayName("PUT /api/v1/master/users/{id} — ユーザー更新")
    class UpdateUser {

        @Test
        @DisplayName("正常系: ユーザー情報を更新できる")
        void update_validInput_returns200() throws Exception {
            // まずテストユーザーを作成
            Long userId = createTestUser("intg_test_upd", "更新前ユーザー",
                    "upd@example.com", "WAREHOUSE_STAFF");
            Integer version = getVersion(userId);

            String body = String.format("""
                    {
                        "fullName": "更新後ユーザー",
                        "email": "updated@example.com",
                        "role": "WAREHOUSE_MANAGER",
                        "isActive": true,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + userId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("fullName").asText()).isEqualTo("更新後ユーザー");
            assertThat(json.get("email").asText()).isEqualTo("updated@example.com");
            assertThat(json.get("role").asText()).isEqualTo("WAREHOUSE_MANAGER");
        }

        @Test
        @DisplayName("SC-USR04: 自分自身のロール変更 → 422 CANNOT_CHANGE_SELF_ROLE")
        void update_selfRoleChange_returns422() throws Exception {
            Long adminId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);
            Integer version = getVersion(adminId);

            String body = String.format("""
                    {
                        "fullName": "管理者 001",
                        "email": "admin001@example.com",
                        "role": "WAREHOUSE_MANAGER",
                        "isActive": true,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + adminId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("CANNOT_CHANGE_SELF_ROLE");
        }

        @Test
        @DisplayName("SC-USR05: 自分自身の無効化 → 422 CANNOT_DEACTIVATE_SELF")
        void update_selfDeactivation_returns422() throws Exception {
            Long adminId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);
            Integer version = getVersion(adminId);

            String body = String.format("""
                    {
                        "fullName": "管理者 001",
                        "email": "admin001@example.com",
                        "role": "SYSTEM_ADMIN",
                        "isActive": false,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + adminId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("CANNOT_DEACTIVATE_SELF");
        }

        @Test
        @DisplayName("楽観的ロック競合 → 409 OPTIMISTIC_LOCK_CONFLICT")
        void update_staleVersion_returns409() throws Exception {
            Long userId = createTestUser("intg_test_lock", "ロックテスト",
                    "lock@example.com", "WAREHOUSE_STAFF");

            String body = """
                    {
                        "fullName": "ロックテスト更新",
                        "email": "lock@example.com",
                        "role": "WAREHOUSE_STAFF",
                        "isActive": true,
                        "version": 999
                    }
                    """;

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + userId, HttpMethod.PUT, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }

        @Test
        @DisplayName("存在しないID → 404")
        void update_nonExistentId_returns404() throws Exception {
            String body = """
                    {
                        "fullName": "不明ユーザー",
                        "email": "none@example.com",
                        "role": "WAREHOUSE_STAFF",
                        "isActive": true,
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
    // PATCH /api/v1/master/users/{id}/toggle-active — 有効/無効切替
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/users/{id}/toggle-active — 有効/無効切替")
    class ToggleActive {

        @Test
        @DisplayName("正常系: ユーザーを無効化できる")
        void toggleActive_deactivate_returns200() throws Exception {
            Long userId = createTestUser("intg_test_toggle", "トグルテスト",
                    "toggle@example.com", "WAREHOUSE_STAFF");
            Integer version = getVersion(userId);

            String body = String.format("""
                    {
                        "isActive": false,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + userId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("正常系: ユーザーを有効化できる")
        void toggleActive_activate_returns200() throws Exception {
            Long userId = createTestUser("intg_test_toggle2", "トグルテスト2",
                    "toggle2@example.com", "WAREHOUSE_STAFF");
            // まず無効化
            jdbcTemplate.update("UPDATE users SET is_active = false WHERE id = ?", userId);
            Integer version = getVersion(userId);

            String body = String.format("""
                    {
                        "isActive": true,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + userId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("isActive").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("自分自身の無効化 → 422 CANNOT_DEACTIVATE_SELF")
        void toggleActive_selfDeactivation_returns422() throws Exception {
            Long adminId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE user_code = 'admin001'", Long.class);
            Integer version = getVersion(adminId);

            String body = String.format("""
                    {
                        "isActive": false,
                        "version": %d
                    }
                    """, version);

            HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + adminId + "/toggle-active",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("code").asText()).isEqualTo("CANNOT_DEACTIVATE_SELF");
        }
    }

    // ========================================================
    // PATCH /api/v1/master/users/{id}/unlock — アカウントロック解除
    // ========================================================

    @Nested
    @DisplayName("PATCH /api/v1/master/users/{id}/unlock — アカウントロック解除")
    class UnlockUser {

        @Test
        @DisplayName("SC-USR06: ロック中ユーザーのロックを解除できる")
        void unlock_lockedUser_returns204() throws Exception {
            Long lockedUserId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE user_code = 'locked_user'", Long.class);

            HttpEntity<String> request = new HttpEntity<>(null, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + lockedUserId + "/unlock",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // DB検証
            Boolean locked = jdbcTemplate.queryForObject(
                    "SELECT locked FROM users WHERE user_code = 'locked_user'",
                    Boolean.class);
            Integer failedCount = jdbcTemplate.queryForObject(
                    "SELECT failed_login_count FROM users WHERE user_code = 'locked_user'",
                    Integer.class);
            assertThat(locked).isFalse();
            assertThat(failedCount).isZero();
        }

        @Test
        @DisplayName("SC-USR07: ロック解除後にログインできる")
        void unlock_thenLoginSucceeds() throws Exception {
            Long lockedUserId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE user_code = 'locked_user'", Long.class);

            // アンロック
            HttpEntity<String> request = new HttpEntity<>(null, adminHeaders);
            restTemplate.exchange(
                    BASE_URL + "/" + lockedUserId + "/unlock",
                    HttpMethod.PATCH, request, String.class);

            // ログイン成功確認
            ResponseEntity<String> loginResponse = loginRaw("locked_user", "Correct@1234");
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("存在しないユーザーのロック解除 → 404")
        void unlock_nonExistentId_returns404() throws Exception {
            HttpEntity<String> request = new HttpEntity<>(null, adminHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/999999/unlock",
                    HttpMethod.PATCH, request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ========================================================
    // GET /api/v1/master/users — 一覧取得・ページネーション・ソート
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/users — 一覧取得")
    class ListUsers {

        @Test
        @DisplayName("正常系: ページネーション付き一覧取得")
        void list_defaultPagination_returns200() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=5",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("content").isArray()).isTrue();
            assertThat(json.get("page").asInt()).isZero();
            assertThat(json.get("size").asInt()).isEqualTo(5);
            assertThat(json.get("totalElements").asLong()).isPositive();
            assertThat(json.get("totalPages").asInt()).isPositive();
        }

        @Test
        @DisplayName("キーワード検索: ユーザーコードで検索")
        void list_keywordSearch_byCode() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?keyword=admin001&page=0&size=10",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            JsonNode content = json.get("content");
            assertThat(content.size()).isGreaterThanOrEqualTo(1);
            boolean found = false;
            for (JsonNode user : content) {
                if ("admin001".equals(user.get("userCode").asText())) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("ロール別フィルタ: SYSTEM_ADMINのみ取得")
        void list_filterByRole_systemAdmin() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?role=SYSTEM_ADMIN&page=0&size=100",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            for (JsonNode user : json.get("content")) {
                assertThat(user.get("role").asText()).isEqualTo("SYSTEM_ADMIN");
            }
        }

        @Test
        @DisplayName("ステータスフィルタ: LOCKEDのみ取得")
        void list_filterByStatus_locked() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?status=LOCKED&page=0&size=100",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            for (JsonNode user : json.get("content")) {
                assertThat(user.get("locked").asBoolean()).isTrue();
            }
        }

        @Test
        @DisplayName("ステータスフィルタ: INACTIVEのみ取得")
        void list_filterByStatus_inactive() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?status=INACTIVE&page=0&size=100",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            for (JsonNode user : json.get("content")) {
                assertThat(user.get("isActive").asBoolean()).isFalse();
            }
        }

        @Test
        @DisplayName("ソート: userCodeの昇順")
        void list_sortByUserCodeAsc() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?sort=userCode,asc&page=0&size=100",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode content = parseJson(response.getBody()).get("content");
            for (int i = 1; i < content.size(); i++) {
                String prev = content.get(i - 1).get("userCode").asText();
                String curr = content.get(i).get("userCode").asText();
                assertThat(prev.compareTo(curr)).isLessThanOrEqualTo(0);
            }
        }

        @Test
        @DisplayName("all=true: 全件取得（ページネーションなし）")
        void list_allTrue_returnsAllUsers() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?all=true",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("totalPages").asInt()).isEqualTo(1);
            assertThat(json.get("content").size()).isEqualTo(
                    json.get("totalElements").asInt());
        }
    }

    // ========================================================
    // GET /api/v1/master/users/exists — 重複チェック
    // ========================================================

    @Nested
    @DisplayName("GET /api/v1/master/users/exists — 重複チェック")
    class CheckExists {

        @Test
        @DisplayName("存在するユーザーコード → exists: true")
        void exists_existingCode_returnsTrue() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/exists?code=admin001",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("exists").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("存在しないユーザーコード → exists: false")
        void exists_nonExistingCode_returnsFalse() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/exists?code=nonexistent_user_xyz",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("exists").asBoolean()).isFalse();
        }
    }

    // ========================================================
    // ロール別アクセス制御
    // ========================================================

    @Nested
    @DisplayName("ロール別アクセス制御 — SYSTEM_ADMINのみ操作可能")
    class AccessControl {

        @Test
        @DisplayName("WAREHOUSE_MANAGER → 403")
        void list_asManager_returns403() throws Exception {
            HttpHeaders managerHeaders = loginAndGetHeaders("wh_manager01", "Manager@1234");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=10",
                    HttpMethod.GET, new HttpEntity<>(managerHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("WAREHOUSE_STAFF → 403")
        void list_asStaff_returns403() throws Exception {
            HttpHeaders staffHeaders = loginAndGetHeaders("wh_staff01", "Staff@1234");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=10",
                    HttpMethod.GET, new HttpEntity<>(staffHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("VIEWER → 403")
        void list_asViewer_returns403() throws Exception {
            HttpHeaders viewerHeaders = loginAndGetHeaders("viewer01", "Test@1234");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=10",
                    HttpMethod.GET, new HttpEntity<>(viewerHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("未認証 → 401")
        void list_unauthenticated_returns401() {
            HttpHeaders noAuthHeaders = createJsonHeaders();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "?page=0&size=10",
                    HttpMethod.GET, new HttpEntity<>(noAuthHeaders), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("WAREHOUSE_MANAGERがユーザー作成 → 403")
        void create_asManager_returns403() throws Exception {
            HttpHeaders managerHeaders = loginAndGetHeaders("wh_manager01", "Manager@1234");

            String body = """
                    {
                        "userCode": "intg_test_forbidden",
                        "fullName": "禁止テスト",
                        "email": "forbidden@example.com",
                        "role": "WAREHOUSE_STAFF",
                        "initialPassword": "Password@123"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, managerHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ========================================================
    // SC-USR09: 初回パスワード変更フラグ
    // ========================================================

    @Nested
    @DisplayName("SC-USR09: 初回パスワード変更フラグ")
    class PasswordChangeRequired {

        @Test
        @DisplayName("登録直後のユーザーはpasswordChangeRequired=trueで作成される")
        void create_setsPasswordChangeRequired() throws Exception {
            String body = """
                    {
                        "userCode": "intg_test_pwflag",
                        "fullName": "PWフラグテスト",
                        "email": "pwflag@example.com",
                        "role": "WAREHOUSE_STAFF",
                        "initialPassword": "Initial@123"
                    }
                    """;

            ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode json = parseJson(response.getBody());
            assertThat(json.get("passwordChangeRequired").asBoolean()).isTrue();

            // ログインしてレスポンス確認
            ResponseEntity<String> loginResponse = loginRaw("intg_test_pwflag", "Initial@123");
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode loginJson = parseJson(loginResponse.getBody());
            assertThat(loginJson.get("passwordChangeRequired").asBoolean()).isTrue();
        }
    }

    // ========================================================
    // Helper methods
    // ========================================================

    private Long createTestUser(String userCode, String fullName, String email, String role) throws Exception {
        String body = String.format("""
                {
                    "userCode": "%s",
                    "fullName": "%s",
                    "email": "%s",
                    "role": "%s",
                    "initialPassword": "Password@123"
                }
                """, userCode, fullName, email, role);

        ResponseEntity<String> response = postJson(BASE_URL, body, adminHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode json = parseJson(response.getBody());
        return json.get("id").asLong();
    }

    private Integer getVersion(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM users WHERE id = ?", Integer.class, userId);
    }
}
