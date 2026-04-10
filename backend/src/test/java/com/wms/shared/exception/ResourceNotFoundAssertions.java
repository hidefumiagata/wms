package com.wms.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ResourceNotFoundException} 用テストアサーションヘルパ。
 *
 * <p><strong>目的:</strong> OWASP A09 (Security Logging and Monitoring Failures) 対応の検証として、
 * 例外メッセージが {@link ResourceNotFoundException#of(String, String, Object)} ファクトリが生成する
 * 固定文言 ({@code "<resourceName> が見つかりません"}) と <strong>完全一致</strong> することを保証する。
 *
 * <p>完全一致アサーションにより、以下をワンショットで担保できる:
 * <ul>
 *   <li>内部 PK (id) 値がメッセージに混入していない — 連鎖 lookup (先行 entity → 後続 entity) ケースで
 *       先行 lookup の id (例: {@code 1L}, {@code 5L}, {@code 10L} 等) が漏れていても検知可能。
 *       {@code hasMessageNotContaining("999")} のような特定値ホワイトリスト方式の盲点を排除する。</li>
 *   <li>{@code "id="} 等の構造的リーク文字列が含まれない — 単なる文字列非包含アサーションのトートロジー
 *       (ファクトリ仕様上そもそも {@code id=} は出力されないため常に真) を排除する。</li>
 *   <li>追加情報 (stack trace 以外の付帯文字列) が一切混入していない。</li>
 * </ul>
 *
 * <p>本ヘルパはファクトリ実装 ({@link ResourceNotFoundException#of(String, String, Object)}) の
 * フォーマット文字列 {@code "%s が見つかりません"} (半角スペース 1 つ) にロックオンしている。
 * ファクトリ実装のフォーマットを変更する場合は、本ヘルパも合わせて更新すること。
 */
public final class ResourceNotFoundAssertions {

    private ResourceNotFoundAssertions() {
        // utility class
    }

    /**
     * throwable が {@link ResourceNotFoundException} であり、errorCode が一致し、メッセージが
     * {@code "<resourceName> が見つかりません"} と完全一致することを検証する。
     *
     * @param thrown       検査対象 throwable (通常 {@code catchThrowable(...)} の戻り値)
     * @param errorCode    期待する errorCode (例: {@code "WAREHOUSE_NOT_FOUND"})
     * @param resourceName 期待する resourceName (例: {@code "倉庫"}, {@code "棚卸"})
     */
    public static void assertResourceNotFound(Throwable thrown,
                                              String errorCode,
                                              String resourceName) {
        assertThat(thrown)
                .as("ResourceNotFoundException であること")
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(String.format("%s が見つかりません", resourceName));
        assertThat(((ResourceNotFoundException) thrown).getErrorCode())
                .as("errorCode が一致すること")
                .isEqualTo(errorCode);
    }
}
