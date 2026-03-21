package com.wms.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDateProviderTest {

    private final BusinessDateProvider provider = new BusinessDateProvider();

    @Test
    @DisplayName("today(): JST基準の現在日付が返される")
    void today_default_returnsCurrentDateInJst() {
        LocalDate result = provider.today();

        LocalDate expectedJst = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        assertThat(result).isEqualTo(expectedJst);
    }

    @Test
    @DisplayName("today(): nullではない")
    void today_default_returnsNonNull() {
        assertThat(provider.today()).isNotNull();
    }
}
