package com.wms.system.controller;

import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.entity.SystemParameter;
import com.wms.system.service.SystemParameterService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemParameterController.class)
@Import(SystemParameterControllerAuthTest.TestSecurityConfig.class)
@DisplayName("SystemParameterController 認可テスト")
class SystemParameterControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemParameterService systemParameterService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String BASE_URL = "/api/v1/system/parameters";

    // ===== 未認証（401） =====

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがGET一覧すると401")
    void list_anonymous_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPUTすると401")
    void update_anonymous_returns401() throws Exception {
        mockMvc.perform(put(BASE_URL + "/SESSION_TIMEOUT_MINUTES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paramValue\":\"30\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== 権限不足（403） =====

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    @DisplayName("WAREHOUSE_MANAGERがGET一覧すると403")
    void list_warehouseManager_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    @DisplayName("WAREHOUSE_STAFFがPUTすると403")
    void update_warehouseStaff_returns403() throws Exception {
        mockMvc.perform(put(BASE_URL + "/SESSION_TIMEOUT_MINUTES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paramValue\":\"30\"}"))
                .andExpect(status().isForbidden());
    }

    // ===== 認可通過 =====

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("SYSTEM_ADMINがGET一覧すると200")
    void list_systemAdmin_returns200() throws Exception {
        when(systemParameterService.findAll()).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("SYSTEM_ADMINがPUTすると200")
    void update_systemAdmin_returns200() throws Exception {
        SystemParameter updated = SystemParameter.builder()
                .paramKey("SESSION_TIMEOUT_MINUTES")
                .paramValue("30")
                .defaultValue("60")
                .displayName("SESSION_TIMEOUT_MINUTES")
                .category("SECURITY")
                .valueType("INTEGER")
                .build();
        when(systemParameterService.updateValue(eq("SESSION_TIMEOUT_MINUTES"), eq("30")))
                .thenReturn(updated);

        mockMvc.perform(put(BASE_URL + "/SESSION_TIMEOUT_MINUTES")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paramValue\":\"30\"}"))
                .andExpect(status().isOk());
    }

    // --- Test Config ---

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

        @Bean
        FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
                JwtAuthenticationFilter filter) {
            FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
            bean.setEnabled(false);
            return bean;
        }
    }
}
