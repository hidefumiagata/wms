package com.wms.report.repository.projection;

/**
 * RPT-14: 配送リスト明細行用ネイティブクエリProjection。
 */
public interface DeliveryListLineRow {

    Long getOutboundSlipId();

    String getProductCode();

    String getProductName();

    String getUnitType();

    Integer getOrderedQty();
}
