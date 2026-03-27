package com.wms.report.controller;

import com.wms.generated.api.ReportApi;
import com.wms.generated.model.DailySummaryReportItem;
import com.wms.generated.model.DeliveryListReportItem;
import com.wms.generated.model.InboundInspectionReportItem;
import com.wms.generated.model.InboundPlanReportItem;
import com.wms.generated.model.InboundResultReportItem;
import com.wms.generated.model.InventoryCorrectionReportItem;
import com.wms.generated.model.InventoryReportItem;
import com.wms.generated.model.InventoryTransitionReportItem;
import com.wms.generated.model.PickingInstructionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.ReturnReason;
import com.wms.generated.model.ReturnType;
import com.wms.generated.model.ReturnsReportItem;
import com.wms.generated.model.ShippingInspectionReportItem;
import com.wms.generated.model.StocktakeListReportItem;
import com.wms.generated.model.StocktakeResultReportItem;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.UnitType;
import com.wms.generated.model.UnreceivedConfirmedReportItem;
import com.wms.generated.model.UnreceivedRealtimeReportItem;
import com.wms.generated.model.UnshippedConfirmedReportItem;
import com.wms.generated.model.UnshippedRealtimeReportItem;
import com.wms.report.service.InboundInspectionReportService;
import com.wms.report.service.InboundPlanReportService;
import com.wms.report.service.InboundResultReportService;
import com.wms.report.service.InventoryCorrectionReportService;
import com.wms.report.service.InventoryReportService;
import com.wms.report.service.InventoryTransitionReportService;
import com.wms.report.service.UnreceivedConfirmedReportService;
import com.wms.report.service.StocktakeListReportService;
import com.wms.report.service.StocktakeResultReportService;
import com.wms.report.service.UnreceivedRealtimeReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * レポート API コントローラー。
 * 入荷系帳票（RPT-01, RPT-03, RPT-04, RPT-05, RPT-06）および
 * 在庫系帳票（RPT-07, RPT-08, RPT-09）および
 * 棚卸系帳票（RPT-10, RPT-11）は本格実装済み。
 * その他のレポートは後続Issueで順次実装する。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER','WAREHOUSE_STAFF','VIEWER')")
public class ReportController implements ReportApi {

    private final InboundInspectionReportService inboundInspectionReportService;
    private final InboundPlanReportService inboundPlanReportService;
    private final InboundResultReportService inboundResultReportService;
    private final UnreceivedRealtimeReportService unreceivedRealtimeReportService;
    private final UnreceivedConfirmedReportService unreceivedConfirmedReportService;
    private final InventoryReportService inventoryReportService;
    private final InventoryTransitionReportService inventoryTransitionReportService;
    private final InventoryCorrectionReportService inventoryCorrectionReportService;
    private final StocktakeListReportService stocktakeListReportService;
    private final StocktakeResultReportService stocktakeResultReportService;

    private static ReportFormat defaultFormat(ReportFormat format) {
        return format != null ? format : ReportFormat.JSON;
    }

    // --- RPT-01: 入荷検品レポート ---
    @Override
    public ResponseEntity<List<InboundInspectionReportItem>> getInboundInspectionReport(
            Long slipId, ReportFormat format) {
        return inboundInspectionReportService.generate(slipId, defaultFormat(format));
    }

    // --- RPT-03: 入荷予定レポート ---
    @Override
    public ResponseEntity<List<InboundPlanReportItem>> getInboundPlanReport(
            Long warehouseId, LocalDate plannedDateFrom, LocalDate plannedDateTo,
            String status, Long partnerId, ReportFormat format) {
        return inboundPlanReportService.generate(
                warehouseId, plannedDateFrom, plannedDateTo, status, partnerId, defaultFormat(format));
    }

    // --- RPT-04: 入庫実績レポート ---
    @Override
    public ResponseEntity<List<InboundResultReportItem>> getInboundResultReport(
            Long warehouseId, LocalDate storedDateFrom, LocalDate storedDateTo,
            Long partnerId, ReportFormat format) {
        return inboundResultReportService.generate(
                warehouseId, storedDateFrom, storedDateTo, partnerId, defaultFormat(format));
    }

