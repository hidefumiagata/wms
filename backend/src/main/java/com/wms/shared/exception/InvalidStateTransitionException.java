package com.wms.shared.exception;

/** 409 Conflict — 状態遷移不正 */
public final class InvalidStateTransitionException extends WmsException {

    public InvalidStateTransitionException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static InvalidStateTransitionException of(String errorCode,
                                                      String currentStatus,
                                                      String targetStatus) {
        return new InvalidStateTransitionException(errorCode,
                String.format("現在のステータス「%s」から「%s」への遷移はできません",
                        currentStatus, targetStatus));
    }
}
