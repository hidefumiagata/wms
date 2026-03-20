package com.wms.shared.exception;

/** 409 Conflict — 楽観的ロック競合 */
public final class OptimisticLockConflictException extends WmsException {

    private static final String DEFAULT_CODE = "OPTIMISTIC_LOCK_CONFLICT";

    public OptimisticLockConflictException() {
        super(DEFAULT_CODE, "他のユーザーが更新済みです。画面を再読み込みしてください");
    }

    public OptimisticLockConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
