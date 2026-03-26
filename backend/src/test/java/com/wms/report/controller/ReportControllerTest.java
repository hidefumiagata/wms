package com.wms.report.controller;

import com.wms.report.service.ReportExportService;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(ReportControllerTest.TestSecurityConfig.class)
@DisplayName("ReportController テスト")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportExportService reportExportService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String REPORTS_BASE = "/api/v1/reports";

    @Nested
    @DisplayName("認可テスト")
    class AuthorizationTests {

        @Test
        @WithAnonymousUser
        @DisplayName("未認証ユーザーがレポートにアクセスすると401")
        void anonymous_returns401() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("VIEWERがレポートにアクセスすると500（未実装UnsupportedOperationException）")
        void viewer_canAccess() throws Exception {
            // Phase 1 では UnsupportedOperationException を投げるため 500
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "WAREHOUSE_STAFF")
        @DisplayName("WAREHOUSE_STAFFがレポートにアクセスすると500（未実装）")
        void staff_canAccess() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "WAREHOUSE_MANAGER")
        @DisplayName("WAREHOUSE_MANAGERがレポートにアクセスすると500（未実装）")
        void manager_canAccess() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "SYSTEM_ADMIN")
        @DisplayName("SYSTEM_ADMINがレポートにアクセスすると500（未実装）")
        void admin_canAccess() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("全エンドポイントの未実装テスト")
    class NotImplementedTests {

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-01: 入荷検品レポート — 未実装で500")
        void rpt01_inboundInspection() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inbound-inspection")
                            .param("slipId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-03: 入荷予定レポート — 未実装で500")
        void rpt03_inboundPlan() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inbound-plan")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-04: 入庫実績レポート — 未実装で500")
        void rpt04_inboundResult() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inbound-result")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-05: 未入荷リスト（リアルタイム） — 未実装で500")
        void rpt05_unreceivedRealtime() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/unreceived-realtime")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-06: 未入荷リスト（確定） — 未実装で500")
        void rpt06_unreceivedConfirmed() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/unreceived-confirmed")
                            .param("warehouseId", "1")
                            .param("batchBusinessDate", "2026-03-27")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-07: 在庫一覧レポート — 未実装で500")
        void rpt07_inventory() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-08: 在庫推移レポート — 未実装で500")
        void rpt08_inventoryTransition() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory-transition")
                            .param("warehouseId", "1")
                            .param("productId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-09: 在庫訂正一覧 — 未実装で500")
        void rpt09_inventoryCorrection() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory-correction")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-10: 棚卸リスト — 未実装で500")
        void rpt10_stocktakeList() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/stocktake-list")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-11: 棚卸結果レポート — 未実装で500")
        void rpt11_stocktakeResult() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/stocktake-result")
                            .param("stocktakeId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-12: ピッキング指示書 — 未実装で500")
        void rpt12_pickingInstruction() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/picking-instruction")
                            .param("pickingInstructionId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-13: 出荷検品レポート — 未実装で500")
        void rpt13_shippingInspection() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/shipping-inspection")
                            .param("slipId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-14: 配送リスト — 未実装で500")
        void rpt14_deliveryList() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/delivery-list")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-15: 未出荷リスト（リアルタイム） — 未実装で500")
        void rpt15_unshippedRealtime() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/unshipped-realtime")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-16: 未出荷リスト（確定） — 未実装で500")
        void rpt16_unshippedConfirmed() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/unshipped-confirmed")
                            .param("warehouseId", "1")
                            .param("batchBusinessDate", "2026-03-27")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-17: 日次集計レポート — 未実装で500")
        void rpt17_dailySummary() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/daily-summary")
                            .param("targetBusinessDate", "2026-03-27")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-18: 返品レポート — 未実装で500")
        void rpt18_returns() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/returns")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isInternalServerError());
        }
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