    // --- RPT-05: 未入荷リスト（リアルタイム） ---
    @Override
    public ResponseEntity<List<UnreceivedRealtimeReportItem>> getUnreceivedRealtimeReport(
            Long warehouseId, LocalDate asOfDate, ReportFormat format) {
        return unreceivedRealtimeReportService.generate(warehouseId, asOfDate, defaultFormat(format));
    }

    // --- RPT-06: 未入荷リスト（確定） ---
    @Override
    public ResponseEntity<List<UnreceivedConfirmedReportItem>> getUnreceivedConfirmedReport(
            Long warehouseId, LocalDate batchBusinessDate, ReportFormat format) {
        return unreceivedConfirmedReportService.generate(warehouseId, batchBusinessDate, defaultFormat(format));
    }

    // --- RPT-07: 在庫一覧レポート ---
    @Override
    public ResponseEntity<List<InventoryReportItem>> getInventoryReport(
            Long warehouseId, String locationCodePrefix, Long productId,
            UnitType unitType, StorageCondition storageCondition, ReportFormat format) {
        return inventoryReportService.generate(
                warehouseId, locationCodePrefix, productId, unitType, storageCondition, defaultFormat(format));
    }

    // --- RPT-08: 在庫推移レポート ---
    @Override
    public ResponseEntity<List<InventoryTransitionReportItem>> getInventoryTransitionReport(
            Long warehouseId, Long productId, LocalDate dateFrom,
            LocalDate dateTo, ReportFormat format) {
        return inventoryTransitionReportService.generate(
                warehouseId, productId, dateFrom, dateTo, defaultFormat(format));
    }

    // --- RPT-09: 在庫訂正一覧 ---
    @Override
    public ResponseEntity<List<InventoryCorrectionReportItem>> getInventoryCorrectionReport(
            Long warehouseId, LocalDate correctionDateFrom,
            LocalDate correctionDateTo, ReportFormat format) {
        return inventoryCorrectionReportService.generate(
                warehouseId, correctionDateFrom, correctionDateTo, defaultFormat(format));
    }

    // --- RPT-10: 棚卸リスト ---
    @Override
    public ResponseEntity<List<StocktakeListReportItem>> getStocktakeListReport(
            Long stocktakeId, Long buildingId, Long areaId,
            Boolean hideBookQty, ReportFormat format) {
        return stocktakeListReportService.generate(
                stocktakeId, buildingId, areaId, hideBookQty, defaultFormat(format));
    }

    // --- RPT-11: 棚卸結果レポート ---
    @Override
    public ResponseEntity<List<StocktakeResultReportItem>> getStocktakeResultReport(
            Long stocktakeId, ReportFormat format) {
        return stocktakeResultReportService.generate(stocktakeId, defaultFormat(format));
    }

    // --- RPT-12: ピッキング指示書 ---
    @Override
    public ResponseEntity<List<PickingInstructionReportItem>> getPickingInstructionReport(
            Long pickingInstructionId, ReportFormat format) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // --- RPT-13: 出荷検品レポート ---
    @Override
    public ResponseEntity<List<ShippingInspectionReportItem>> getShippingInspectionReport(
            Long slipId, ReportFormat format) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // --- RPT-14: 配送リスト ---
    @Override
    public ResponseEntity<List<DeliveryListReportItem>> getDeliveryListReport(
            Long warehouseId, LocalDate plannedDateFrom, LocalDate plannedDateTo,
            String status, String carrier, ReportFormat format) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // --- RPT-15: 未出荷リスト（リアルタイム） ---
    @Override
    public ResponseEntity<List<UnshippedRealtimeReportItem>> getUnshippedRealtimeReport(
            Long warehouseId, LocalDate asOfDate, ReportFormat format) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // --- RPT-16: 未出荷リスト（確定） ---
    @Override
    public ResponseEntity<List<UnshippedConfirmedReportItem>> getUnshippedConfirmedReport(
            Long warehouseId, LocalDate batchBusinessDate, ReportFormat format) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // --- RPT-17: 日次集計レポート ---
    @Override
    public ResponseEntity<List<DailySummaryReportItem>> getDailySummaryReport(
            LocalDate targetBusinessDate, ReportFormat format) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // --- RPT-18: 返品レポート ---
    @Override
    public ResponseEntity<List<ReturnsReportItem>> getReturnsReport(
            Long warehouseId, ReturnType returnType, LocalDate returnDateFrom,
            LocalDate returnDateTo, Long productId, Long partnerId,
            ReturnReason returnReason, ReportFormat format) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
