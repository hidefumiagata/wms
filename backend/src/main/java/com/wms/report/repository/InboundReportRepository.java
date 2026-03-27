package com.wms.report.repository;

import com.wms.inbound.entity.InboundSlipLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 入荷系レポートのデータ取得用リポジトリ。
 * 既存の InboundSlipRepository / InboundSlipLineRepository とは別に、
 * レポート固有の結合・集計クエリを定義する。
 */
public interface InboundReportRepository extends JpaRepository<InboundSlipLine, Long> {

    /**
     * RPT-01: 入荷検品レポート用データ取得。
     * 指定伝票の明細行を商品情報（ケース入数）と結合して取得する。
     */
    @Query("""
            SELECT l FROM InboundSlipLine l
            JOIN FETCH l.inboundSlip s
            WHERE s.id = :slipId
            ORDER BY l.lineNo ASC
            """)
    List<InboundSlipLine> findInspectionReportData(@Param("slipId") Long slipId);

    /**
     * RPT-03: 入荷予定レポート用データ取得。
     * 条件に合致する入荷伝票の明細行を取得する。
     */
    @Query("""
            SELECT l FROM InboundSlipLine l
            JOIN FETCH l.inboundSlip s
            WHERE s.warehouseId = :warehouseId
              AND (CAST(:plannedDateFrom AS java.time.LocalDate) IS NULL OR s.plannedDate >= :plannedDateFrom)
              AND (CAST(:plannedDateTo AS java.time.LocalDate) IS NULL OR s.plannedDate <= :plannedDateTo)
              AND (:status IS NULL OR s.status = :status)
              AND (:partnerId IS NULL OR s.partnerId = :partnerId)
            ORDER BY s.plannedDate ASC, s.partnerName ASC, l.productCode ASC
            """)
    List<InboundSlipLine> findPlanReportData(
            @Param("warehouseId") Long warehouseId,
            @Param("plannedDateFrom") LocalDate plannedDateFrom,
            @Param("plannedDateTo") LocalDate plannedDateTo,
            @Param("status") String status,
            @Param("partnerId") Long partnerId);

    /**
     * RPT-04: 入庫実績レポート用データ取得。
     * 入庫完了（STORED）の明細行を取得する。
     */
    @Query("""
            SELECT l FROM InboundSlipLine l
            JOIN FETCH l.inboundSlip s
            WHERE s.warehouseId = :warehouseId
              AND l.lineStatus = 'STORED'
              AND (CAST(:storedDateFrom AS java.time.OffsetDateTime) IS NULL OR l.storedAt >= :storedDateFrom)
              AND (CAST(:storedDateTo AS java.time.OffsetDateTime) IS NULL OR l.storedAt < :storedDateTo)
              AND (:partnerId IS NULL OR s.partnerId = :partnerId)
            ORDER BY l.storedAt DESC, s.slipNumber ASC
            """)
    List<InboundSlipLine> findResultReportData(
            @Param("warehouseId") Long warehouseId,
            @Param("storedDateFrom") OffsetDateTime storedDateFrom,
            @Param("storedDateTo") OffsetDateTime storedDateTo,
            @Param("partnerId") Long partnerId);

    /**
     * RPT-05: 未入荷リスト（リアルタイム）用データ取得。
     * 入荷予定日が基準日以前で、入庫完了・キャンセル以外の伝票を取得する。
     */
    @Query("""
            SELECT l FROM InboundSlipLine l
            JOIN FETCH l.inboundSlip s
            WHERE s.warehouseId = :warehouseId
              AND s.plannedDate <= :asOfDate
              AND s.status NOT IN ('STORED', 'CANCELLED')
            ORDER BY s.partnerName ASC, s.plannedDate ASC, s.slipNumber ASC
            """)
    List<InboundSlipLine> findUnreceivedRealtimeData(
            @Param("warehouseId") Long warehouseId,
            @Param("asOfDate") LocalDate asOfDate);
}
