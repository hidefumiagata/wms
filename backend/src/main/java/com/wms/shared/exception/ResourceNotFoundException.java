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
     * <p><strong>ロガー名に関する注意:</strong> 本ファクトリから出力されるログは
     * {@code com.wms.shared.exception.ResourceNotFoundException} ロガーで発行される
     * (呼び出し元 Service クラスのロガーではない)。運用ログ検索時に発生源 Service を
     * ロガー名で特定したい場合は、本ファクトリを使わず、呼び出し元 Service 層で
     * 先に {@code log.info(...)} を明示的に出力してから 2 引数コンストラクタ
     * {@link #ResourceNotFoundException(String, String)} を直接使用すること。
     * メッセージ本文 ({@code "{resourceName} not found: id=..., errorCode=..."}) と
     * resourceName はロガー名に依らず一意に grep 可能なため、通常運用では本ファクトリの
     * 利用で差し支えない (MDC の traceId も併用される)。</p>
     *
     * <p><strong>id 引数の安全性:</strong> {@code id} は信頼できる内部 PK (Long 等の数値型) のみを
     * 渡すこと。ユーザー入力由来の文字列を直接渡すと、{@code \r\n} を含む値で偽ログ行が挿入される
     * (CRLF ログインジェクション) リスクがある。文字列 ID をログ出力する場合は呼び出し元で
     * 改行文字をサニタイズしてから渡すこと。</p>
     *
     * @param id 内部 PK — メッセージには埋め込まれず、ログ出力専用。
     */
    public static ResourceNotFoundException of(String errorCode, String resourceName, Object id) {
        LOG.info("{} not found: id={}, errorCode={}", resourceName, id, errorCode);
        return new ResourceNotFoundException(errorCode,
                String.format("%s が見つかりません", resourceName));
    }
}
