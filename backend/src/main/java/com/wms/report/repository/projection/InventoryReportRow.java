package com.wms.report.repository.projection;

import java.time.LocalDate;

/**
 * RPT-07: 在庫一覧レポート用ネイティブクエリProjection。
 */
public interface InventoryReportRow {

    String getUnitType();

    String getLotNumber();

    LocalDate getExpiryDate();

    Integer getQuantity();

    Integer getAllocatedQty();

    String getLocationCode();

    String getBuildingName();

    String getAreaName();

    String getProductCode();

    String getProductName();
}
