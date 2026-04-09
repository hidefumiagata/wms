package com.wms.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 404 Not Found — 指定リソースが存在しない */
public final class ResourceNotFoundException extends WmsException {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceNotFoundException.class);

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 汎用ファクトリ: "XXX が見つかりません".
     *
     * <p>OWASP A09 (Security Logging and Monitoring Failures): 例外メッセージには内部 PK (id)
     * を含めない。ID 列挙攻撃への補助情報を与えないため。追跡用の id はこのファクトリ内部で
     * サーバーログ (log.info) へ出力する。</p>
     *
     * @param id 内部 PK — メッセージには埋め込まれず、ログ出力専用。
     */
    public static ResourceNotFoundException of(String errorCode, String resourceName, Object id) {
        LOG.info("{} not found: id={}, errorCode={}", resourceName, id, errorCode);
        return new ResourceNotFoundException(errorCode,
                String.format("%s が見つかりません", resourceName));
    }
}
