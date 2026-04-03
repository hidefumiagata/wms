package com.wms.shared.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
public abstract class IntegrationTestBase {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wms_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Requested-With", "XMLHttpRequest");
        return headers;
    }

    /**
     * Login and return headers containing cookies for authenticated requests.
     */
    protected HttpHeaders loginAndGetHeaders(String userCode, String password) {
        HttpHeaders headers = createJsonHeaders();
        String body = String.format("{\"userCode\":\"%s\",\"password\":\"%s\"}", userCode, password);
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, request, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Login failed with status " + response.getStatusCode()
                    + ", body=" + response.getBody());
        }

        HttpHeaders authHeaders = createJsonHeaders();
        String cookieHeader = buildCookieHeader(response);
        if (cookieHeader != null) {
            authHeaders.set("Cookie", cookieHeader);
        }
        return authHeaders;
    }

    /**
     * Login and return the raw response (for inspecting cookies, body, etc.).
     */
    protected ResponseEntity<String> loginRaw(String userCode, String password) {
        HttpHeaders headers = createJsonHeaders();
        String body = String.format("{\"userCode\":\"%s\",\"password\":\"%s\"}", userCode, password);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, request, String.class);
    }

    protected String extractCookie(ResponseEntity<?> response, String cookieName) {
        List<String> cookies = response.getHeaders().get("Set-Cookie");
        if (cookies != null) {
            for (String setCookie : cookies) {
                if (setCookie.startsWith(cookieName + "=")) {
                    return setCookie.split(";")[0].substring(cookieName.length() + 1);
                }
            }
        }
        return null;
    }

    protected String buildCookieHeader(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get("Set-Cookie");
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        StringBuilder cookieHeader = new StringBuilder();
        for (String setCookie : cookies) {
            String nameValue = setCookie.split(";")[0].trim();
            if (!cookieHeader.isEmpty()) {
                cookieHeader.append("; ");
            }
            cookieHeader.append(nameValue);
        }
        return cookieHeader.toString();
    }

    protected ResponseEntity<String> postJson(String url, String body, HttpHeaders headers) {
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }

    protected ResponseEntity<String> postNoBody(String url, HttpHeaders headers) {
        HttpEntity<String> request = new HttpEntity<>(null, headers);
        return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }

    protected JsonNode parseJson(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body);
    }
}
