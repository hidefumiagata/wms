package com.wms.report.repository;

import com.wms.report.entity.DailySummaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * RPT-18: 返品レポート用リポジトリ。
 * return_slips テーブルを products, partners と結合して返品明細を取得する。
 */
public interface ReturnsReportRepository extends JpaRepository<DailySummaryRecord, Long> {

    /**
     * 返品レポートデータ取得。
     * 返品種別昇順 → 返品日昇順 → 返品伝票番号昇順でソート。
     */
    @Query(value = """
            SELECT rs.return_number,
                   rs.return_type,
                   rs.return_date,
                   rs.product_code,
                   rs.product_name,
                   rs.quantity,
                   rs.unit_type,
                   rs.return_reason,
                   rs.return_reason_note,
                   rs.related_slip_number,
                   rs.partner_name
            FROM return_slips rs
            WHERE rs.warehouse_id = :warehouseId
              AND (:returnType IS NULL OR rs.return_type = :returnType)
              AND (:returnDateFrom IS NULL OR rs.return_date >= :returnDateFrom)
              AND (:returnDateTo IS NULL OR rs.return_date <= :returnDateTo)
              AND (:productId IS NULL OR rs.product_id = :productId)
              AND (:partnerId IS NULL OR rs.partner_id = :partnerId)
              AND (:returnReason IS NULL OR rs.return_reason = :returnReason)
            ORDER BY rs.return_type ASC, rs.return_date ASC, rs.return_number ASC
            """, nativeQuery = true)
    List<Object[]> findReturnsReportData(
            @Param("warehouseId") Long warehouseId,
            @Param("returnType") String returnType,
            @Param("returnDateFrom") LocalDate returnDateFrom,
            @Param("returnDateTo") LocalDate returnDateTo,
            @Param("productId") Long productId,
            @Param("partnerId") Long partnerId,
            @Param("returnReason") String returnReason);
}
