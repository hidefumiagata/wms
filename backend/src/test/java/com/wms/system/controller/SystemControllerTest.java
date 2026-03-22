package com.wms.system.controller;

import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.service.SystemParameterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SystemController")
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemParameterService systemParameterService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("GET /api/v1/system/session-config")
    class GetSessionConfig {

        @Test
        @DisplayName("セッション設定を返す（timeoutMinutes=60, warningMinutes=55）")
        void getSessionConfig_returns200() throws Exception {
            when(systemParameterService.getIntValue("SESSION_TIMEOUT_MINUTES")).thenReturn(60);

            mockMvc.perform(get("/api/v1/system/session-config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timeoutMinutes").value(60))
                    .andExpect(jsonPath("$.warningMinutes").value(55));
        }

        @Test
        @DisplayName("タイムアウトが5分以下の場合warningMinutesは0になる")
        void getSessionConfig_smallTimeout_warningIsZero() throws Exception {
            when(systemParameterService.getIntValue("SESSION_TIMEOUT_MINUTES")).thenReturn(3);

            mockMvc.perform(get("/api/v1/system/session-config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timeoutMinutes").value(3))
                    .andExpect(jsonPath("$.warningMinutes").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/system/business-date")
    class GetBusinessDate {

        @Test
        @DisplayName("営業日を返す")
        void getBusinessDate_returns200() throws Exception {
            mockMvc.perform(get("/api/v1/system/business-date"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessDate").exists());
        }
    }
}
