package com.wms.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenApiConfig: Springdoc OpenAPI メタデータ設定")
class OpenApiConfigTest {

    private OpenAPI openAPI;

    @BeforeEach
    void setUp() {
        openAPI = new OpenApiConfig().wmsOpenAPI();
    }

    @Test
    @DisplayName("タイトル・説明・バージョン・連絡先が正しく設定される")
    void wmsOpenAPI_default_hasCorrectInfo() {
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("WMS ShowCase API");
        assertThat(openAPI.getInfo().getDescription()).contains("倉庫管理システム");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("0.1.0");
        assertThat(openAPI.getInfo().getContact().getName()).isEqualTo("WMS ShowCase Project");
    }

    @Test
    @DisplayName("Bearer JWT セキュリティスキームが定義される")
    void wmsOpenAPI_default_hasSecurityScheme() {
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey("bearerAuth");
        var scheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(scheme.getType().toString()).isEqualToIgnoringCase("HTTP");
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    @DisplayName("グローバルセキュリティ要件は設定されない（エンドポイント個別で制御）")
    void wmsOpenAPI_default_hasNoGlobalSecurityRequirement() {
        assertThat(openAPI.getSecurity()).isNull();
    }
}
