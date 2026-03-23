package com.wms.outbound.repository;

import com.wms.outbound.entity.PickingInstruction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PickingInstructionRepository extends JpaRepository<PickingInstruction, Long> {

    @Query("""
            SELECT p FROM PickingInstruction p
            WHERE p.warehouseId = :warehouseId
              AND (:instructionNumber IS NULL OR p.instructionNumber LIKE CONCAT(:instructionNumber, '%') ESCAPE '\\')
              AND (:statuses IS NULL OR p.status IN :statuses)
              AND (CAST(:createdDateFrom AS java.time.OffsetDateTime) IS NULL OR p.createdAt >= :createdDateFrom)
              AND (CAST(:createdDateTo AS java.time.OffsetDateTime) IS NULL OR p.createdAt < :createdDateTo)
            """)
    Page<PickingInstruction> search(
            @Param("warehouseId") Long warehouseId,
            @Param("instructionNumber") String instructionNumber,
            @Param("statuses") List<String> statuses,
            @Param("createdDateFrom") OffsetDateTime createdDateFrom,
            @Param("createdDateTo") OffsetDateTime createdDateTo,
            Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    @Query("SELECT p FROM PickingInstruction p WHERE p.id = :id")
    Optional<PickingInstruction> findByIdWithLines(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "lines")
    @Query("SELECT p FROM PickingInstruction p WHERE p.id = :id")
    Optional<PickingInstruction> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(p.instructionNumber, LENGTH(CONCAT('PIC-', :dateStr, '-')) + 1) AS integer)), 0)
            FROM PickingInstruction p
            WHERE p.instructionNumber LIKE CONCAT('PIC-', :dateStr, '-%')
            """)
    int findMaxSequenceByDate(@Param("dateStr") String dateStr);

    @Query("SELECT COUNT(l) FROM PickingInstructionLine l WHERE l.pickingInstruction.id = :pickingInstructionId")
    long countLinesByInstructionId(@Param("pickingInstructionId") Long pickingInstructionId);
}
