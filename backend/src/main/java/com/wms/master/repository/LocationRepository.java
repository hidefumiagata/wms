package com.wms.master.repository;

import com.wms.master.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, Long> {

    boolean existsByWarehouseIdAndLocationCode(Long warehouseId, String locationCode);

    long countByAreaId(Long areaId);

    long countByAreaIdAndIsActiveTrue(Long areaId);

    // TODO: #72 パターン — LIKE 検索のワイルドカード（%/_）エスケープ対応を検討
    @Query("SELECT l FROM Location l WHERE "
            + "(:warehouseId IS NULL OR l.warehouseId = :warehouseId) "
            + "AND (:areaId IS NULL OR l.areaId = :areaId) "
            + "AND (:codePrefix IS NULL OR l.locationCode LIKE CONCAT(:codePrefix, '%')) "
            + "AND (:isActive IS NULL OR l.isActive = :isActive)")
    Page<Location> search(
            @Param("warehouseId") Long warehouseId,
            @Param("areaId") Long areaId,
            @Param("codePrefix") String codePrefix,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    @Query("SELECT COUNT(l) FROM Location l WHERE "
            + "(:warehouseId IS NULL OR l.warehouseId = :warehouseId) "
            + "AND (:areaId IS NULL OR l.areaId = :areaId) "
            + "AND (:isActive IS NULL OR l.isActive = :isActive)")
    long countFiltered(
            @Param("warehouseId") Long warehouseId,
            @Param("areaId") Long areaId,
            @Param("isActive") Boolean isActive);
}
