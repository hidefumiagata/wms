package com.wms.report.repository.projection;

import java.time.LocalDate;

/**
 * RPT-10: 棚卸リスト用ネイティブクエリProjection。
 * 棚卸ID指定（実績）と棟ID指定（プレビュー）の両方で共通利用する。
 */
public interface StocktakeListRow {

    String getLocationCode();

    String getAreaName();

    String getProductCode();

    String getProductName();

    String getUnitType();

    String getLotNumber();

    LocalDate getExpiryDate();

    Integer getSystemQuantity();

    Integer getActualQuantity();
}
