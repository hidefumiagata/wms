package com.wms.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.shared.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private JwtAuthenticationEntryPoint entryPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("認証エラー時に401とJSON ErrorResponseを返す")
    void commence_returns401WithJsonErrorResponse() throws Exception {
        // Act
        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        String body = response.getContentAsString();
        // Parse with JavaType to handle the record
        ErrorResponse errorResponse = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(errorResponse.code()).isEqualTo("UNAUTHORIZED");
        assertThat(errorResponse.message()).isEqualTo("認証が必要です");
        assertThat(errorResponse.traceId()).isNotNull();
        assertThat(errorResponse.timestamp()).isNotNull();
    }
}
