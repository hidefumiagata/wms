package com.wms.shared.exception;

/** 409 Conflict — 楽観的ロック競合 */
public final class OptimisticLockConflictException extends WmsException {

    private static final String DEFAULT_CODE = "OPTIMISTIC_LOCK_CONFLICT";
    private static final String STANDARD_MESSAGE = "他のユーザーによる更新が先行しました";

    /**
     * カスタム errorCode / message を指定するコンストラクタ。
     *
     * @deprecated 新規コードでは {@link #standard()} を使用すること。メッセージに内部 ID
     *     (id / key / 伝票番号等) を含めると OWASP A09 (Security Logging and Monitoring
     *     Failures) に該当するため、全 Service で共通メッセージを用いる {@code standard()}
     *     に統一している。追跡用の識別子は呼び出し元 Service で throw 直前に
     *     {@code log.info} へ出力する運用とする。
     *
     *     <p><strong>削除予定はない:</strong> 本コンストラクタは {@code standard()} の
     *     内部実装および既存テスト互換のため残置する方針。{@code forRemoval = false} を
     *     明示する。</p>
     */
    @Deprecated(since = "2026-04-10", forRemoval = false)
    public OptimisticLockConflictException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 標準ファクトリ: 全 Service 共通の楽観的ロック競合例外を生成する。
     *
     * <p>OWASP A09 (Security Logging and Monitoring Failures):
     * 例外メッセージには内部 PK (id) を含めない。追跡用の id は呼び出し元 (Service 層)
     * で例外 throw 前に log.info / log.warn へ出力すること。</p>
     */
    @SuppressWarnings("deprecation")
    public static OptimisticLockConflictException standard() {
        return new OptimisticLockConflictException(DEFAULT_CODE, STANDARD_MESSAGE);
    }
}
