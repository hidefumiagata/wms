package com.wms.shared.exception;

/** 404 Not Found — 指定リソースが存在しない */
public final class ResourceNotFoundException extends WmsException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    /** 汎用ファクトリ: "XXX が見つかりません (id=123)" */
    public static ResourceNotFoundException of(String errorCode, String resourceName, Object id) {
        return new ResourceNotFoundException(errorCode,
                String.format("%s が見つかりません (id=%s)", resourceName, id));
    }
}
