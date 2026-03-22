package com.wms.inventory.repository;

import com.wms.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
            Long locationId, Long productId, String unitType, String lotNumber, LocalDate expiryDate);
}
