package com.wms.report.controller;

import com.wms.report.service.DeliveryListReportService;
import com.wms.report.service.InventoryCorrectionReportService;
import com.wms.report.service.InventoryReportService;
import com.wms.report.service.InventoryTransitionReportService;
import com.wms.report.service.InboundInspectionReportService;
import com.wms.report.service.InboundPlanReportService;
import com.wms.report.service.InboundResultReportService;
import com.wms.report.service.PickingInstructionReportService;
import com.wms.report.service.ShippingInspectionReportService;
import com.wms.report.service.StocktakeListReportService;
import com.wms.report.service.StocktakeResultReportService;
import com.wms.report.service.UnreceivedConfirmedReportService;
import com.wms.report.service.UnreceivedRealtimeReportService;
import com.wms.report.service.UnshippedConfirmedReportService;
import com.wms.report.service.UnshippedRealtimeReportService;
import com.wms.report.service.DailySummaryReportService;
import com.wms.report.service.ReturnsReportService;
import com.wms.shared.config.OpenApiEnumConverterConfig;
import com.wms.shared.exception.GlobalExceptionHandler;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({ReportControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class, OpenApiEnumConverterConfig.class})
@DisplayName("ReportController テスト")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundInspectionReportService inboundInspectionReportService;

    @MockitoBean
    private InboundPlanReportService inboundPlanReportService;

    @MockitoBean
    private InboundResultReportService inboundResultReportService;

    @MockitoBean
    private UnreceivedRealtimeReportService unreceivedRealtimeReportService;

    @MockitoBean
    private UnreceivedConfirmedReportService unreceivedConfirmedReportService;

    @MockitoBean
    private InventoryReportService inventoryReportService;

    @MockitoBean
    private InventoryTransitionReportService inventoryTransitionReportService;

    @MockitoBean
    private InventoryCorrectionReportService inventoryCorrectionReportService;

    @MockitoBean
    private StocktakeListReportService stocktakeListReportService;

    @MockitoBean
    private StocktakeResultReportService stocktakeResultReportService;

    @MockitoBean
    private PickingInstructionReportService pickingInstructionReportService;

    @MockitoBean
    private ShippingInspectionReportService shippingInspectionReportService;

    @MockitoBean
    private DeliveryListReportService deliveryListReportService;

    @MockitoBean
    private UnshippedRealtimeReportService unshippedRealtimeReportService;

    @MockitoBean
    private UnshippedConfirmedReportService unshippedConfirmedReportService;

    @MockitoBean
    private DailySummaryReportService dailySummaryReportService;

    @MockitoBean
    private ReturnsReportService returnsReportService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String REPORTS_BASE = "/api/v1/reports";

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpMocks() {
        ResponseEntity<?> ok = ResponseEntity.ok(List.of());
        lenient().when(inboundInspectionReportService.generate(any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(inboundPlanReportService.generate(any(), any(), any(), any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(inboundResultReportService.generate(any(), any(), any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(unreceivedRealtimeReportService.generate(any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(unreceivedConfirmedReportService.generate(any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(inventoryReportService.generate(any(), any(), any(), any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(inventoryTransitionReportService.generate(any(), any(), any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(inventoryCorrectionReportService.generate(any(), any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(stocktakeListReportService.generate(any(), any(), any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(stocktakeResultReportService.generate(any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(pickingInstructionReportService.generate(any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(shippingInspectionReportService.generate(any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(deliveryListReportService.generate(any(), any(), any(), any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(unshippedRealtimeReportService.generate(any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(unshippedConfirmedReportService.generate(any(), any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(dailySummaryReportService.generate(any(), any())).thenReturn((ResponseEntity) ok);
        lenient().when(returnsReportService.generate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn((ResponseEntity) ok);
    }

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
        @DisplayName("VIEWERがレポートにアクセスすると200")
        void viewer_canAccess() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "WAREHOUSE_STAFF")
        @DisplayName("WAREHOUSE_STAFFがレポートにアクセスすると200")
        void staff_canAccess() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "WAREHOUSE_MANAGER")
        @DisplayName("WAREHOUSE_MANAGERがレポートにアクセスすると200")
        void manager_canAccess() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "SYSTEM_ADMIN")
        @DisplayName("SYSTEM_ADMINがレポートにアクセスすると200")
        void admin_canAccess() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("全エンドポイント正常呼び出しテスト")
    class EndpointTests {

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-01: 入荷検品レポート — 200")
        void rpt01_inboundInspection() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inbound-inspection")
                            .param("slipId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-03: 入荷予定レポート — 200")
        void rpt03_inboundPlan() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inbound-plan")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-04: 入庫実績レポート — 200")
        void rpt04_inboundResult() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inbound-result")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-05: 未入荷リスト（リアルタイム） — 200")
        void rpt05_unreceivedRealtime() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/unreceived-realtime")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-06: 未入荷リスト（確定） — 200")
        void rpt06_unreceivedConfirmed() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/unreceived-confirmed")
                            .param("warehouseId", "1")
                            .param("batchBusinessDate", "2026-03-27")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-07: 在庫一覧レポート — 200")
        void rpt07_inventory() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-08: 在庫推移レポート — 200")
        void rpt08_inventoryTransition() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory-transition")
                            .param("warehouseId", "1")
                            .param("productId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-09: 在庫訂正一覧 — 200")
        void rpt09_inventoryCorrection() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/inventory-correction")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-10: 棚卸リスト — 200")
        void rpt10_stocktakeList() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/stocktake-list")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-11: 棚卸結果レポート — 200")
        void rpt11_stocktakeResult() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/stocktake-result")
                            .param("stocktakeId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-12: ピッキング指示書 — 200")
        void rpt12_pickingInstruction() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/picking-instruction")
                            .param("pickingInstructionId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-13: 出荷検品レポート — 200")
        void rpt13_shippingInspection() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/shipping-inspection")
                            .param("slipId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-14: 配送リスト — 200")
        void rpt14_deliveryList() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/delivery-list")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-15: 未出荷リスト（リアルタイム） — 200")
        void rpt15_unshippedRealtime() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/unshipped-realtime")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-16: 未出荷リスト（確定） — 200")
        void rpt16_unshippedConfirmed() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/unshipped-confirmed")
                            .param("warehouseId", "1")
                            .param("batchBusinessDate", "2026-03-27")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-17: 日次集計レポート — 200")
        void rpt17_dailySummary() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/daily-summary")
                            .param("targetBusinessDate", "2026-03-27")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("RPT-18: 返品レポート — 200")
        void rpt18_returns() throws Exception {
            mockMvc.perform(get(REPORTS_BASE + "/returns")
                            .param("warehouseId", "1")
                            .header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isOk());
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
