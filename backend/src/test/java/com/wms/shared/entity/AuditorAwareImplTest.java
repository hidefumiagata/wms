package com.wms.shared.entity;

import com.wms.shared.security.WmsUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditorAwareImplTest {

    private AuditorAwareImpl auditorAware;

    @BeforeEach
    void setUp() {
        auditorAware = new AuditorAwareImpl();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("WmsUserDetailsが認証済みの場合、userIdを返す")
    void getCurrentAuditor_returnsUserIdWhenAuthenticated() {
        Long expectedUserId = 42L;
        WmsUserDetails userDetails = new WmsUserDetails(
                expectedUserId, "testuser", "password", "WH001",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).isPresent().contains(expectedUserId);
    }

    @Test
    @DisplayName("認証情報がない場合、空のOptionalを返す")
    void getCurrentAuditor_returnsEmptyWhenNotAuthenticated() {
        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("principalがWmsUserDetailsでない場合、空のOptionalを返す")
    void getCurrentAuditor_returnsEmptyWhenPrincipalIsNotWmsUserDetails() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("plainStringPrincipal", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("認証が未認証状態の場合、空のOptionalを返す")
    void getCurrentAuditor_returnsEmptyWhenAuthenticationNotAuthenticated() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user", "pass");
        // unauthenticated token (no authorities) — isAuthenticated() returns false
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).isEmpty();
    }
}
