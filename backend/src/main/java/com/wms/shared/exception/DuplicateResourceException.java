package com.wms.shared.exception;

/** 409 Conflict — 一意制約違反・重複登録 */
public final class DuplicateResourceException extends WmsException {

    public DuplicateResourceException(String errorCode, String message) {
        super(errorCode, message);
    }
}
