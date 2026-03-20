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
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CustomAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CustomAccessDeniedHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new CustomAccessDeniedHandler(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("アクセス拒否時に403とJSON ErrorResponseを返す")
    void handle_returns403WithJsonErrorResponse() throws Exception {
        // Act
        handler.handle(request, response, new AccessDeniedException("Access denied"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        String body = response.getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(errorResponse.code()).isEqualTo("FORBIDDEN");
        assertThat(errorResponse.message()).isEqualTo("この操作を実行する権限がありません");
        assertThat(errorResponse.traceId()).isNotNull();
        assertThat(errorResponse.timestamp()).isNotNull();
    }
}
