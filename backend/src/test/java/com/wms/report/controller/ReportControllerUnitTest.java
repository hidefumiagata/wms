package com.wms.report.controller;

import com.wms.generated.model.InboundInspectionReportItem;
import com.wms.generated.model.InboundPlanReportItem;
import com.wms.generated.model.InboundResultReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnreceivedConfirmedReportItem;
import com.wms.generated.model.UnreceivedRealtimeReportItem;
import com.wms.report.service.InboundInspectionReportService;
import com.wms.report.service.InboundPlanReportService;
import com.wms.report.service.InboundResultReportService;
import com.wms.report.service.InventoryCorrectionReportService;
import com.wms.report.service.InventoryReportService;
import com.wms.report.service.InventoryTransitionReportService;
import com.wms.report.service.DeliveryListReportService;
import com.wms.report.service.PickingInstructionReportService;
import com.wms.report.service.ShippingInspectionReportService;
import com.wms.report.service.StocktakeListReportService;
import com.wms.report.service.StocktakeResultReportService;
import com.wms.report.service.UnreceivedConfirmedReportService;
import com.wms.report.service.UnreceivedRealtimeReportService;
import com.wms.report.service.UnshippedConfirmedReportService;
import com.wms.report.service.UnshippedRealtimeReportService;
import com.wms.report.service.DailySummaryReportService;
import com.wms.report.service.ReturnsReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportController 単体テスト")
class ReportControllerUnitTest {

    @Mock
    private InboundInspectionReportService inboundInspectionReportService;

    @Mock
    private InboundPlanReportService inboundPlanReportService;

    @Mock
    private InboundResultReportService inboundResultReportService;

    @Mock
    private UnreceivedRealtimeReportService unreceivedRealtimeReportService;

    @Mock
    private UnreceivedConfirmedReportService unreceivedConfirmedReportService;

    @Mock
    private InventoryReportService inventoryReportService;

    @Mock
    private InventoryTransitionReportService inventoryTransitionReportService;

    @Mock
    private InventoryCorrectionReportService inventoryCorrectionReportService;

    @Mock
    private StocktakeListReportService stocktakeListReportService;

    @Mock
    private StocktakeResultReportService stocktakeResultReportService;

    @Mock
    private PickingInstructionReportService pickingInstructionReportService;

    @Mock
    private ShippingInspectionReportService shippingInspectionReportService;

    @Mock
    private DeliveryListReportService deliveryListReportService;

    @Mock
    private UnshippedRealtimeReportService unshippedRealtimeReportService;

    @Mock
    private UnshippedConfirmedReportService unshippedConfirmedReportService;

    @Mock
    private DailySummaryReportService dailySummaryReportService;

    @Mock
    private ReturnsReportService returnsReportService;

    @InjectMocks
    private ReportController controller;

    @Test
    @DisplayName("RPT-01: format指定ありの場合はそのまま渡される")
    void getInboundInspectionReport_withFormat_passesFormat() {
        when(inboundInspectionReportService.generate(eq(1L), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));

        controller.getInboundInspectionReport(1L, ReportFormat.PDF);

        verify(inboundInspectionReportService).generate(1L, ReportFormat.PDF);
    }

    @Test
    @DisplayName("RPT-01: format未指定の場合はJSONがデフォルト")
    void getInboundInspectionReport_nullFormat_defaultsToJson() {
        when(inboundInspectionReportService.generate(eq(1L), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));

        controller.getInboundInspectionReport(1L, null);

        verify(inboundInspectionReportService).generate(1L, ReportFormat.JSON);
    }

    @Test
    @DisplayName("RPT-03: format指定ありの場合はそのまま渡される")
    void getInboundPlanReport_withFormat_passesFormat() {
        when(inboundPlanReportService.generate(any(), any(), any(), any(), any(), eq(ReportFormat.CSV)))
                .thenReturn(ResponseEntity.ok(List.of()));

        controller.getInboundPlanReport(1L, null, null, null, null, ReportFormat.CSV);

        verify(inboundPlanReportService).generate(eq(1L), any(), any(), any(), any(), eq(ReportFormat.CSV));
    }

    @Test
    @DisplayName("RPT-03: format未指定の場合はJSONがデフォルト")
    void getInboundPlanReport_nullFormat_defaultsToJson() {
        when(inboundPlanReportService.generate(any(), any(), any(), any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));

        controller.getInboundPlanReport(1L, null, null, null, null, null);

        verify(inboundPlanReportService).generate(eq(1L), any(), any(), any(), any(), eq(ReportFormat.JSON));
    }

