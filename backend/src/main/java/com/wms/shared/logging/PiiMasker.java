package com.wms.shared.logging;

import java.util.regex.Pattern;

/**
 * ログメッセージ中のメールアドレス・電話番号をマスクするユーティリティ。
 * LogstashEncoder の MessageJsonProvider カスタマイズ等で使用する。
 */
public final class PiiMasker {

    private PiiMasker() {}

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
        Pattern.compile(
            "0\\d{1,4}[-\\s]?\\d{1,4}[-\\s]?\\d{2,5}");

    public static String mask(String message) {
        if (message == null) return null;
        String masked = EMAIL_PATTERN.matcher(message)
            .replaceAll("***@***.***");
        masked = PHONE_PATTERN.matcher(masked)
            .replaceAll("***-****-****");
        return masked;
    }
}
