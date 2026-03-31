package com.wms.report.repository.projection;

import java.time.LocalDate;

/**
 * RPT-14: 配送リストヘッダー用ネイティブクエリProjection。
 */
public interface DeliveryListHeaderRow {

    Long getId();

    String getSlipNumber();

    String getPartnerName();

    LocalDate getPlannedDate();

    String getStatus();

    String getCarrier();

    String getTrackingNumber();

    String getAddress();
}
