package com.wms.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenApiConfig: Springdoc OpenAPI メタデータ設定")
class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    @DisplayName("OpenAPIオブジェクトにタイトル・バージョン・連絡先が設定される")
    void wmsOpenAPI_hasCorrectInfo() {
        OpenAPI openAPI = config.wmsOpenAPI();

        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("WMS ShowCase API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("0.1.0");
        assertThat(openAPI.getInfo().getContact().getName()).isEqualTo("WMS ShowCase Project");
    }

    @Test
    @DisplayName("Bearer JWT セキュリティスキームが設定される")
    void wmsOpenAPI_hasSecurityScheme() {
        OpenAPI openAPI = config.wmsOpenAPI();

        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey("bearerAuth");
        var scheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(scheme.getType().toString()).isEqualToIgnoringCase("HTTP");
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    @DisplayName("グローバルセキュリティ要件が設定される")
    void wmsOpenAPI_hasSecurityRequirement() {
        OpenAPI openAPI = config.wmsOpenAPI();

        assertThat(openAPI.getSecurity()).isNotEmpty();
        assertThat(openAPI.getSecurity().get(0).get("bearerAuth")).isNotNull();
    }
}
