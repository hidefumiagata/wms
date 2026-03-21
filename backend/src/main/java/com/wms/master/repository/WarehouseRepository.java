package com.wms.master.repository;

import com.wms.master.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByWarehouseCode(String warehouseCode);

    @Query("SELECT w FROM Warehouse w WHERE "
            + "(:warehouseCode IS NULL OR w.warehouseCode LIKE :warehouseCode%) "
            + "AND (:warehouseName IS NULL OR w.warehouseName LIKE %:warehouseName%) "
            + "AND (:isActive IS NULL OR w.isActive = :isActive)")
    Page<Warehouse> search(
            @Param("warehouseCode") String warehouseCode,
            @Param("warehouseName") String warehouseName,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    @Query("SELECT w FROM Warehouse w WHERE (:isActive IS NULL OR w.isActive = :isActive) ORDER BY w.warehouseCode ASC")
    List<Warehouse> findAllSimple(@Param("isActive") Boolean isActive);
}
