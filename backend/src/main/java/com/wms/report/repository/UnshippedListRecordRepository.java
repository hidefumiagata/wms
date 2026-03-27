package com.wms.report.repository;

import com.wms.report.entity.UnshippedListRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * RPT-16: 未出荷リスト（確定）リポジトリ。
 * 日替処理で生成された unshipped_list_records テーブルからデータを取得する。
 */
public interface UnshippedListRecordRepository extends JpaRepository<UnshippedListRecord, Long> {

    List<UnshippedListRecord> findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
            LocalDate batchBusinessDate, String warehouseCode);
}
