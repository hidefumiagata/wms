package com.wms.report.repository;

import com.wms.report.entity.BatchExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface BatchExecutionLogRepository extends JpaRepository<BatchExecutionLog, Long> {

    boolean existsByTargetBusinessDateAndStatus(LocalDate targetBusinessDate, String status);

    Optional<BatchExecutionLog> findByTargetBusinessDateAndStatus(
            LocalDate targetBusinessDate, String status);

    @Query("""
            SELECT b FROM BatchExecutionLog b
            WHERE (:executedDateFrom IS NULL OR CAST(b.startedAt AS LocalDate) >= :executedDateFrom)
              AND (:executedDateTo IS NULL OR CAST(b.startedAt AS LocalDate) <= :executedDateTo)
              AND (:targetBusinessDate IS NULL OR b.targetBusinessDate = :targetBusinessDate)
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<BatchExecutionLog> search(
            @Param("executedDateFrom") LocalDate executedDateFrom,
            @Param("executedDateTo") LocalDate executedDateTo,
            @Param("targetBusinessDate") LocalDate targetBusinessDate,
            @Param("status") String status,
            Pageable pageable);

    @Modifying
    @Query("DELETE FROM BatchExecutionLog b WHERE b.targetBusinessDate = :date AND b.status = 'FAILED'")
    void deleteFailedByTargetBusinessDate(@Param("date") LocalDate date);

    @Modifying
    @Query("""
            UPDATE BatchExecutionLog b
            SET b.status = :status, b.completedAt = :completedAt
            WHERE b.id = :id
            """)
    void updateStatus(@Param("id") Long id, @Param("status") String status,
                      @Param("completedAt") OffsetDateTime completedAt);

    @Modifying
    @Query("""
            UPDATE BatchExecutionLog b
            SET b.status = :status, b.errorMessage = :errorMessage, b.completedAt = :completedAt
            WHERE b.id = :id
            """)
    void updateStatusWithError(@Param("id") Long id, @Param("status") String status,
                                @Param("errorMessage") String errorMessage,
                                @Param("completedAt") OffsetDateTime completedAt);
}
