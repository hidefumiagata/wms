package com.wms.shared.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ServiceLoggingAspect {

    /**
     * Service 層の public メソッドの実行時間をログ出力する。
     */
    @Around("execution(* com.wms..service.*Service.*(..))")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint)
            throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // モジュール名を MDC に設定（ネスト対応: 前の値を退避・復元）
        String module = extractModule(
            joinPoint.getTarget().getClass().getPackageName());
        String previousModule = MDC.get("module");
        MDC.put("module", module);

        log.info("START {}.{}", className, methodName);
        long start = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("END {}.{} [{}ms]", className, methodName, elapsedMs);
            return result;
        } catch (Throwable ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("FAIL {}.{} [{}ms] - {}",
                className, methodName, elapsedMs,
                PiiMasker.mask(ex.getMessage()));
            throw ex;
        } finally {
            if (previousModule != null) {
                MDC.put("module", previousModule);
            } else {
                MDC.remove("module");
            }
        }
    }

    private String extractModule(String packageName) {
        // com.wms.inbound.service -> inbound
        String[] parts = packageName.split("\\.");
        return parts.length >= 3 ? parts[2] : "unknown";
    }
}
