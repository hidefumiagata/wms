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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.locationId = :locationId AND i.unitType = :unitType")
    int sumQuantityByLocationAndUnitType(@Param("locationId") Long locationId, @Param("unitType") String unitType);

    @Query("SELECT i FROM Inventory i WHERE i.warehouseId = :warehouseId AND i.productId = :productId AND (i.quantity - i.allocatedQty) > 0 ORDER BY i.expiryDate ASC NULLS LAST, i.id ASC")
    List<Inventory> findAvailableStock(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

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
