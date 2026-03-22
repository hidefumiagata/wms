package com.wms.shared.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * インメモリのレートリミッター。
 * Bucket4jを使用し、キーごとのリクエスト数を制限する。
 * ShowCase規模のためRedis不要。min replicas=0でのコールドスタート時にリセットされるが許容。
 */
@Service
public class RateLimiterService {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * ログインエンドポイント用: 同一IPから15分間に20回まで
     */
    public boolean tryConsumeLogin(String ipAddress) {
        Bucket bucket = buckets.computeIfAbsent(
                "login:ip:" + ipAddress,
                k -> createBucket(20, Duration.ofMinutes(15)));
        return bucket.tryConsume(1);
    }

    /**
     * パスワードリセット用: 同一IPから15分間に5回まで
     */
    public boolean tryConsumePasswordResetByIp(String ipAddress) {
        Bucket bucket = buckets.computeIfAbsent(
                "pw-reset:ip:" + ipAddress,
                k -> createBucket(5, Duration.ofMinutes(15)));
        return bucket.tryConsume(1);
    }

    /**
     * パスワードリセット用: 同一identifierから15分間に3回まで
     */
    public boolean tryConsumePasswordResetByIdentifier(String identifier) {
        Bucket bucket = buckets.computeIfAbsent(
                "pw-reset:id:" + identifier,
                k -> createBucket(3, Duration.ofMinutes(15)));
        return bucket.tryConsume(1);
    }

    /**
     * コード存在確認エンドポイント用: 同一ユーザーから1分間に30回まで
     */
    public boolean tryConsumeCodeExists(String userIdentifier) {
        Bucket bucket = buckets.computeIfAbsent(
                "code-exists:user:" + userIdentifier,
                k -> createBucket(30, Duration.ofMinutes(1)));
        return bucket.tryConsume(1);
    }

    private Bucket createBucket(long capacity, Duration refillDuration) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, refillDuration)
                        .build())
                .build();
    }
}
