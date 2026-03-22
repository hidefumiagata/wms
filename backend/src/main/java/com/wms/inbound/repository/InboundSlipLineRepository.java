package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundSlipLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface InboundSlipLineRepository extends JpaRepository<InboundSlipLine, Long> {

    long countByInboundSlipId(Long inboundSlipId);

    @Query(value = """
            SELECT l FROM InboundSlipLine l JOIN FETCH l.inboundSlip s
            WHERE s.warehouseId = :warehouseId
              AND l.lineStatus = 'STORED'
              AND l.storedAt >= :storedDateFrom
              AND l.storedAt <= :storedDateTo
              AND (:partnerId IS NULL OR s.partnerId = :partnerId)
              AND (:slipNumber IS NULL OR s.slipNumber LIKE CONCAT(:slipNumber, '%') ESCAPE '\\')
              AND (:productCode IS NULL OR l.productCode LIKE CONCAT(:productCode, '%') ESCAPE '\\')
            """,
            countQuery = """
            SELECT COUNT(l) FROM InboundSlipLine l JOIN l.inboundSlip s
            WHERE s.warehouseId = :warehouseId
              AND l.lineStatus = 'STORED'
              AND l.storedAt >= :storedDateFrom
              AND l.storedAt <= :storedDateTo
              AND (:partnerId IS NULL OR s.partnerId = :partnerId)
              AND (:slipNumber IS NULL OR s.slipNumber LIKE CONCAT(:slipNumber, '%') ESCAPE '\\')
              AND (:productCode IS NULL OR l.productCode LIKE CONCAT(:productCode, '%') ESCAPE '\\')
            """)
    Page<InboundSlipLine> searchResults(
            @Param("warehouseId") Long warehouseId,
            @Param("storedDateFrom") OffsetDateTime storedDateFrom,
            @Param("storedDateTo") OffsetDateTime storedDateTo,
            @Param("partnerId") Long partnerId,
            @Param("slipNumber") String slipNumber,
            @Param("productCode") String productCode,
            Pageable pageable);
}
