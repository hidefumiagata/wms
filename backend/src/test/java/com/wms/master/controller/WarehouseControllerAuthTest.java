package com.wms.master.controller;

import com.wms.master.entity.Warehouse;
import com.wms.master.service.WarehouseService;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.shared.security.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 倉庫コントローラーの認可テスト。
 * セキュリティフィルターを有効にして @PreAuthorize による認可チェックを検証する。
 */
@WebMvcTest(WarehouseController.class)
@Import(WarehouseControllerAuthTest.TestSecurityConfig.class)
@DisplayName("WarehouseController 認可テスト")
class WarehouseControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    private static final String BASE_URL = "/api/v1/master/warehouses";

    private static final String VALID_CREATE_JSON = """
            {"warehouseCode":"TKYO","warehouseName":"東京倉庫"}
            """;
    private static final String VALID_UPDATE_JSON = """
            {"warehouseName":"東京倉庫","version":0}
            """;
    private static final String VALID_TOGGLE_JSON = """
            {"isActive":false,"version":0}
            """;

    // ===== 未認証（401） =====

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがGET一覧すると401を返す")
    void list_anonymous_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがGET詳細すると401を返す")
    void get_anonymous_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがGET存在確認すると401を返す")
    void exists_anonymous_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/exists").param("warehouseCode", "TKYO"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPOSTすると401を返す")
    void create_anonymous_returns401() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPUTすると401を返す")
    void update_anonymous_returns401() throws Exception {
        mockMvc.perform(put(BASE_URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPATCHすると401を返す")
    void toggle_anonymous_returns401() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TOGGLE_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ===== 権限不足（403） =====

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがPOSTすると403を返す")
    void create_viewer_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    @DisplayName("WAREHOUSE_STAFFがPOSTすると403を返す")
    void create_warehouseStaff_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがPUTすると403を返す")
    void update_viewer_returns403() throws Exception {
        mockMvc.perform(put(BASE_URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    @DisplayName("WAREHOUSE_STAFFがPATCHすると403を返す")
    void toggle_warehouseStaff_returns403() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TOGGLE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがPATCHすると403を返す")
    void toggle_viewer_returns403() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TOGGLE_JSON))
                .andExpect(status().isForbidden());
    }

    // ===== 認可通過 =====

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    @DisplayName("WAREHOUSE_MANAGERがPOSTすると201を返す")
    void create_warehouseManager_returns201() throws Exception {
        Warehouse created = createWarehouse(1L, "TKYO", "東京倉庫");
        when(warehouseService.create(any(Warehouse.class))).thenReturn(created);

        mockMvc.perform(post(BASE_URL)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseCode").value("TKYO"));
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("SYSTEM_ADMINがPUTすると200を返す")
    void update_systemAdmin_returns200() throws Exception {
        Warehouse updated = createWarehouse(1L, "TKYO", "東京倉庫");
        when(warehouseService.update(anyLong(), anyString(), any(), any(), anyInt()))
                .thenReturn(updated);

        mockMvc.perform(put(BASE_URL + "/1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseCode").value("TKYO"));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    @DisplayName("WAREHOUSE_MANAGERがPATCH(deactivate)すると200を返す")
    void toggle_warehouseManager_returns200() throws Exception {
        Warehouse updated = createWarehouse(1L, "TKYO", "東京倉庫");
        when(warehouseService.toggleActive(anyLong(), anyBoolean(), anyInt())).thenReturn(updated);

        mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TOGGLE_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    @DisplayName("WAREHOUSE_STAFFがGET一覧すると200を返す（isAuthenticated）")
    void list_warehouseStaff_returns200() throws Exception {
        when(warehouseService.search(any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

        mockMvc.perform(get(BASE_URL)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    @DisplayName("WAREHOUSE_STAFFがGET詳細すると200を返す（isAuthenticated）")
    void get_warehouseStaff_returns200() throws Exception {
        Warehouse w = createWarehouse(1L, "TKYO", "東京倉庫");
        when(warehouseService.findById(1L)).thenReturn(w);

        mockMvc.perform(get(BASE_URL + "/1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    // --- Test Config ---

    /**
     * テスト用セキュリティ設定。
     * CSRF無効・認証必須・メソッドセキュリティ有効の最小構成。
     */
    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, res, e) ->
                                    res.sendError(401, "Unauthorized"))
                            .accessDeniedHandler((req, res, e) ->
                                    res.sendError(403, "Forbidden")));
            return http.build();
        }

        /**
         * JwtAuthenticationFilter はモックなので doFilter が chain を呼ばない。
         * サーブレットフィルターとしての登録を無効化し、リクエストが DispatcherServlet に届くようにする。
         */
        @Bean
        FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
                JwtAuthenticationFilter filter) {
            FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
            bean.setEnabled(false);
            return bean;
        }
    }

    // --- Helper ---

    private static Warehouse createWarehouse(Long id, String code, String name) {
        Warehouse w = new Warehouse();
        w.setWarehouseCode(code);
        w.setWarehouseName(name);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(w, id);
                var createdAtField = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(w, OffsetDateTime.now());
                var updatedAtField = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAtField.setAccessible(true);
                updatedAtField.set(w, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return w;
    }
}
