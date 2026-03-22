package com.wms.master.repository;

import com.wms.master.entity.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    boolean existsByWarehouseIdAndBuildingCode(Long warehouseId, String buildingCode);
    @Query("SELECT b FROM Building b WHERE "
            + "(:warehouseId IS NULL OR b.warehouseId = :warehouseId) "
            + "AND (:buildingCode IS NULL OR b.buildingCode LIKE CONCAT(:buildingCode, '%') ESCAPE '\\') "
            + "AND (:buildingName IS NULL OR b.buildingName LIKE CONCAT('%', :buildingName, '%') ESCAPE '\\') "
            + "AND (:isActive IS NULL OR b.isActive = :isActive)")
    Page<Building> search(
            @Param("warehouseId") Long warehouseId,
            @Param("buildingCode") String buildingCode,
            @Param("buildingName") String buildingName,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    long countByWarehouseIdAndBuildingCodeNotAndIsActiveTrue(Long warehouseId, String buildingCode);

    long countByWarehouseId(Long warehouseId);
}
