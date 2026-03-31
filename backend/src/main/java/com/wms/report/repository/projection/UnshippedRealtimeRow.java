package com.wms.report.repository.projection;

import java.time.LocalDate;

/**
 * RPT-15: 未出荷リスト（リアルタイム）用ネイティブクエリProjection。
 */
public interface UnshippedRealtimeRow {

    String getSlipNumber();

    String getPartnerName();

    LocalDate getPlannedDate();

    String getProductCode();

    String getProductName();

    Integer getOrderedQty();

    String getStatus();
}
