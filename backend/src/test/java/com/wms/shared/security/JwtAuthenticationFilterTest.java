package com.wms.shared.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("有効なトークン → SecurityContextにAuthentication設定、MDCにuserId設定")
    void validToken_setsSecurityContextAndMdcUserId() throws Exception {
        // Arrange
        String token = "valid.jwt.token";
        request.setCookies(new Cookie("access_token", token));

        Claims claims = mock(Claims.class);
        when(claims.get("userCode", String.class)).thenReturn("USER001");
        when(claims.get("role", String.class)).thenReturn("ADMIN");

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(42L);

        // Capture SecurityContext and MDC value during filter chain execution
        final String[] capturedMdcUserId = {null};
        final var capturedAuth = new Object[1];
        doAnswer(invocation -> {
            capturedMdcUserId[0] = MDC.get("userId");
            capturedAuth[0] = SecurityContextHolder.getContext().getAuthentication();
            return null;
        }).when(filterChain).doFilter(request, response);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert - SecurityContext was set DURING filter chain execution
        assertThat(capturedAuth[0]).isNotNull();
        assertThat(capturedAuth[0]).isInstanceOf(UsernamePasswordAuthenticationToken.class);

        var authentication = (UsernamePasswordAuthenticationToken) capturedAuth[0];
        assertThat(authentication.getPrincipal()).isInstanceOf(WmsUserDetails.class);

        WmsUserDetails userDetails = (WmsUserDetails) authentication.getPrincipal();
        assertThat(userDetails.getUserId()).isEqualTo(42L);
        assertThat(userDetails.getUsername()).isEqualTo("USER001");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");

        // SecurityContext is cleared AFTER filter chain completes
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // MDC userId was set during filter chain
        assertThat(capturedMdcUserId[0]).isEqualTo("42");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Cookieなし → SecurityContext未設定のまま通過")
    void noCookie_noSecurityContextSet() throws Exception {
        // Arrange - no cookies set

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("無効なトークン → SecurityContext未設定のまま通過")
    void invalidToken_noSecurityContextSet() throws Exception {
        // Arrange
        String token = "invalid.jwt.token";
        request.setCookies(new Cookie("access_token", token));

        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).parseToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("access_token以外のCookieのみ → SecurityContext未設定のまま通過")
    void otherCookieOnly_noSecurityContextSet() throws Exception {
        request.setCookies(new Cookie("other_cookie", "somevalue"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("フィルタチェーン実行後にMDC userIdがクリーンアップされる")
    void mdcUserId_cleanedUpAfterFilterChain() throws Exception {
        // Arrange
        String token = "valid.jwt.token";
        request.setCookies(new Cookie("access_token", token));

        Claims claims = mock(Claims.class);
        when(claims.get("userCode", String.class)).thenReturn("USER002");
        when(claims.get("role", String.class)).thenReturn("OPERATOR");

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(99L);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert - MDC userId should be removed after filter completes
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    @DisplayName("フィルタチェーンが例外を投げてもMDC userIdとSecurityContextがクリーンアップされる")
    void mdcAndSecurityContext_cleanedUpEvenWhenFilterChainThrows() throws Exception {
        // Arrange
        String token = "valid.jwt.token";
        request.setCookies(new Cookie("access_token", token));

        Claims claims = mock(Claims.class);
        when(claims.get("userCode", String.class)).thenReturn("USER002");
        when(claims.get("role", String.class)).thenReturn("OPERATOR");

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        when(jwtTokenProvider.getUserIdFromClaims(claims)).thenReturn(99L);
        doThrow(new RuntimeException("downstream error")).when(filterChain).doFilter(request, response);

        // Act & Assert
        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get("userId")).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