    @Test
    @DisplayName("RPT-04: format指定ありの場合はそのまま渡される")
    void getInboundResultReport_withFormat_passesFormat() {
        when(inboundResultReportService.generate(any(), any(), any(), any(), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));

        controller.getInboundResultReport(1L, null, null, null, ReportFormat.PDF);

        verify(inboundResultReportService).generate(eq(1L), any(), any(), any(), eq(ReportFormat.PDF));
    }

    @Test
    @DisplayName("RPT-04: format未指定の場合はJSONがデフォルト")
    void getInboundResultReport_nullFormat_defaultsToJson() {
        when(inboundResultReportService.generate(any(), any(), any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));

        controller.getInboundResultReport(1L, null, null, null, null);

        verify(inboundResultReportService).generate(eq(1L), any(), any(), any(), eq(ReportFormat.JSON));
    }

    @Test
    @DisplayName("RPT-05: format指定ありの場合はそのまま渡される")
    void getUnreceivedRealtimeReport_withFormat_passesFormat() {
        when(unreceivedRealtimeReportService.generate(any(), any(), eq(ReportFormat.CSV)))
                .thenReturn(ResponseEntity.ok(List.of()));

        controller.getUnreceivedRealtimeReport(1L, null, ReportFormat.CSV);

        verify(unreceivedRealtimeReportService).generate(eq(1L), any(), eq(ReportFormat.CSV));
    }

    @Test
    @DisplayName("RPT-05: format未指定の場合はJSONがデフォルト")
    void getUnreceivedRealtimeReport_nullFormat_defaultsToJson() {
        when(unreceivedRealtimeReportService.generate(any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));

        controller.getUnreceivedRealtimeReport(1L, null, null);

        verify(unreceivedRealtimeReportService).generate(eq(1L), any(), eq(ReportFormat.JSON));
    }

    @Test
    @DisplayName("RPT-06: format指定ありの場合はそのまま渡される")
    void getUnreceivedConfirmedReport_withFormat_passesFormat() {
        when(unreceivedConfirmedReportService.generate(any(), any(), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));

        LocalDate batchDate = LocalDate.of(2026, 3, 14);
        controller.getUnreceivedConfirmedReport(1L, batchDate, ReportFormat.PDF);

        verify(unreceivedConfirmedReportService).generate(eq(1L), eq(batchDate), eq(ReportFormat.PDF));
    }

    @Test
    @DisplayName("RPT-06: format未指定の場合はJSONがデフォルト")
    void getUnreceivedConfirmedReport_nullFormat_defaultsToJson() {
        when(unreceivedConfirmedReportService.generate(any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));

        LocalDate batchDate = LocalDate.of(2026, 3, 14);
        controller.getUnreceivedConfirmedReport(1L, batchDate, null);

        verify(unreceivedConfirmedReportService).generate(eq(1L), eq(batchDate), eq(ReportFormat.JSON));
    }

