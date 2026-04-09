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
     * <p><strong>副作用に関する設計上の注意:</strong> 本ファクトリは「例外構築時」に
     * {@code log.info} を出力する副作用を意図的に持つ。これにより呼び出し元
     * ({@code orElseThrow(() -> ResourceNotFoundException.of(...))}) で追加のログ記述を
     * 不要とし、全モジュールの「リソース未検出ログ」フォーマットを一元化している。
     * この副作用は本クラスの仕様であり、呼び出し元で {@code log.info} を重複して
     * 出力する必要は無い (むしろ禁止)。</p>
     *
     * <p>Pure factory (副作用なし) が必要になった場合は、本ファクトリではなく
     * コンストラクタ {@link #ResourceNotFoundException(String, String)} を直接使用すること。</p>
     *
     * @param id 内部 PK — メッセージには埋め込まれず、ログ出力専用。
     */
    public static ResourceNotFoundException of(String errorCode, String resourceName, Object id) {
        LOG.info("{} not found: id={}, errorCode={}", resourceName, id, errorCode);
        return new ResourceNotFoundException(errorCode,
                String.format("%s が見つかりません", resourceName));
    }
}
