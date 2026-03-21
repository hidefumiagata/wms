package com.wms.shared.logging;

import java.util.regex.Pattern;

/**
 * ログメッセージ中のPII（個人情報）・機密情報をマスクするユーティリティ。
 * LogstashEncoder の MessageJsonProvider カスタマイズで自動適用される。
 */
public final class PiiMasker {

    private PiiMasker() {}

    // S2対策: 内側文字クラスにpossessive quantifier(++)を使用しReDoS防止
    // 外側グループ(*)はバックトラッキング許可（パターン正常動作に必要）
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile(
            "[a-zA-Z0-9._%+\\-]++@[a-zA-Z0-9\\-]++(?:\\.[a-zA-Z0-9\\-]++)*\\.[a-zA-Z]{2,}");

    // M4対策: 先頭セグメント2桁以上に制限、ワードバウンダリ追加
    private static final Pattern PHONE_PATTERN =
        Pattern.compile(
            "\\b0[0-9]{1,3}-[0-9]{1,4}-[0-9]{3,4}\\b");

    // s1対策: JWE(5パート)対応、Base64URLパディング(=)対応
    private static final Pattern JWT_PATTERN =
        Pattern.compile(
            "eyJ[a-zA-Z0-9_=-]+\\.[a-zA-Z0-9_=-]+\\.[a-zA-Z0-9_=-]+(?:\\.[a-zA-Z0-9_=-]+)*");

    // S1対策: tokenキーワード追加、M3対策: セパレーターをグループ化して保持
    // s2対策: JSON形式("key":"value")にも対応
    // (?!\[JWT): JWT-REDACTED済みの値を再マスキングしない
    private static final Pattern PASSWORD_KV_PATTERN =
        Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token)(\\s*[=:]\\s*)(?!\\[JWT)\\S+");

    private static final Pattern PASSWORD_JSON_PATTERN =
        Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token)(\"\\s*:\\s*\")([^\"]*)\"");

    public static String mask(String message) {
        if (message == null) return null;

        // fast-path: PIIを含まない可能性が高いメッセージをスキップ
        // 注意: 新しいパターン追加時はこのチェックも更新すること
        if (!mayContainSensitiveData(message)) {
            return message;
        }

        String masked = EMAIL_PATTERN.matcher(message)
            .replaceAll("***@***.***");
        masked = PHONE_PATTERN.matcher(masked)
            .replaceAll("***-****-****");
        masked = JWT_PATTERN.matcher(masked)
            .replaceAll("[JWT-REDACTED]");
        // JSON形式を先に処理（"password":"value"）、次に一般形式（password=value）
        masked = PASSWORD_JSON_PATTERN.matcher(masked)
            .replaceAll("$1$2*****\"");
        masked = PASSWORD_KV_PATTERN.matcher(masked)
            .replaceAll("$1$2*****");
        return masked;
    }

    /**
     * メッセージが機密データを含む可能性があるか簡易判定する。
     * '@'、0始まり数字-ハイフン、'eyJ'、パスワード系キーワードのいずれかを含む場合のみtrue。
     * <p>注意: 新しいマスキングパターン追加時はこのメソッドも更新すること。</p>
     */
    static boolean mayContainSensitiveData(String message) {
        if (message.isEmpty()) return false;
        // Email check
        if (message.indexOf('@') >= 0) return true;
        // Phone check (0始まりの数字-ハイフンシーケンス)
        if (message.indexOf('-') >= 0 && containsPhoneLikeSequence(message)) return true;
        // JWT check
        if (message.contains("eyJ")) return true;
        // Password/Token KV check
        return containsPasswordKeyword(message);
    }

    // M4/m1対策: 電話番号パターンに合わせて0始まりチェック
    private static boolean containsPhoneLikeSequence(String message) {
        for (int i = 1; i < message.length(); i++) {
            if (message.charAt(i) == '-' && message.charAt(i - 1) == '0') {
                return true;
            }
            // 0x-で始まるパターン（03-, 090-等）
            if (message.charAt(i) == '-' && i >= 2
                    && message.charAt(i - 2) == '0' && Character.isDigit(message.charAt(i - 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPasswordKeyword(String message) {
        String lower = message.toLowerCase();
        return lower.contains("password") || lower.contains("passwd")
            || lower.contains("pwd") || lower.contains("secret")
            || lower.contains("token");
    }
}
