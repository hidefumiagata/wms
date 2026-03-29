package com.wms.system.controller;

import com.wms.shared.security.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "server.forward-headers-strategy=native")
@DisplayName("AuthController: RemoteIpValve結合テスト")
class AuthControllerRemoteIpIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @Test
    @DisplayName("X-Forwarded-ForヘッダーのクライアントIPがレート制限キーに使われる")
    void login_withXForwardedFor_usesClientIpForRateLimit() {
        // RemoteIpValve が X-Forwarded-For を解決し、remoteAddr にクライアントIPが設定される
        // 127.0.0.1（ローカル接続）はデフォルトの internal-proxies に含まれるため信頼プロキシ扱い
        when(rateLimiterService.tryConsumeLogin(eq("203.0.113.50"))).thenReturn(false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "203.0.113.50");
        headers.set("X-Requested-With", "XMLHttpRequest");

        String body = """
                {"userCode": "USR001", "password": "test"}
                """;

        var response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(body, headers),
                String.class);

        // レート制限で429が返ること（=コントローラに到達してremoteAddrが203.0.113.50で解決された）
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(429);
        verify(rateLimiterService).tryConsumeLogin("203.0.113.50");
    }

    @Test
    @DisplayName("X-Forwarded-Forがない場合は直接接続のIPがレート制限キーに使われる")
    void login_withoutXForwardedFor_usesDirectConnectionIp() {
        when(rateLimiterService.tryConsumeLogin(eq("127.0.0.1"))).thenReturn(false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Requested-With", "XMLHttpRequest");

        String body = """
                {"userCode": "USR001", "password": "test"}
                """;

        var response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(body, headers),
                String.class);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(429);
        verify(rateLimiterService).tryConsumeLogin("127.0.0.1");
    }
}
