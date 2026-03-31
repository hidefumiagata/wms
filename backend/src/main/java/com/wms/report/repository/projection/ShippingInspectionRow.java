package com.wms.report.repository.projection;

import java.time.LocalDate;

/**
 * RPT-13: 出荷検品レポート用ネイティブクエリProjection。
 */
public interface ShippingInspectionRow {

    String getSlipNumber();

    String getPartnerName();

    LocalDate getPlannedDate();

    String getProductCode();

    String getProductName();

    String getUnitType();

    Integer getPickedQty();

    Integer getInspectedQty();
}
