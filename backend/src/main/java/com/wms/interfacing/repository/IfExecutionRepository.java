package com.wms.interfacing.repository;

import com.wms.interfacing.entity.IfExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
              AND (:warehouseId IS NULL OR e.warehouseId = :warehouseId)
              AND (:fileName IS NULL OR LOWER(e.fileName) LIKE LOWER(CONCAT('%', :fileName, '%')))
            """)
    Page<IfExecution> search(
            @Param("ifType") String ifType,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            @Param("status") String status,
            @Param("warehouseId") Long warehouseId,
            @Param("fileName") String fileName,
            Pageable pageable);

    /**
     * {@code blob_move_failed} カラムを false に更新する単一カラム UPDATE。
     *
     * <p>本メソッドは {@code @Modifying} クエリを発行するため、
     * 呼び出し側で必ずトランザクションを張ること
     * （例: {@link org.springframework.transaction.support.TransactionTemplate#execute}
     * または {@code @Transactional}）。非トランザクション文脈で呼ばれた場合は
     * {@code TransactionRequiredException} が発生する。
     *
     * <p>BAT-IF-RECONCILE (Issue #440) との並行更新による Lost Update を回避するため、
     * 全カラム merge ではなく単一カラム UPDATE を採用している。
     *
     * @param id 対象 IfExecution の id
     * @return 更新行数（通常 1。0 の場合はリカバリバッチ等により既に更新済みの可能性）
     */
    @Modifying
    @Query("update IfExecution e set e.blobMoveFailed = false where e.id = :id")
    int markBlobMoveSucceeded(@Param("id") Long id);
}
