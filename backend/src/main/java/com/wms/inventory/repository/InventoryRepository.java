package com.wms.inventory.repository;

import com.wms.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
            Long locationId, Long productId, String unitType, String lotNumber, LocalDate expiryDate);

    boolean existsByLocationIdAndProductIdNot(Long locationId, Long productId);

    /**
     * 引当用在庫検索 — FEFO/FIFO順でソート。有効在庫（quantity - allocated_qty > 0）のみ対象。
     * 同一荷姿を優先、異なる荷姿はケース→ボール→ピースの順。
     */
    @Query("""
            SELECT i FROM Inventory i
            WHERE i.warehouseId = :warehouseId
              AND i.productId = :productId
              AND (i.quantity - i.allocatedQty) > 0
            ORDER BY i.expiryDate ASC NULLS LAST, i.updatedAt ASC
            """)
    List<Inventory> findAvailableStock(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);
}
