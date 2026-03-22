package com.wms.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prd")
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "cookieAuth";

    @Bean
    public OpenAPI wmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WMS ShowCase API")
                        .description("倉庫管理システム（WMS）ShowCase のREST API")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("WMS ShowCase Project")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name("access_token")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .description("JWTアクセストークン（httpOnly Cookie）。"
                                                + "Swagger UIでは /api/v1/auth/login でログイン後、"
                                                + "ブラウザにCookieが設定されるため自動的に認証されます。")));
    }
}
