package com.wms.shared.exception;

/**
 * 全カスタム例外の基底クラス（sealed）。
 * サブクラスはこのパッケージ内の5種類に限定される。
 */
public abstract sealed class WmsException extends RuntimeException
        permits ResourceNotFoundException,
                DuplicateResourceException,
                BusinessRuleViolationException,
                OptimisticLockConflictException,
                InvalidStateTransitionException {

    private final String errorCode;

    protected WmsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected WmsException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
