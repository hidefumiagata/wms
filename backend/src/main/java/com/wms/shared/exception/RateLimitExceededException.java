package com.wms.shared.exception;

/**
 * 429 Too Many Requests — レートリミット超過。
 * sealed WmsException の外（RuntimeException直系）に定義する。
 * レートリミットはビジネスロジックではなくインフラ的な制約のため。
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("リクエスト回数の上限を超えました。しばらく待ってから再度お試しください");
    }
}
