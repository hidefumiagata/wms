package com.wms.master.repository;

import com.wms.master.entity.Area;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AreaRepository extends JpaRepository<Area, Long> {

    boolean existsByBuildingIdAndAreaCode(Long buildingId, String areaCode);

    long countByBuildingIdAndIsActiveTrue(Long buildingId);

    long countByBuildingId(Long buildingId);


    @Query("SELECT a FROM Area a WHERE "
            + "(:warehouseId IS NULL OR a.warehouseId = :warehouseId) "
            + "AND (:buildingId IS NULL OR a.buildingId = :buildingId) "
            + "AND (:areaType IS NULL OR a.areaType = :areaType) "
            + "AND (:storageCondition IS NULL OR a.storageCondition = :storageCondition) "
            + "AND (:isActive IS NULL OR a.isActive = :isActive)")
    Page<Area> search(
            @Param("warehouseId") Long warehouseId,
            @Param("buildingId") Long buildingId,
            @Param("areaType") String areaType,
            @Param("storageCondition") String storageCondition,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    long countByAreaTypeAndBuildingId(String areaType, Long buildingId);
}
