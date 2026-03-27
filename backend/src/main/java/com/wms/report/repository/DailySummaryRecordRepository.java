package com.wms.report.repository;

import com.wms.report.entity.DailySummaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * RPT-17: 日次集計レポート用リポジトリ。
 * daily_summary_records テーブルと warehouses テーブルを結合して倉庫名を取得する。
 */
public interface DailySummaryRecordRepository extends JpaRepository<DailySummaryRecord, Long> {

    /**
     * 指定営業日の日次集計レコードを倉庫名付きで取得する。
     * 倉庫IDの昇順でソート。
     */
    @Query(value = """
            SELECT dsr.business_date,
                   dsr.warehouse_id,
                   w.warehouse_name,
                   dsr.inbound_count,
                   dsr.inbound_line_count,
                   dsr.inbound_quantity_total,
                   dsr.outbound_count,
                   dsr.outbound_line_count,
                   dsr.outbound_quantity_total,
                   dsr.return_count,
                   dsr.return_quantity_total,
                   dsr.inventory_quantity_total,
                   dsr.unreceived_count,
                   dsr.unshipped_count
            FROM daily_summary_records dsr
              JOIN warehouses w ON dsr.warehouse_id = w.id
            WHERE dsr.business_date = :businessDate
            ORDER BY dsr.warehouse_id ASC
            """, nativeQuery = true)
    List<Object[]> findDailySummaryData(@Param("businessDate") LocalDate businessDate);
}
