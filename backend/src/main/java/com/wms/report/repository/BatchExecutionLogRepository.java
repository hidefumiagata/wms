package com.wms.report.repository;

import com.wms.report.entity.BatchExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

/**
 * バッチ実行履歴リポジトリ。
 * RPT-17 で日替処理の SUCCESS 完了を確認するために使用する。
 */
public interface BatchExecutionLogRepository extends JpaRepository<BatchExecutionLog, Long> {

    boolean existsByTargetBusinessDateAndStatus(LocalDate targetBusinessDate, String status);
}
