package com.wms.shared.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMasker: PII（個人情報）マスキングユーティリティ")
class PiiMaskerTest {

    @Test
    @DisplayName("null入力はnullを返す")
    void mask_null_returnsNull() {
        assertThat(PiiMasker.mask(null)).isNull();
    }

    @Test
    @DisplayName("PII未含有のメッセージはそのまま返す")
    void mask_noSensitiveData_returnsUnchanged() {
        String input = "Normal log message without PII";
        assertThat(PiiMasker.mask(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("メールアドレスがマスキングされる")
    void mask_email_isMasked() {
        String input = "User email is user@example.com and admin@test.co.jp";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("user@example.com");
        assertThat(result).doesNotContain("admin@test.co.jp");
        assertThat(result).contains("***@***.***");
    }

    @Test
    @DisplayName("固定電話（ハイフン付き）がマスキングされる")
    void mask_phoneWithHyphens_isMasked() {
        String input = "Phone: 03-1234-5678";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("03-1234-5678");
        assertThat(result).contains("***-****-****");
    }

    @Test
    @DisplayName("携帯電話がマスキングされる")
    void mask_mobilePhone_isMasked() {
        String input = "Mobile: 090-1234-5678";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("090-1234-5678");
        assertThat(result).contains("***-****-****");
    }

    @Test
    @DisplayName("メールと電話の混在でも全てマスキングされる")
    void mask_mixedContent_allMasked() {
        String input = "User test@example.com called from 03-1234-5678";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("test@example.com");
        assertThat(result).doesNotContain("03-1234-5678");
    }

    @Test
    @DisplayName("空文字列はそのまま返す")
    void mask_emptyString_returnsEmpty() {
        assertThat(PiiMasker.mask("")).isEqualTo("");
    }

    @ParameterizedTest
    @DisplayName("各種メールアドレスフォーマットがマスキングされる")
    @CsvSource({
        "user.name+tag@domain.co.jp, ***@***.***",
        "simple@test.com, ***@***.***"
    })
    void mask_variousEmailFormats_areMasked(String email, String expected) {
        String result = PiiMasker.mask(email);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("ハイフンなしの数字列は電話番号としてマスキングされない（偽陽性防止）")
    @ValueSource(strings = {"0312345678", "00012345", "0001-2345"})
    void mask_numericStringsWithoutFullFormat_notMasked(String input) {
        // ハイフン区切りの完全形式のみマスキング対象
        // "0001-2345" はセグメントが2つしかないのでマスキングされない
        String result = PiiMasker.mask(input);
        assertThat(result).isEqualTo(input);
    }
}
