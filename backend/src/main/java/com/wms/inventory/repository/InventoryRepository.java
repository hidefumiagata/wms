package com.wms.inventory.repository;

import com.wms.inventory.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
            Long locationId, Long productId, String unitType, String lotNumber, LocalDate expiryDate);

    boolean existsByLocationIdAndProductIdNot(Long locationId, Long productId);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Inventory i WHERE i.productId = :productId AND i.quantity > 0")
    boolean existsByProductIdWithPositiveQty(@Param("productId") Long productId);

    /**
     * 引当用在庫検索 — FEFO/FIFO順でソート。有効在庫（quantity - allocated_qty > 0）のみ対象。
     * FIFO基準: inventory_movements の最古INBOUND入庫日時（executed_at）。
     * 入庫履歴がない在庫は最後にソートされる。
     * 荷姿の優先順はService層で制御（Phase1: 同一荷姿、Phase2: 上位荷姿からばらし）。
     */
    @Query(value = """
            SELECT i.* FROM inventories i
            LEFT JOIN (
                SELECT im.product_id, im.location_id, im.unit_type, im.lot_number, im.expiry_date,
                       MIN(im.executed_at) AS first_inbound_at
                FROM inventory_movements im
                WHERE im.movement_type = 'INBOUND'
                  AND im.product_id = :productId
                  AND im.warehouse_id = :warehouseId
                GROUP BY im.product_id, im.location_id, im.unit_type, im.lot_number, im.expiry_date
            ) m ON i.product_id = m.product_id
                AND i.location_id = m.location_id
                AND i.unit_type = m.unit_type
                AND (i.lot_number = m.lot_number OR (i.lot_number IS NULL AND m.lot_number IS NULL))
                AND (i.expiry_date = m.expiry_date OR (i.expiry_date IS NULL AND m.expiry_date IS NULL))
            WHERE i.warehouse_id = :warehouseId
              AND i.product_id = :productId
              AND (i.quantity - i.allocated_qty) > 0
            ORDER BY i.expiry_date ASC NULLS LAST, m.first_inbound_at ASC NULLS LAST, i.id ASC
            """, nativeQuery = true)
    List<Inventory> findAvailableStock(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.locationId = :locationId AND i.unitType = :unitType")
    int sumQuantityByLocationAndUnitType(@Param("locationId") Long locationId, @Param("unitType") String unitType);

    @Query("SELECT i FROM Inventory i WHERE i.locationId IN :locationIds AND i.quantity > 0")
    List<Inventory> findByLocationIdsWithPositiveQty(@Param("locationIds") List<Long> locationIds);

    @Query("""
            SELECT i FROM Inventory i
            WHERE i.warehouseId = :warehouseId
            AND (:locationCodePrefix IS NULL OR EXISTS (
                SELECT 1 FROM Location l WHERE l.id = i.locationId AND l.locationCode LIKE CONCAT(:locationCodePrefix, '%')
            ))
            AND (:productId IS NULL OR i.productId = :productId)
            AND (:unitType IS NULL OR i.unitType = :unitType)
            AND (:storageCondition IS NULL OR EXISTS (
                SELECT 1 FROM Location l JOIN Area a ON l.areaId = a.id WHERE l.id = i.locationId AND a.storageCondition = :storageCondition
            ))
            """)
    Page<Inventory> searchByLocation(
            @Param("warehouseId") Long warehouseId,
            @Param("locationCodePrefix") String locationCodePrefix,
            @Param("productId") Long productId,
            @Param("unitType") String unitType,
            @Param("storageCondition") String storageCondition,
            Pageable pageable);
    @Query("""
            SELECT i.productId,
                   SUM(CASE WHEN i.unitType = 'CASE' THEN i.quantity ELSE 0 END),
                   SUM(CASE WHEN i.unitType = 'BALL' THEN i.quantity ELSE 0 END),
                   SUM(CASE WHEN i.unitType = 'PIECE' THEN i.quantity ELSE 0 END),
                   SUM(i.allocatedQty),
                   SUM(i.quantity) - SUM(i.allocatedQty)
            FROM Inventory i
            WHERE i.warehouseId = :warehouseId
            AND (:productId IS NULL OR i.productId = :productId)
            AND (:storageCondition IS NULL OR EXISTS (
                SELECT 1 FROM Location l JOIN Area a ON l.areaId = a.id WHERE l.id = i.locationId AND a.storageCondition = :storageCondition
            ))
            GROUP BY i.productId
            """)
    Page<Object[]> searchProductSummary(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("storageCondition") String storageCondition,
            Pageable pageable);
}
