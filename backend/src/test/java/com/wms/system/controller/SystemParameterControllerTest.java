package com.wms.system.controller;

import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.entity.SystemParameter;
import com.wms.system.service.SystemParameterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemParameterController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SystemParameterController")
class SystemParameterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemParameterService systemParameterService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String BASE_URL = "/api/v1/system/parameters";

    private SystemParameter createParam(String key, String value, String category, String valueType) {
        return SystemParameter.builder()
                .id(1L)
                .paramKey(key)
                .paramValue(value)
                .defaultValue(value)
                .displayName(key)
                .category(category)
                .valueType(valueType)
                .displayOrder(0)
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/system/parameters")
    class GetSystemParameters {

        @Test
        @DisplayName("パラメータ一覧を返す")
        void getSystemParameters_returns200() throws Exception {
            SystemParameter p = createParam("SESSION_TIMEOUT_MINUTES", "60", "SECURITY", "INTEGER");
            when(systemParameterService.findAll()).thenReturn(List.of(p));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].paramKey").value("SESSION_TIMEOUT_MINUTES"))
                    .andExpect(jsonPath("$[0].paramValue").value("60"))
                    .andExpect(jsonPath("$[0].category").value("SECURITY"));
        }

        @Test
        @DisplayName("空リストで200を返す")
        void getSystemParameters_empty_returns200() throws Exception {
            when(systemParameterService.findAll()).thenReturn(List.of());

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/system/parameters/{paramKey}")
    class UpdateSystemParameter {

        @Test
        @DisplayName("パラメータ値を更新して200を返す")
        void updateSystemParameter_returns200() throws Exception {
            SystemParameter updated = createParam("SESSION_TIMEOUT_MINUTES", "30", "SECURITY", "INTEGER");
            when(systemParameterService.updateValue(eq("SESSION_TIMEOUT_MINUTES"), eq("30"), any()))
                    .thenReturn(updated);

            mockMvc.perform(put(BASE_URL + "/SESSION_TIMEOUT_MINUTES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paramValue\":\"30\",\"version\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paramKey").value("SESSION_TIMEOUT_MINUTES"))
                    .andExpect(jsonPath("$.paramValue").value("30"));
        }

        @Test
        @DisplayName("存在しないキーで404を返す")
        void updateSystemParameter_notFound_returns404() throws Exception {
            when(systemParameterService.updateValue(eq("UNKNOWN_KEY"), any(), any()))
                    .thenThrow(new com.wms.shared.exception.ResourceNotFoundException(
                            "SYSTEM_PARAMETER_NOT_FOUND", "システムパラメータが見つかりません: UNKNOWN_KEY"));

            mockMvc.perform(put(BASE_URL + "/UNKNOWN_KEY")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paramValue\":\"x\",\"version\":0}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void updateSystemParameter_missingValue_returns400() throws Exception {
            mockMvc.perform(put(BASE_URL + "/SESSION_TIMEOUT_MINUTES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
