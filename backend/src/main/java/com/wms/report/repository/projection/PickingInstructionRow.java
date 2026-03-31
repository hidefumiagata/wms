package com.wms.report.repository.projection;

import java.time.LocalDate;

/**
 * RPT-12: ピッキング指示書用ネイティブクエリProjection。
 */
public interface PickingInstructionRow {

    String getLocationCode();

    String getProductCode();

    String getProductName();

    String getUnitType();

    Integer getQtyToPick();

    String getSlipNumber();

    String getPartnerName();

    LocalDate getPlannedDate();

    String getLotNumber();
}
