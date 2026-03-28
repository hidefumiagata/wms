package com.wms.inventory.repository;

import com.wms.inventory.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query("SELECT m FROM InventoryMovement m WHERE m.warehouseId = :warehouseId"
            + " AND m.locationId = :locationId AND m.productId = :productId"
            + " AND m.unitType = :unitType AND m.movementType = :movementType"
            + " ORDER BY m.executedAt DESC LIMIT 5")
    List<InventoryMovement> findRecentByCondition(
            Long warehouseId, Long locationId, Long productId, String unitType, String movementType);
}
