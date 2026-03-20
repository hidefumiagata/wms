package com.wms.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    @Test
    void tryConsumeLogin_withinLimit_returnsTrue() {
        for (int i = 0; i < 20; i++) {
            assertThat(rateLimiterService.tryConsumeLogin("192.168.1.1")).isTrue();
        }
    }

    @Test
    void tryConsumeLogin_exceedsLimit_returnsFalse() {
        for (int i = 0; i < 20; i++) {
            rateLimiterService.tryConsumeLogin("10.0.0.1");
        }
        assertThat(rateLimiterService.tryConsumeLogin("10.0.0.1")).isFalse();
    }

    @Test
    void tryConsumeLogin_differentIps_independent() {
        for (int i = 0; i < 20; i++) {
            rateLimiterService.tryConsumeLogin("10.0.0.2");
        }
        // 別IPはまだ使える
        assertThat(rateLimiterService.tryConsumeLogin("10.0.0.3")).isTrue();
    }

    @Test
    void tryConsumePasswordResetByIp_withinLimit_returnsTrue() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiterService.tryConsumePasswordResetByIp("192.168.1.1")).isTrue();
        }
    }

    @Test
    void tryConsumePasswordResetByIp_exceedsLimit_returnsFalse() {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.tryConsumePasswordResetByIp("10.0.0.10");
        }
        assertThat(rateLimiterService.tryConsumePasswordResetByIp("10.0.0.10")).isFalse();
    }

    @Test
    void tryConsumePasswordResetByIdentifier_withinLimit_returnsTrue() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiterService.tryConsumePasswordResetByIdentifier("user@example.com")).isTrue();
        }
    }

    @Test
    void tryConsumePasswordResetByIdentifier_exceedsLimit_returnsFalse() {
        for (int i = 0; i < 3; i++) {
            rateLimiterService.tryConsumePasswordResetByIdentifier("admin001");
        }
        assertThat(rateLimiterService.tryConsumePasswordResetByIdentifier("admin001")).isFalse();
    }

    @Test
    void tryConsumePasswordResetByIdentifier_differentIdentifiers_independent() {
        for (int i = 0; i < 3; i++) {
            rateLimiterService.tryConsumePasswordResetByIdentifier("user1");
        }
        assertThat(rateLimiterService.tryConsumePasswordResetByIdentifier("user2")).isTrue();
    }
}
