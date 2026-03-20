package com.wms.shared.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    void mask_null_returnsNull() {
        assertThat(PiiMasker.mask(null)).isNull();
    }

    @Test
    void mask_noSensitiveData_returnsUnchanged() {
        String input = "Normal log message without PII";
        assertThat(PiiMasker.mask(input)).isEqualTo(input);
    }

    @Test
    void mask_email_isMasked() {
        String input = "User email is user@example.com and admin@test.co.jp";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("user@example.com");
        assertThat(result).doesNotContain("admin@test.co.jp");
        assertThat(result).contains("***@***.***");
    }

    @Test
    void mask_phoneWithHyphens_isMasked() {
        String input = "Phone: 03-1234-5678";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("03-1234-5678");
        assertThat(result).contains("***-****-****");
    }

    @Test
    void mask_mobilePhone_isMasked() {
        String input = "Mobile: 090-1234-5678";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("090-1234-5678");
        assertThat(result).contains("***-****-****");
    }

    @Test
    void mask_phoneWithoutHyphens_isMasked() {
        String input = "Tel: 0312345678";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("0312345678");
    }

    @Test
    void mask_mixedContent_allMasked() {
        String input = "User test@example.com called from 03-1234-5678";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("test@example.com");
        assertThat(result).doesNotContain("03-1234-5678");
    }

    @Test
    void mask_emptyString_returnsEmpty() {
        assertThat(PiiMasker.mask("")).isEqualTo("");
    }

    @ParameterizedTest
    @CsvSource({
        "user.name+tag@domain.co.jp, ***@***.***",
        "simple@test.com, ***@***.***"
    })
    void mask_variousEmailFormats(String email, String expected) {
        String result = PiiMasker.mask(email);
        assertThat(result).isEqualTo(expected);
    }
}
