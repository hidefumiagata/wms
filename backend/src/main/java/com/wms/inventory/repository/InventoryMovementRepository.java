package com.wms.inventory.repository;

import com.wms.inventory.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findTop5ByWarehouseIdAndLocationIdAndProductIdAndUnitTypeAndMovementTypeOrderByExecutedAtDesc(
            Long warehouseId, Long locationId, Long productId, String unitType, String movementType);
}
