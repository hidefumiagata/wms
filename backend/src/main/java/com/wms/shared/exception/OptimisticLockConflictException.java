package com.wms.shared.exception;

/** 409 Conflict — 楽観的ロック競合 */
public final class OptimisticLockConflictException extends WmsException {

    private static final String DEFAULT_CODE = "OPTIMISTIC_LOCK_CONFLICT";
    private static final String STANDARD_MESSAGE = "他のユーザーによる更新が先行しました";

    public OptimisticLockConflictException() {
        super(DEFAULT_CODE, "他のユーザーが更新済みです。画面を再読み込みしてください");
    }

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
    public static OptimisticLockConflictException standard() {
        return new OptimisticLockConflictException(DEFAULT_CODE, STANDARD_MESSAGE);
    }
}
