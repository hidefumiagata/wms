package com.wms.report.repository;

import com.wms.inventory.entity.StocktakeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 棚卸系レポートのデータ取得用リポジトリ。
 * RPT-10（棚卸リスト）、RPT-11（棚卸結果レポート）用のネイティブクエリを提供する。
 */
public interface StocktakeReportRepository extends JpaRepository<StocktakeLine, Long> {

    /**
     * RPT-10: 棚卸リスト用データ取得（棚卸ID指定）。
     * stocktake_lines テーブルからロケーション順→商品コード順で取得する。
     */
    @Query(value = """
            SELECT sl.location_code, a.area_name, sl.product_code, sl.product_name,
                   sl.unit_type, sl.lot_number, sl.expiry_date,
                   sl.quantity_before, sl.quantity_counted
            FROM stocktake_lines sl
            JOIN stocktake_headers sh ON sl.stocktake_header_id = sh.id
            JOIN locations l ON sl.location_id = l.id
            JOIN areas a ON l.area_id = a.id
            WHERE sh.id = :stocktakeId
            ORDER BY sl.location_code ASC, sl.product_code ASC
            """, nativeQuery = true)
    List<Object[]> findStocktakeListByStocktakeId(@Param("stocktakeId") Long stocktakeId);

    /**
     * RPT-10: 棚卸リスト用データ取得（棟ID指定・プレビュー）。
     * inventory テーブルから現在在庫を取得し、actualQuantity=null で返す。
     */
    @Query(value = """
            SELECT l.location_code, a.area_name, p.product_code, p.product_name,
                   i.unit_type, i.lot_number, i.expiry_date,
                   i.quantity, CAST(NULL AS INTEGER) AS quantity_counted
            FROM inventories i
            JOIN locations l ON i.location_id = l.id
            JOIN areas a ON l.area_id = a.id
            JOIN buildings b ON a.building_id = b.id
            JOIN products p ON i.product_id = p.id
            WHERE b.id = :buildingId
              AND (:areaId IS NULL OR a.id = :areaId)
              AND i.quantity > 0
            ORDER BY l.location_code ASC, p.product_code ASC
            """, nativeQuery = true)
    List<Object[]> findStocktakeListByBuildingId(
            @Param("buildingId") Long buildingId,
            @Param("areaId") Long areaId);

    /**
     * RPT-11: 棚卸結果レポート用データ取得。
     * stocktake_lines からロケーション順→商品コード順で取得する。
     */
    @Query(value = """
            SELECT sl.location_code, sl.product_code, sl.product_name,
                   sl.unit_type, sl.lot_number,
                   sl.quantity_before, sl.quantity_counted
            FROM stocktake_lines sl
            WHERE sl.stocktake_header_id = :stocktakeId
            ORDER BY sl.location_code ASC, sl.product_code ASC
            """, nativeQuery = true)
    List<Object[]> findStocktakeResultByStocktakeId(@Param("stocktakeId") Long stocktakeId);
}
