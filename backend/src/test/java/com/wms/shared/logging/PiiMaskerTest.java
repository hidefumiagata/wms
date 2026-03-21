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
    @DisplayName("空文字列はそのまま返す")
    void mask_emptyString_returnsEmpty() {
        assertThat(PiiMasker.mask("")).isEqualTo("");
    }

    // --- メールアドレスマスキング ---

    @Test
    @DisplayName("メールアドレスがマスキングされる")
    void mask_email_isMasked() {
        String input = "User email is user@example.com and admin@test.co.jp";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("user@example.com");
        assertThat(result).doesNotContain("admin@test.co.jp");
        assertThat(result).contains("***@***.***");
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

    @Test
    @DisplayName("ReDoS対策: 長い@以降文字列でも高速に処理される")
    void mask_longEmailDomain_noReDoS() {
        // possessive quantifierにより、バックトラッキングが発生しない
        // ウォームアップ（JIT最適化・クラスロード）
        for (int i = 0; i < 10; i++) {
            PiiMasker.mask("warmup@example.com");
        }

        String input = "user@" + "a".repeat(1000);
        long start = System.nanoTime();
        PiiMasker.mask(input);
        long elapsed = System.nanoTime() - start;
        // 1秒以内に完了すること
        // ReDoS脆弱な場合、1000文字でも数秒以上かかる
        assertThat(elapsed).isLessThan(1_000_000_000L);
    }

    // --- 電話番号マスキング ---

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

    @ParameterizedTest
    @DisplayName("電話番号形式以外はマスキングされない（偽陽性防止）")
    @ValueSource(strings = {"0312345678", "00012345", "0001-2345", "item-1-processed", "warehouse-2-zone"})
    void mask_nonPhoneFormats_notMasked(String input) {
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

    @Test
    @DisplayName("JWEトークン（5パート）がマスキングされる")
    void mask_jwe_isMasked() {
        String jwe = "eyJhbGciOiJSU0EtT0FFUA.dGVzdA.iv123.cipher456.tag789";
        String result = PiiMasker.mask(jwe);
        assertThat(result).doesNotContain("eyJ");
        assertThat(result).contains("[JWT-REDACTED]");
    }

    // --- パスワードKVマスキング ---

    @ParameterizedTest
    @DisplayName("各種パスワードキーバリューがマスキングされる（セパレーター保持）")
    @CsvSource({
        "'password=secret123', 'password=*****'",
        "'Password=MyPass!', 'Password=*****'",
        "'pwd=abc', 'pwd=*****'",
        "'secret=token123', 'secret=*****'",
        "'passwd:hidden', 'passwd:*****'",
        "'token=abc123def', 'token=*****'"
    })
    void mask_passwordKeyValue_isMasked(String input, String expected) {
        String result = PiiMasker.mask(input);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("JSON形式のパスワードKVがマスキングされる")
    void mask_jsonPasswordKV_isMasked() {
        String input = "{\"password\":\"mySecret123\"}";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("mySecret123");
        assertThat(result).contains("password\":\"*****\"");
    }

    @Test
    @DisplayName("JSON形式のスペース入りパスワードがマスキングされる")
    void mask_jsonPasswordWithSpace_isMasked() {
        String input = "{\"password\": \"my secret value\"}";
        String result = PiiMasker.mask(input);
        assertThat(result).doesNotContain("my secret value");
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
    @DisplayName("fast-path: 0始まり数字-ハイフンを含むメッセージはチェック対象")
    void mayContainSensitiveData_withPhoneLike_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("Phone: 03-1234-5678")).isTrue();
    }

    @Test
    @DisplayName("fast-path: ハイフンあり0なしのメッセージはチェック不要")
    void mayContainSensitiveData_hyphenWithoutZero_returnsFalse() {
        assertThat(PiiMasker.mayContainSensitiveData("item-1-processed")).isFalse();
    }

    @Test
    @DisplayName("fast-path: 0x-パターン（03-等）はチェック対象")
    void mayContainSensitiveData_withZeroDigitHyphen_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("call 03-1234")).isTrue();
    }

    @Test
    @DisplayName("fast-path: 先頭の0-パターンはチェック対象")
    void mayContainSensitiveData_withZeroHyphen_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("code 0-1234-5678")).isTrue();
    }

    @Test
    @DisplayName("fast-path: 短い文字列でハイフンがindex1にあるケース")
    void mayContainSensitiveData_hyphenAtIndex1_returnsFalse() {
        // i=1で'-'だがi-1='a'（0でない）→ 2番目のifでi>=2がfalse
        assertThat(PiiMasker.mayContainSensitiveData("a-b")).isFalse();
    }

    @Test
    @DisplayName("fast-path: 非0始まりの数字-ハイフンはチェック不要")
    void mayContainSensitiveData_nonZeroDigitHyphen_returnsFalse() {
        // i>=2でi-2が'0'でない場合
        assertThat(PiiMasker.mayContainSensitiveData("ab12-34")).isFalse();
    }

    @Test
    @DisplayName("fast-path: 0の後に非数字がありハイフンがあるケース")
    void mayContainSensitiveData_zeroThenNonDigitHyphen_returnsFalse() {
        // i-2=='0'だがi-1が非数字の場合
        assertThat(PiiMasker.mayContainSensitiveData("x0a-bc")).isFalse();
    }

    @Test
    @DisplayName("fast-path: eyJを含むメッセージはチェック対象")
    void mayContainSensitiveData_withEyJ_returnsTrue() {
        assertThat(PiiMasker.mayContainSensitiveData("Token: eyJhbGci...")).isTrue();
    }

    @ParameterizedTest
    @DisplayName("fast-path: パスワード系キーワードを含むメッセージはチェック対象")
    @ValueSource(strings = {"password=secret", "passwd:hidden", "pwd=abc", "secret=token", "token=abc123"})
    void mayContainSensitiveData_withPasswordKeywords_returnsTrue(String input) {
        assertThat(PiiMasker.mayContainSensitiveData(input)).isTrue();
    }

    @Test
    @DisplayName("fast-path: 空文字列はチェック不要")
    void mayContainSensitiveData_emptyString_returnsFalse() {
        assertThat(PiiMasker.mayContainSensitiveData("")).isFalse();
    }

    @Test
    @DisplayName("fast-path: ハイフンのみのメッセージはチェック不要")
    void mayContainSensitiveData_hyphenOnly_returnsFalse() {
        assertThat(PiiMasker.mayContainSensitiveData("hello-world")).isFalse();
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
