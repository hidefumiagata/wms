package com.wms.interfacing.repository;

import com.wms.interfacing.entity.IfExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface IfExecutionRepository extends JpaRepository<IfExecution, Long> {

    @Query("""
            SELECT e FROM IfExecution e
            WHERE (:ifType IS NULL OR e.ifType = :ifType)
              AND (CAST(:dateFrom AS java.time.OffsetDateTime) IS NULL OR e.executedAt >= :dateFrom)
              AND (CAST(:dateTo AS java.time.OffsetDateTime) IS NULL OR e.executedAt < :dateTo)
              AND (:status IS NULL OR e.status = :status)
              AND (:fileName IS NULL OR LOWER(e.fileName) LIKE LOWER(CONCAT('%', :fileName, '%')))
            """)
    Page<IfExecution> search(
            @Param("ifType") String ifType,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            @Param("status") String status,
            @Param("fileName") String fileName,
            Pageable pageable);
}
