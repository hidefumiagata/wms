package com.wms.returns.repository;

import com.wms.returns.entity.ReturnSlip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ReturnSlipRepository extends JpaRepository<ReturnSlip, Long> {

    @Query("""
            SELECT s FROM ReturnSlip s
            WHERE s.warehouseId = :warehouseId
              AND (:returnType IS NULL OR s.returnType = :returnType)
              AND (CAST(:returnDateFrom AS java.time.LocalDate) IS NULL OR s.returnDate >= :returnDateFrom)
              AND (CAST(:returnDateTo AS java.time.LocalDate) IS NULL OR s.returnDate <= :returnDateTo)
              AND (:productId IS NULL OR s.productId = :productId)
              AND (:partnerId IS NULL OR s.partnerId = :partnerId)
              AND (:returnReason IS NULL OR s.returnReason = :returnReason)
            """)
    Page<ReturnSlip> search(
            @Param("warehouseId") Long warehouseId,
            @Param("returnType") String returnType,
            @Param("returnDateFrom") LocalDate returnDateFrom,
            @Param("returnDateTo") LocalDate returnDateTo,
            @Param("productId") Long productId,
            @Param("partnerId") Long partnerId,
            @Param("returnReason") String returnReason,
            Pageable pageable);

    /**
     * 当日の返品伝票通番の最大値を取得する（全種別共通の通し番号）。
     * 返品番号形式: RTN-{T}-YYYYMMDD-XXXX（16文字目以降が通番）
     */
    @Query("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(s.returnNumber, 16) AS integer)), 0)
            FROM ReturnSlip s
            WHERE s.returnNumber LIKE CONCAT('RTN-%-', :dateStr, '-%')
            """)
    int findMaxSequenceByDate(@Param("dateStr") String dateStr);
}
