package com.wms.report.controller;

import com.wms.generated.api.ReportApi;
import com.wms.generated.model.*;
import com.wms.report.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * レポート API コントローラー。
 * Phase 1 では全メソッドが未実装（UnsupportedOperationException）。
 * Phase 2 以降、個別レポートサービスを注入して順次実装する。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER','WAREHOUSE_STAFF','VIEWER')")
public class ReportController implements ReportApi {

    private final ReportExportService reportExportService;

    // --- RPT-01: 入荷検品レポート ---
    @Override
    public ResponseEntity<List<InboundInspectionReportItem>> getInboundInspectionReport(
            Long slipId, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-01 not yet implemented");
    }

    // --- RPT-03: 入荷予定レポート ---
    @Override
    public ResponseEntity<List<InboundPlanReportItem>> getInboundPlanReport(
            Long warehouseId, LocalDate plannedDateFrom, LocalDate plannedDateTo,
            String status, Long partnerId, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-03 not yet implemented");
    }

    // --- RPT-04: 入庫実績レポート ---
    @Override
    public ResponseEntity<List<InboundResultReportItem>> getInboundResultReport(
            Long warehouseId, LocalDate storedDateFrom, LocalDate storedDateTo,
            Long partnerId, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-04 not yet implemented");
    }

    // --- RPT-05: 未入荷リスト（リアルタイム） ---
    @Override
    public ResponseEntity<List<UnreceivedRealtimeReportItem>> getUnreceivedRealtimeReport(
            Long warehouseId, LocalDate asOfDate, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-05 not yet implemented");
    }

    // --- RPT-06: 未入荷リスト（確定） ---
    @Override
    public ResponseEntity<List<UnreceivedConfirmedReportItem>> getUnreceivedConfirmedReport(
            Long warehouseId, LocalDate batchBusinessDate, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-06 not yet implemented");
    }

    // --- RPT-07: 在庫一覧レポート ---
    @Override
    public ResponseEntity<List<InventoryReportItem>> getInventoryReport(
            Long warehouseId, String locationCodePrefix, Long productId,
            UnitType unitType, StorageCondition storageCondition, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-07 not yet implemented");
    }

    // --- RPT-08: 在庫推移レポート ---
    @Override
    public ResponseEntity<List<InventoryTransitionReportItem>> getInventoryTransitionReport(
            Long warehouseId, Long productId, LocalDate dateFrom,
            LocalDate dateTo, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-08 not yet implemented");
    }

    // --- RPT-09: 在庫訂正一覧 ---
    @Override
    public ResponseEntity<List<InventoryCorrectionReportItem>> getInventoryCorrectionReport(
            Long warehouseId, LocalDate correctionDateFrom,
            LocalDate correctionDateTo, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-09 not yet implemented");
    }

    // --- RPT-10: 棚卸リスト ---
    @Override
    public ResponseEntity<List<StocktakeListReportItem>> getStocktakeListReport(
            Long stocktakeId, Long buildingId, Long areaId,
            Boolean hideBookQty, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-10 not yet implemented");
    }

    // --- RPT-11: 棚卸結果レポート ---
    @Override
    public ResponseEntity<List<StocktakeResultReportItem>> getStocktakeResultReport(
            Long stocktakeId, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-11 not yet implemented");
    }

    // --- RPT-12: ピッキング指示書 ---
    @Override
    public ResponseEntity<List<PickingInstructionReportItem>> getPickingInstructionReport(
            Long pickingInstructionId, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-12 not yet implemented");
    }

    // --- RPT-13: 出荷検品レポート ---
    @Override
    public ResponseEntity<List<ShippingInspectionReportItem>> getShippingInspectionReport(
            Long slipId, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-13 not yet implemented");
    }

    // --- RPT-14: 配送リスト ---
    @Override
    public ResponseEntity<List<DeliveryListReportItem>> getDeliveryListReport(
            Long warehouseId, LocalDate plannedDateFrom, LocalDate plannedDateTo,
            String status, String carrier, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-14 not yet implemented");
    }

    // --- RPT-15: 未出荷リスト（リアルタイム） ---
    @Override
    public ResponseEntity<List<UnshippedRealtimeReportItem>> getUnshippedRealtimeReport(
            Long warehouseId, LocalDate asOfDate, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-15 not yet implemented");
    }

    // --- RPT-16: 未出荷リスト（確定） ---
    @Override
    public ResponseEntity<List<UnshippedConfirmedReportItem>> getUnshippedConfirmedReport(
            Long warehouseId, LocalDate batchBusinessDate, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-16 not yet implemented");
    }

    // --- RPT-17: 日次集計レポート ---
    @Override
    public ResponseEntity<List<DailySummaryReportItem>> getDailySummaryReport(
            LocalDate targetBusinessDate, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-17 not yet implemented");
    }

    // --- RPT-18: 返品レポート ---
    @Override
    public ResponseEntity<List<ReturnsReportItem>> getReturnsReport(
            Long warehouseId, ReturnType returnType, LocalDate returnDateFrom,
            LocalDate returnDateTo, Long productId, Long partnerId,
            ReturnReason returnReason, ReportFormat format) {
        throw new UnsupportedOperationException("RPT-18 not yet implemented");
    }
}
