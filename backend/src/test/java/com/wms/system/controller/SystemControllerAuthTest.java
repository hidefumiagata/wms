package com.wms.system.controller;

import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.service.SystemParameterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import(SystemControllerAuthTest.TestSecurityConfig.class)
@DisplayName("SystemController 認可テスト")
class SystemControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemParameterService systemParameterService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // ===== 未認証（401） =====

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーが session-config にアクセスすると401")
    void sessionConfig_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/system/session-config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーが business-date にアクセスすると401")
    void businessDate_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/system/business-date"))
                .andExpect(status().isUnauthorized());
    }

    // ===== 認可通過 =====

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    @DisplayName("認証済みユーザーが session-config にアクセスすると200")
    void sessionConfig_authenticated_returns200() throws Exception {
        when(systemParameterService.getIntValue("SESSION_TIMEOUT_MINUTES")).thenReturn(60);

        mockMvc.perform(get("/api/v1/system/session-config")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    @DisplayName("認証済みユーザーが business-date にアクセスすると200")
    void businessDate_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/system/business-date")
                        .header("X-Requested-With", "XMLHttpRequest"))
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
