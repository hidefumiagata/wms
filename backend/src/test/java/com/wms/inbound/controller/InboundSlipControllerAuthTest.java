package com.wms.inbound.controller;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.service.InboundSlipService;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InboundSlipController.class)
@Import(InboundSlipControllerAuthTest.TestSecurityConfig.class)
@DisplayName("InboundSlipController 認可テスト")
class InboundSlipControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundSlipService inboundSlipService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String SLIPS_URL = "/api/v1/inbound/slips";
    private static final String RESULTS_URL = "/api/v1/inbound/results";

    // ===== 未認証（401） =====

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがGET一覧すると401")
    void listSlips_anonymous_returns401() throws Exception {
        mockMvc.perform(get(SLIPS_URL).param("warehouseId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがGET詳細すると401")
    void getSlip_anonymous_returns401() throws Exception {
        mockMvc.perform(get(SLIPS_URL + "/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPOST(create)すると401")
    void create_anonymous_returns401() throws Exception {
        mockMvc.perform(post(SLIPS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがDELETEすると401")
    void delete_anonymous_returns401() throws Exception {
        mockMvc.perform(delete(SLIPS_URL + "/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPOST(confirm)すると401")
    void confirm_anonymous_returns401() throws Exception {
        mockMvc.perform(post(SLIPS_URL + "/1/confirm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPOST(cancel)すると401")
    void cancel_anonymous_returns401() throws Exception {
        mockMvc.perform(post(SLIPS_URL + "/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPOST(inspect)すると401")
    void inspect_anonymous_returns401() throws Exception {
        mockMvc.perform(post(SLIPS_URL + "/1/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがPOST(store)すると401")
    void store_anonymous_returns401() throws Exception {
        mockMvc.perform(post(SLIPS_URL + "/1/store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("未認証ユーザーがGET(results)すると401")
    void results_anonymous_returns401() throws Exception {
        mockMvc.perform(get(RESULTS_URL).param("warehouseId", "1"))
                .andExpect(status().isUnauthorized());
    }

    // ===== 権限不足（403） =====

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがPOST(create)すると403")
    void create_viewer_returns403() throws Exception {
        mockMvc.perform(post(SLIPS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":1,\"plannedDate\":\"2026-03-20\",\"slipType\":\"NORMAL\",\"partnerId\":1,\"lines\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがDELETEすると403")
    void delete_viewer_returns403() throws Exception {
        mockMvc.perform(delete(SLIPS_URL + "/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがPOST(confirm)すると403")
    void confirm_viewer_returns403() throws Exception {
        mockMvc.perform(post(SLIPS_URL + "/1/confirm"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがPOST(cancel)すると403")
    void cancel_viewer_returns403() throws Exception {
        mockMvc.perform(post(SLIPS_URL + "/1/cancel"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがPOST(inspect)すると403")
    void inspect_viewer_returns403() throws Exception {
        mockMvc.perform(post(SLIPS_URL + "/1/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがPOST(store)すると403")
    void store_viewer_returns403() throws Exception {
        mockMvc.perform(post(SLIPS_URL + "/1/store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isForbidden());
    }

    // ===== 認可通過 =====

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがGET一覧すると200（isAuthenticated）")
    void listSlips_viewer_returns200() throws Exception {
        when(inboundSlipService.search(
                eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(SLIPS_URL)
                        .param("warehouseId", "1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがGET詳細すると200（isAuthenticated）")
    void getSlip_viewer_returns200() throws Exception {
        InboundSlip slip = createSlip(1L);
        when(inboundSlipService.findByIdWithLines(1L)).thenReturn(slip);
        when(inboundSlipService.resolveUserName(any())).thenReturn(null);

        mockMvc.perform(get(SLIPS_URL + "/1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("VIEWERがGET(results)すると200（isAuthenticated）")
    void results_viewer_returns200() throws Exception {
        when(inboundSlipService.findResults(
                eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(RESULTS_URL)
                        .param("warehouseId", "1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    @DisplayName("WAREHOUSE_STAFFがGET一覧すると200")
    void listSlips_staff_returns200() throws Exception {
        when(inboundSlipService.search(
                eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(SLIPS_URL)
                        .param("warehouseId", "1")
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

    // --- Helper ---

    private static InboundSlip createSlip(Long id) {
        InboundSlip slip = InboundSlip.builder()
                .slipNumber("INB-20260320-0001")
                .slipType("NORMAL")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京DC")
                .plannedDate(LocalDate.of(2026, 3, 20))
                .status("PLANNED")
                .lines(new ArrayList<>())
                .build();
        try {
            var field = InboundSlip.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(slip, id);
            var createdAt = InboundSlip.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(slip, OffsetDateTime.now());
            var createdBy = InboundSlip.class.getDeclaredField("createdBy");
            createdBy.setAccessible(true);
            createdBy.set(slip, 10L);
            var updatedAt = InboundSlip.class.getDeclaredField("updatedAt");
            updatedAt.setAccessible(true);
            updatedAt.set(slip, OffsetDateTime.now());
            var updatedBy = InboundSlip.class.getDeclaredField("updatedBy");
            updatedBy.setAccessible(true);
            updatedBy.set(slip, 10L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return slip;
    }
}
