package com.wms.shared.exception;

/** 404 Not Found — 指定リソースが存在しない */
public final class ResourceNotFoundException extends WmsException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 汎用ファクトリ: "XXX が見つかりません".
     *
     * <p>OWASP A09 (Security Logging and Monitoring Failures): 例外メッセージには内部 PK (id)
     * を含めない。ID 列挙攻撃への補助情報を与えないため。追跡用の id は呼び出し元 (Service 層)
     * でログ側 (log.info / log.warn) に出力すること。</p>
     *
     * @param id 内部 PK — メッセージには埋め込まれないが、互換性のためシグネチャに残している。
     *           将来的に呼び出し元がログ出力していることを保証できたら削除可能。
     */
    public static ResourceNotFoundException of(String errorCode, String resourceName, Object id) {
        return new ResourceNotFoundException(errorCode,
                String.format("%s が見つかりません", resourceName));
    }
}
