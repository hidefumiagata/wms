package com.wms.report.repository.projection;

import java.time.LocalDate;

/**
 * RPT-18: 返品レポート用ネイティブクエリProjection。
 */
public interface ReturnsReportRow {

    String getReturnNumber();

    String getReturnType();

    LocalDate getReturnDate();

    String getProductCode();

    String getProductName();

    Integer getQuantity();

    String getUnitType();

    String getReturnReason();

    String getReturnReasonNote();

    String getRelatedSlipNumber();

    String getPartnerName();
}
