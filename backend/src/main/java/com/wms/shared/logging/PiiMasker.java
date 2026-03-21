package com.wms.shared.logging;

import java.util.regex.Pattern;

/**
 * ログメッセージ中のPII（個人情報）・機密情報をマスクするユーティリティ。
 * LogstashEncoder の MessageJsonProvider カスタマイズで自動適用される。
 */
public final class PiiMasker {

    private PiiMasker() {}

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9\\-]+(?:\\.[a-zA-Z0-9\\-]+)*\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
        Pattern.compile(
            "0[0-9]{1,4}-[0-9]{1,4}-[0-9]{3,4}");

    /** JWTトークン（eyJで始まるBase64URLエンコード文字列） */
    private static final Pattern JWT_PATTERN =
        Pattern.compile(
            "eyJ[a-zA-Z0-9_-]+\\.eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");

    /** パスワード系キーバリューペア（password=xxx, pwd=xxx 等） */
    private static final Pattern PASSWORD_KV_PATTERN =
        Pattern.compile(
            "(?i)(password|passwd|pwd|secret)\\s*[=:]\\s*\\S+");

    public static String mask(String message) {
        if (message == null) return null;

        // fast-path: PIIを含まない可能性が高いメッセージをスキップ
        if (!mayContainSensitiveData(message)) {
            return message;
        }

        String masked = EMAIL_PATTERN.matcher(message)
            .replaceAll("***@***.***");
        masked = PHONE_PATTERN.matcher(masked)
            .replaceAll("***-****-****");
        masked = JWT_PATTERN.matcher(masked)
            .replaceAll("[JWT-REDACTED]");
        masked = PASSWORD_KV_PATTERN.matcher(masked)
            .replaceAll("$1=*****");
        return masked;
    }

    /**
     * メッセージが機密データを含む可能性があるか簡易判定する。
     * '@'、ハイフン付き数字、'eyJ'、パスワード系キーワードのいずれかを含む場合のみtrue。
     */
    static boolean mayContainSensitiveData(String message) {
        if (message.isEmpty()) return false;
        // Email check
        if (message.indexOf('@') >= 0) return true;
        // Phone check (ハイフン付き数字)
        if (message.indexOf('-') >= 0 && containsDigitHyphenSequence(message)) return true;
        // JWT check
        if (message.contains("eyJ")) return true;
        // Password KV check
        return containsPasswordKeyword(message);
    }

    private static boolean containsDigitHyphenSequence(String message) {
        for (int i = 1; i < message.length(); i++) {
            if (message.charAt(i) == '-' && Character.isDigit(message.charAt(i - 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPasswordKeyword(String message) {
        String lower = message.toLowerCase();
        return lower.contains("password") || lower.contains("passwd")
            || lower.contains("pwd") || lower.contains("secret");
    }
}
