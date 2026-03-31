package com.wms.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.swagger-ui.enabled=false",
        "springdoc.api-docs.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("SecurityConfig: Swagger無効化時のアクセス制御")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("swagger-ui.enabled=falseの場合、Swagger UIへのアクセスが認証必須になる")
    void securityFilterChain_swaggerDisabled_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("swagger-ui.enabled=falseの場合、api-docsへのアクセスが拒否される")
    void securityFilterChain_apiDocsDisabled_notAccessible() throws Exception {
        int status = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getStatus();
        // 401(認証拒否) or 500(エンドポイント未登録) のいずれかで、200(公開)でないことを確認
        assertThat(status).isNotEqualTo(200);
    }
}
