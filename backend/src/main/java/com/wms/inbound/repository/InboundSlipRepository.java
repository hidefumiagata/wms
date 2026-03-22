package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundSlip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InboundSlipRepository extends JpaRepository<InboundSlip, Long> {

    @Query("""
            SELECT s FROM InboundSlip s
            WHERE s.warehouseId = :warehouseId
              AND (:slipNumber IS NULL OR s.slipNumber LIKE :slipNumber || '%' ESCAPE '\\')
              AND (:statuses IS NULL OR s.status IN :statuses)
              AND (CAST(:plannedDateFrom AS java.time.LocalDate) IS NULL OR s.plannedDate >= :plannedDateFrom)
              AND (CAST(:plannedDateTo AS java.time.LocalDate) IS NULL OR s.plannedDate <= :plannedDateTo)
              AND (:partnerId IS NULL OR s.partnerId = :partnerId)
            """)
    Page<InboundSlip> search(
            @Param("warehouseId") Long warehouseId,
            @Param("slipNumber") String slipNumber,
            @Param("statuses") List<String> statuses,
            @Param("plannedDateFrom") LocalDate plannedDateFrom,
            @Param("plannedDateTo") LocalDate plannedDateTo,
            @Param("partnerId") Long partnerId,
            Pageable pageable);
}
