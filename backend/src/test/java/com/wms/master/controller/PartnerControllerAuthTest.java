package com.wms.master.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.entity.Partner;
import com.wms.master.service.PartnerService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 取引先コントローラーの認可テスト。
 * セキュリティフィルターを有効にして @PreAuthorize による認可チェックを検証する。
 */
@WebMvcTest(PartnerController.class)
@Import(PartnerControllerAuthTest.TestSecurityConfig.class)
@DisplayName("PartnerController 認可テスト")
class PartnerControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PartnerService partnerService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    private static final String BASE_URL = "/api/v1/master/partners";

    private static final String VALID_CREATE_JSON = """
            {"partnerCode":"SUP-001","partnerName":"仕入先A","partnerNameKana":"シイレサキエー","partnerType":"SUPPLIER"}
            """;
    private static final String VALID_UPDATE_JSON = """
            {"partnerName":"仕入先A","partnerNameKana":"シイレサキエー","partnerType":"SUPPLIER","version":0}
            """;
    private static final String VALID_TOGGLE_JSON = """
            {"isActive":false,"version":0}
            """;

    // ===== 未認証（401） =====

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
        mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TOGGLE_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ===== 権限不足（403） =====

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
        mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TOGGLE_JSON))
                .andExpect(status().isForbidden());
    }

    // ===== 認可通過 =====

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    @DisplayName("WAREHOUSE_MANAGERがPOSTすると201を返す")
    void create_warehouseManager_returns201() throws Exception {
        Partner created = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
        when(partnerService.create(any(Partner.class))).thenReturn(created);

        mockMvc.perform(post(BASE_URL)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("SYSTEM_ADMINがPUTすると200を返す")
    void update_systemAdmin_returns200() throws Exception {
        Partner updated = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
        when(partnerService.update(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(put(BASE_URL + "/1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_JSON))
                .andExpect(status().isOk());
    }

    // --- Test Config ---

    /**
     * テスト用セキュリティ設定。
     * CSRF無効・認証必須・メソッドセキュリティ有効の最小構成。
     */
    @TestConfiguration
    @EnableMethodSecurity
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

    private static Partner createPartner(Long id, String code, String name, String type) {
        Partner p = new Partner();
        p.setPartnerCode(code);
        p.setPartnerName(name);
        p.setPartnerType(type);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(p, id);
                var createdAtField = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(p, OffsetDateTime.now());
                var updatedAtField = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAtField.setAccessible(true);
                updatedAtField.set(p, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return p;
    }
}
