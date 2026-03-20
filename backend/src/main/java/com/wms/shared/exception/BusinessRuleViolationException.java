package com.wms.shared.exception;

/** 422 Unprocessable Entity — 業務ルール違反 */
public final class BusinessRuleViolationException extends WmsException {

    public BusinessRuleViolationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleViolationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
