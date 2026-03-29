package com.wms.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "springdoc.swagger-ui.enabled=false")
@DisplayName("SecurityConfig: Swagger無効化時のアクセス制御")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("swagger-ui.enabled=falseの場合、Swagger UIへのアクセスが拒否される")
    void securityFilterChain_swaggerDisabled_requiresAuthentication() throws Exception {
        // コンテキストキャッシュ状態により 401 または 403 が返る（いずれもアクセス拒否）
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("swagger-ui.enabled=falseの場合、api-docsへのアクセスが拒否される")
    void securityFilterChain_apiDocsDisabled_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }
}
