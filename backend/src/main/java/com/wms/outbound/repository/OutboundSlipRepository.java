package com.wms.outbound.repository;

import com.wms.outbound.entity.OutboundSlip;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OutboundSlipRepository extends JpaRepository<OutboundSlip, Long> {

    @Query("""
            SELECT s FROM OutboundSlip s
            WHERE s.warehouseId = :warehouseId
              AND (:slipNumber IS NULL OR s.slipNumber LIKE CONCAT(:slipNumber, '%') ESCAPE '\\')
              AND (:statuses IS NULL OR s.status IN :statuses)
              AND (CAST(:plannedDateFrom AS java.time.LocalDate) IS NULL OR s.plannedDate >= :plannedDateFrom)
              AND (CAST(:plannedDateTo AS java.time.LocalDate) IS NULL OR s.plannedDate <= :plannedDateTo)
              AND (:partnerId IS NULL OR s.partnerId = :partnerId)
            """)
    Page<OutboundSlip> search(
            @Param("warehouseId") Long warehouseId,
            @Param("slipNumber") String slipNumber,
            @Param("statuses") List<String> statuses,
            @Param("plannedDateFrom") LocalDate plannedDateFrom,
            @Param("plannedDateTo") LocalDate plannedDateTo,
            @Param("partnerId") Long partnerId,
            Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    @Query("SELECT s FROM OutboundSlip s WHERE s.id = :id")
    Optional<OutboundSlip> findByIdWithLines(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "lines")
    @Query("SELECT s FROM OutboundSlip s WHERE s.id = :id")
    Optional<OutboundSlip> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(s.slipNumber, LENGTH(CONCAT('OUT-', :dateStr, '-')) + 1) AS integer)), 0)
            FROM OutboundSlip s
            WHERE s.slipNumber LIKE CONCAT('OUT-', :dateStr, '-%')
            """)
    int findMaxSequenceByDate(@Param("dateStr") String dateStr);

    @Query("SELECT COUNT(l) FROM OutboundSlipLine l WHERE l.outboundSlip.id = :slipId")
    long countLinesBySlipId(@Param("slipId") Long slipId);

    /**
     * 引当対象受注検索。partnerNameは部分一致。
     */
    @Query("""
            SELECT s FROM OutboundSlip s
            WHERE s.warehouseId = :warehouseId
              AND (:statuses IS NULL OR s.status IN :statuses)
              AND (CAST(:shippingDateFrom AS java.time.LocalDate) IS NULL OR s.plannedDate >= :shippingDateFrom)
              AND (CAST(:shippingDateTo AS java.time.LocalDate) IS NULL OR s.plannedDate <= :shippingDateTo)
              AND (:partnerName IS NULL OR s.partnerName LIKE CONCAT('%', :partnerName, '%') ESCAPE '\\')
            """)
    Page<OutboundSlip> searchForAllocation(
            @Param("warehouseId") Long warehouseId,
            @Param("statuses") List<String> statuses,
            @Param("shippingDateFrom") LocalDate shippingDateFrom,
            @Param("shippingDateTo") LocalDate shippingDateTo,
            @Param("partnerName") String partnerName,
            Pageable pageable);

    /**
     * 引当済み受注検索（ALLOCATED/PARTIAL_ALLOCATED）。
     */
    @Query("""
            SELECT s FROM OutboundSlip s
            WHERE s.status IN :statuses
            """)
    Page<OutboundSlip> findByStatusIn(
            @Param("statuses") List<String> statuses,
            Pageable pageable);

    /**
     * 引当済み明細数をカウント。
     */
    @Query("""
            SELECT COUNT(l) FROM OutboundSlipLine l
            WHERE l.outboundSlip.id = :slipId
              AND l.lineStatus = 'ALLOCATED'
            """)
    long countAllocatedLinesBySlipId(@Param("slipId") Long slipId);

    /**
     * 出荷明細IDから親の出荷伝票をロード（明細含む）。
     */
    @EntityGraph(attributePaths = "lines")
    @Query("SELECT s FROM OutboundSlip s JOIN s.lines l WHERE l.id = :slipLineId")
    Optional<OutboundSlip> findBySlipLineId(@Param("slipLineId") Long slipLineId);
}