    // --- RPT-07 ---
    @Test
    @DisplayName("RPT-07: format指定ありの場合はそのまま渡される")
    void getInventoryReport_withFormat_passesFormat() {
        when(inventoryReportService.generate(any(), any(), any(), any(), any(), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getInventoryReport(1L, null, null, null, null, ReportFormat.PDF);
        verify(inventoryReportService).generate(any(), any(), any(), any(), any(), eq(ReportFormat.PDF));
    }

    @Test
    @DisplayName("RPT-07: format未指定の場合はJSONがデフォルト")
    void getInventoryReport_nullFormat_defaultsToJson() {
        when(inventoryReportService.generate(any(), any(), any(), any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getInventoryReport(1L, null, null, null, null, null);
        verify(inventoryReportService).generate(any(), any(), any(), any(), any(), eq(ReportFormat.JSON));
    }

    // --- RPT-08 ---
    @Test
    @DisplayName("RPT-08: format指定ありの場合はそのまま渡される")
    void getInventoryTransitionReport_withFormat_passesFormat() {
        when(inventoryTransitionReportService.generate(any(), any(), any(), any(), eq(ReportFormat.CSV)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getInventoryTransitionReport(1L, 100L, null, null, ReportFormat.CSV);
        verify(inventoryTransitionReportService).generate(any(), any(), any(), any(), eq(ReportFormat.CSV));
    }

    @Test
    @DisplayName("RPT-08: format未指定の場合はJSONがデフォルト")
    void getInventoryTransitionReport_nullFormat_defaultsToJson() {
        when(inventoryTransitionReportService.generate(any(), any(), any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getInventoryTransitionReport(1L, 100L, null, null, null);
        verify(inventoryTransitionReportService).generate(any(), any(), any(), any(), eq(ReportFormat.JSON));
    }

    // --- RPT-09 ---
    @Test
    @DisplayName("RPT-09: format指定ありの場合はそのまま渡される")
    void getInventoryCorrectionReport_withFormat_passesFormat() {
        when(inventoryCorrectionReportService.generate(any(), any(), any(), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getInventoryCorrectionReport(1L, null, null, ReportFormat.PDF);
        verify(inventoryCorrectionReportService).generate(any(), any(), any(), eq(ReportFormat.PDF));
    }

    @Test
    @DisplayName("RPT-09: format未指定の場合はJSONがデフォルト")
    void getInventoryCorrectionReport_nullFormat_defaultsToJson() {
        when(inventoryCorrectionReportService.generate(any(), any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getInventoryCorrectionReport(1L, null, null, null);
        verify(inventoryCorrectionReportService).generate(any(), any(), any(), eq(ReportFormat.JSON));
    }

    // --- RPT-10 ---
    @Test
    @DisplayName("RPT-10: format指定ありの場合はそのまま渡される")
    void getStocktakeListReport_withFormat_passesFormat() {
        when(stocktakeListReportService.generate(any(), any(), any(), any(), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getStocktakeListReport(10L, null, null, null, ReportFormat.PDF);
        verify(stocktakeListReportService).generate(eq(10L), any(), any(), any(), eq(ReportFormat.PDF));
    }

    @Test
    @DisplayName("RPT-10: format未指定の場合はJSONがデフォルト")
    void getStocktakeListReport_nullFormat_defaultsToJson() {
        when(stocktakeListReportService.generate(any(), any(), any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getStocktakeListReport(10L, null, null, null, null);
        verify(stocktakeListReportService).generate(eq(10L), any(), any(), any(), eq(ReportFormat.JSON));
    }

    // --- RPT-11 ---
    @Test
    @DisplayName("RPT-11: format指定ありの場合はそのまま渡される")
    void getStocktakeResultReport_withFormat_passesFormat() {
        when(stocktakeResultReportService.generate(any(), eq(ReportFormat.CSV)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getStocktakeResultReport(10L, ReportFormat.CSV);
        verify(stocktakeResultReportService).generate(eq(10L), eq(ReportFormat.CSV));
    }

    @Test
    @DisplayName("RPT-11: format未指定の場合はJSONがデフォルト")
    void getStocktakeResultReport_nullFormat_defaultsToJson() {
        when(stocktakeResultReportService.generate(any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getStocktakeResultReport(10L, null);
        verify(stocktakeResultReportService).generate(eq(10L), eq(ReportFormat.JSON));
    }

    // --- RPT-12 ---
    @Test
    @DisplayName("RPT-12: format指定ありの場合はそのまま渡される")
    void getPickingInstructionReport_withFormat_passesFormat() {
        when(pickingInstructionReportService.generate(eq(1L), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getPickingInstructionReport(1L, ReportFormat.PDF);
        verify(pickingInstructionReportService).generate(1L, ReportFormat.PDF);
    }

    @Test
    @DisplayName("RPT-12: format未指定の場合はJSONがデフォルト")
    void getPickingInstructionReport_nullFormat_defaultsToJson() {
        when(pickingInstructionReportService.generate(eq(1L), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getPickingInstructionReport(1L, null);
        verify(pickingInstructionReportService).generate(1L, ReportFormat.JSON);
    }

    // --- RPT-13 ---
    @Test
    @DisplayName("RPT-13: format指定ありの場合はそのまま渡される")
    void getShippingInspectionReport_withFormat_passesFormat() {
        when(shippingInspectionReportService.generate(eq(1L), eq(ReportFormat.CSV)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getShippingInspectionReport(1L, ReportFormat.CSV);
        verify(shippingInspectionReportService).generate(1L, ReportFormat.CSV);
    }

    @Test
    @DisplayName("RPT-13: format未指定の場合はJSONがデフォルト")
    void getShippingInspectionReport_nullFormat_defaultsToJson() {
        when(shippingInspectionReportService.generate(eq(1L), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getShippingInspectionReport(1L, null);
        verify(shippingInspectionReportService).generate(1L, ReportFormat.JSON);
    }

    // --- RPT-14 ---
    @Test
    @DisplayName("RPT-14: format指定ありの場合はそのまま渡される")
    void getDeliveryListReport_withFormat_passesFormat() {
        when(deliveryListReportService.generate(any(), any(), any(), any(), any(), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getDeliveryListReport(1L, null, null, null, null, ReportFormat.PDF);
        verify(deliveryListReportService).generate(eq(1L), any(), any(), any(), any(), eq(ReportFormat.PDF));
    }

    @Test
    @DisplayName("RPT-14: format未指定の場合はJSONがデフォルト")
    void getDeliveryListReport_nullFormat_defaultsToJson() {
        when(deliveryListReportService.generate(any(), any(), any(), any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getDeliveryListReport(1L, null, null, null, null, null);
        verify(deliveryListReportService).generate(eq(1L), any(), any(), any(), any(), eq(ReportFormat.JSON));
    }

    // --- RPT-15 ---
    @Test
    @DisplayName("RPT-15: format指定ありの場合はそのまま渡される")
    void getUnshippedRealtimeReport_withFormat_passesFormat() {
        when(unshippedRealtimeReportService.generate(any(), any(), eq(ReportFormat.CSV)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getUnshippedRealtimeReport(1L, null, ReportFormat.CSV);
        verify(unshippedRealtimeReportService).generate(eq(1L), any(), eq(ReportFormat.CSV));
    }

    @Test
    @DisplayName("RPT-15: format未指定の場合はJSONがデフォルト")
    void getUnshippedRealtimeReport_nullFormat_defaultsToJson() {
        when(unshippedRealtimeReportService.generate(any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getUnshippedRealtimeReport(1L, null, null);
        verify(unshippedRealtimeReportService).generate(eq(1L), any(), eq(ReportFormat.JSON));
    }

    // --- RPT-16 ---
    @Test
    @DisplayName("RPT-16: format指定ありの場合はそのまま渡される")
    void getUnshippedConfirmedReport_withFormat_passesFormat() {
        when(unshippedConfirmedReportService.generate(any(), any(), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));
        LocalDate batchDate = LocalDate.of(2026, 3, 14);
        controller.getUnshippedConfirmedReport(1L, batchDate, ReportFormat.PDF);
        verify(unshippedConfirmedReportService).generate(eq(1L), eq(batchDate), eq(ReportFormat.PDF));
    }

    @Test
    @DisplayName("RPT-16: format未指定の場合はJSONがデフォルト")
    void getUnshippedConfirmedReport_nullFormat_defaultsToJson() {
        when(unshippedConfirmedReportService.generate(any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        LocalDate batchDate = LocalDate.of(2026, 3, 14);
        controller.getUnshippedConfirmedReport(1L, batchDate, null);
        verify(unshippedConfirmedReportService).generate(eq(1L), eq(batchDate), eq(ReportFormat.JSON));
    }

    // --- RPT-17 ---
    @Test
    @DisplayName("RPT-17: format指定ありの場合はそのまま渡される")
    void getDailySummaryReport_withFormat_passesFormat() {
        LocalDate targetDate = LocalDate.of(2026, 3, 14);
        when(dailySummaryReportService.generate(eq(targetDate), eq(ReportFormat.PDF)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getDailySummaryReport(targetDate, ReportFormat.PDF);
        verify(dailySummaryReportService).generate(targetDate, ReportFormat.PDF);
    }

    @Test
    @DisplayName("RPT-17: format未指定の場合はJSONがデフォルト")
    void getDailySummaryReport_nullFormat_defaultsToJson() {
        LocalDate targetDate = LocalDate.of(2026, 3, 14);
        when(dailySummaryReportService.generate(eq(targetDate), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getDailySummaryReport(targetDate, null);
        verify(dailySummaryReportService).generate(targetDate, ReportFormat.JSON);
    }

    // --- RPT-18 ---
    @Test
    @DisplayName("RPT-18: format指定ありの場合はそのまま渡される")
    void getReturnsReport_withFormat_passesFormat() {
        when(returnsReportService.generate(any(), any(), any(), any(), any(), any(), any(), eq(ReportFormat.CSV)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getReturnsReport(1L, null, null, null, null, null, null, ReportFormat.CSV);
        verify(returnsReportService).generate(eq(1L), any(), any(), any(), any(), any(), any(), eq(ReportFormat.CSV));
    }

    @Test
    @DisplayName("RPT-18: format未指定の場合はJSONがデフォルト")
    void getReturnsReport_nullFormat_defaultsToJson() {
        when(returnsReportService.generate(any(), any(), any(), any(), any(), any(), any(), eq(ReportFormat.JSON)))
                .thenReturn(ResponseEntity.ok(List.of()));
        controller.getReturnsReport(1L, null, null, null, null, null, null, null);
        verify(returnsReportService).generate(eq(1L), any(), any(), any(), any(), any(), any(), eq(ReportFormat.JSON));
    }
}
