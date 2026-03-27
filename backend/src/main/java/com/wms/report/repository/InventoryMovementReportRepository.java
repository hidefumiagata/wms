package com.wms.report.repository;

import com.wms.inventory.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 在庫変動系レポートのデータ取得用リポジトリ。
 * RPT-08（在庫推移）、RPT-09（在庫訂正一覧）用のクエリを提供する。
 */
public interface InventoryMovementReportRepository extends JpaRepository<InventoryMovement, Long> {

    /**
     * RPT-08: 在庫推移レポート用データ取得。
     * 指定倉庫・商品・期間の在庫変動履歴を変動日昇順で取得する。
     */
    @Query("""
            SELECT m FROM InventoryMovement m
            WHERE m.warehouseId = :warehouseId
              AND m.productId = :productId
              AND m.executedAt >= :dateFrom
              AND m.executedAt < :dateTo
            ORDER BY m.executedAt ASC, m.id ASC
            """)
    List<InventoryMovement> findTransitionReportData(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo);

    /**
     * RPT-09: 在庫訂正一覧用データ取得。
     * movement_type = 'CORRECTION' のレコードを訂正日降順で取得する。
     */
    @Query("""
            SELECT m FROM InventoryMovement m
            WHERE m.warehouseId = :warehouseId
              AND m.movementType = 'CORRECTION'
              AND m.executedAt >= :dateFrom
              AND m.executedAt < :dateTo
            ORDER BY m.executedAt ASC, m.locationCode ASC
            """)
    List<InventoryMovement> findCorrectionReportData(
            @Param("warehouseId") Long warehouseId,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo);
}
