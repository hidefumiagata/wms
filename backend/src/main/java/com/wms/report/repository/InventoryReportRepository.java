package com.wms.report.repository;

import com.wms.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 在庫系レポートのデータ取得用リポジトリ。
 * RPT-07（在庫一覧）用のネイティブクエリを提供する。
 */
public interface InventoryReportRepository extends JpaRepository<Inventory, Long> {

    /**
     * RPT-07: 在庫一覧レポート用データ取得。
     * 在庫テーブルをロケーション・エリア・棟・商品と結合し、商品コード→ロケーションコード順でソートして返す。
     */
    @Query(value = """
            SELECT i.id, i.warehouse_id, i.location_id, i.product_id, i.unit_type,
                   i.lot_number, i.expiry_date, i.quantity, i.allocated_qty,
                   i.version, i.updated_at,
                   l.location_code, b.building_name, a.area_name,
                   p.product_code, p.product_name
            FROM inventories i
            JOIN locations l ON i.location_id = l.id
            JOIN areas a ON l.area_id = a.id
            JOIN buildings b ON a.building_id = b.id
            JOIN products p ON i.product_id = p.id
            WHERE i.warehouse_id = :warehouseId
              AND i.quantity > 0
              AND (:locationCodePrefix IS NULL OR l.location_code LIKE :locationCodePrefix || '%')
              AND (:productId IS NULL OR i.product_id = :productId)
              AND (:unitType IS NULL OR i.unit_type = :unitType)
              AND (:storageCondition IS NULL OR a.storage_condition = :storageCondition)
            ORDER BY p.product_code ASC, a.area_code ASC, l.location_code ASC
            """, nativeQuery = true)
    List<Object[]> findInventoryReportData(
            @Param("warehouseId") Long warehouseId,
            @Param("locationCodePrefix") String locationCodePrefix,
            @Param("productId") Long productId,
            @Param("unitType") String unitType,
            @Param("storageCondition") String storageCondition);
}
