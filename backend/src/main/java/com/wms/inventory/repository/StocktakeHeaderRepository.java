package com.wms.inventory.repository;

import com.wms.inventory.entity.StocktakeHeader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface StocktakeHeaderRepository extends JpaRepository<StocktakeHeader, Long> {

    @Query("""
            SELECT h FROM StocktakeHeader h
            WHERE h.warehouseId = :warehouseId
            AND (:status IS NULL OR h.status = :status)
            AND (:dateFrom IS NULL OR h.startedAt >= :dateFrom)
            AND (:dateTo IS NULL OR h.startedAt < :dateTo)
            """)
    Page<StocktakeHeader> search(
            @Param("warehouseId") Long warehouseId,
            @Param("status") String status,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            Pageable pageable);

    @Query("SELECT h FROM StocktakeHeader h LEFT JOIN FETCH h.lines WHERE h.id = :id")
    Optional<StocktakeHeader> findByIdWithLines(@Param("id") Long id);

    @Query("SELECT MAX(CAST(SUBSTRING(h.stocktakeNumber, LENGTH('ST-') + 1) AS int)) FROM StocktakeHeader h")
    Integer findMaxSequence();
}
