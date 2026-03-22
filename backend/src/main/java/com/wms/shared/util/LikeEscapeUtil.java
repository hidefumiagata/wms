package com.wms.shared.util;

/**
 * JPQL LIKE 検索用のワイルドカードエスケープユーティリティ。
 * {@code %} と {@code _} をエスケープし、LIKE句での意図しないマッチを防止する。
 * JPQL側で {@code ESCAPE '\'} を指定すること。
 */
public final class LikeEscapeUtil {

    private LikeEscapeUtil() {}

    /**
     * LIKE 検索値のワイルドカードをエスケープする。
     * null の場合は null をそのまま返す（フィルタ無効を表す）。
     */
    public static String escape(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
