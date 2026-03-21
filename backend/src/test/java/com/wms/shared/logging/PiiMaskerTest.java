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

    // --- JWTトークンマスキング ---

    @Test
    @DisplayName("JWTトークンがマスキングされる")
    void mask_jwt_isMasked() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.abc123def456";
        String input = "Authorization: Bearer " + jwt;
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("eyJ");
        assertThat(result).contains("[JWT-REDACTED]");
    }

    @Test
    @DisplayName("JWT風だが不完全なトークンはマスキングされない")
    void mask_incompleteJwt_notMasked() {
        String input = "eyJhbGciOi incomplete token";
        String result = PiiMasker.mask(input);
        assertThat(result).isEqualTo(input);
    }

    // --- パスワードKVマスキング ---

    @ParameterizedTest
    @DisplayName("各種パスワードキーバリューがマスキングされる")
    @CsvSource({
        "'password=secret123', 'password=*****'",
        "'Password=MyPass!', 'Password=*****'",
        "'pwd=abc', 'pwd=*****'",
        "'secret=token123', 'secret=*****'",
        "'passwd:hidden', 'passwd=*****'"
    })
    void mask_passwordKeyValue_isMasked(String input, String expected) {
        String result = PiiMasker.mask(input);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("パスワードキーワードが値なしの場合はマスキングされない")
    void mask_passwordKeywordAlone_notMasked() {
        String input = "Changed password policy";
        String result = PiiMasker.mask(input);
        assertThat(result).isEqualTo(input);
    }

    // --- fast-path最適化テスト ---

    @Test
    @DisplayName("fast-path: PII未含有メッセージは正規表現を実行せずにそのまま返す")
    void mayContainSensitiveData_plainMessage_returnsFalse() {
        assertThat(PiiMasker.mayContainSensitiveData("Normal log message")).isFalse();
    }

    @Test
    @DisplayName("fast-path: @を含むメッセージはチェック対象")
    void mayContainSensitiveData_withAt_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("user@example.com")).isTrue();
    }

    @Test
    @DisplayName("fast-path: 数字-ハイフンを含むメッセージはチェック対象")
    void mayContainSensitiveData_withDigitHyphen_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("Phone: 03-1234-5678")).isTrue();
    }

    @Test
    @DisplayName("fast-path: eyJを含むメッセージはチェック対象")
    void mayContainSensitiveData_withEyJ_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("Token: eyJhbGci...")).isTrue();
    }

    @Test
    @DisplayName("fast-path: passwordキーワードを含むメッセージはチェック対象")
    void mayContainSensitiveData_withPassword_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("password=secret")).isTrue();
    }

    @Test
    @DisplayName("fast-path: passwdキーワードを含むメッセージはチェック対象")
    void mayContainSensitiveData_withPasswd_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("passwd:hidden")).isTrue();
    }

    @Test
    @DisplayName("fast-path: pwdキーワードを含むメッセージはチェック対象")
    void mayContainSensitiveData_withPwd_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("pwd=abc")).isTrue();
    }

    @Test
    @DisplayName("fast-path: secretキーワードを含むメッセージはチェック対象")
    void mayContainSensitiveData_withSecret_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("secret=token")).isTrue();
    }

    @Test
    @DisplayName("fast-path: 空文字列はチェック不要")
    void mayContainSensitiveData_emptyString_returnsFalse() {
        assertThat(PiiMasker.mayContainSensitiveData("")).isFalse();
    }

    // --- 複合パターンテスト ---

    @Test
    @DisplayName("全PIIパターンが混在するメッセージで全てマスキングされる")
    void mask_allPatterns_allMasked() {
        String input = "User user@test.com called 03-1234-5678 with token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.abc123 and password=secret";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("user@test.com");
        assertThat(result).doesNotContain("03-1234-5678");
        assertThat(result).doesNotContain("eyJ");
        assertThat(result).doesNotContain("password=secret");
        assertThat(result).contains("***@***.***");
        assertThat(result).contains("***-****-****");
        assertThat(result).contains("[JWT-REDACTED]");
        assertThat(result).contains("password=*****");
    }
}
