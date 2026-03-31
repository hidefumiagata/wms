package com.wms.report.repository.projection;

import java.time.LocalDate;

/**
 * RPT-17: 日次集計レポート用ネイティブクエリProjection。
 */
public interface DailySummaryReportRow {

    LocalDate getBusinessDate();

    Long getWarehouseId();

    String getWarehouseName();

    Integer getInboundCount();

    Integer getInboundLineCount();

    Integer getInboundQuantityTotal();

    Integer getOutboundCount();

    Integer getOutboundLineCount();

    Integer getOutboundQuantityTotal();

    Integer getReturnCount();

    Integer getReturnQuantityTotal();

    Integer getInventoryQuantityTotal();

    Integer getUnreceivedCount();

    Integer getUnshippedCount();
}
