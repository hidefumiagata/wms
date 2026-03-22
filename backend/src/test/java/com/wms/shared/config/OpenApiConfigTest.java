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
    @DisplayName("Cookie認証セキュリティスキームが定義される")
    void wmsOpenAPI_default_hasSecurityScheme() {
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey("cookieAuth");
        var scheme = openAPI.getComponents().getSecuritySchemes().get("cookieAuth");
        assertThat(scheme.getType().toString()).isEqualToIgnoringCase("APIKEY");
        assertThat(scheme.getIn().toString()).isEqualToIgnoringCase("COOKIE");
        assertThat(scheme.getName()).isEqualTo("access_token");
    }

    @Test
    @DisplayName("グローバルセキュリティ要件は設定されない（エンドポイント個別で制御）")
    void wmsOpenAPI_default_hasNoGlobalSecurityRequirement() {
        assertThat(openAPI.getSecurity()).isNull();
    }
}
