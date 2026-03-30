package com.wms.report.repository.projection;

/**
 * RPT-11: 棚卸結果レポート用ネイティブクエリProjection。
 */
public interface StocktakeResultRow {

    String getLocationCode();

    String getProductCode();

    String getProductName();

    String getUnitType();

    String getLotNumber();

    Integer getQuantityBefore();

    Integer getQuantityCounted();
}
