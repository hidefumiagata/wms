package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.ShippingInspectionReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.report.repository.OutboundReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-13: 出荷検品レポートサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ShippingInspectionReportService {

    private final OutboundReportRepository outboundReportRepository;
    private final OutboundSlipRepository outboundSlipRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    // --- ネイティブクエリのカラムインデックス定数 ---
    private static final int COL_SLIP_NUMBER = 0;
    private static final int COL_PARTNER_NAME = 1;
    private static final int COL_PLANNED_DATE = 2;
    private static final int COL_PRODUCT_CODE = 3;
    private static final int COL_PRODUCT_NAME = 4;
    private static final int COL_UNIT_TYPE = 5;
    private static final int COL_ORDERED_QTY = 6;
    private static final int COL_INSPECTED_QTY = 7;

    private static final String[] CSV_HEADERS = {
            "伝票番号", "出荷先名", "出荷予定日", "商品コード", "商品名",
            "荷姿", "ピッキング数", "検品数", "差異数"
    };

    public ResponseEntity<List<ShippingInspectionReportItem>> generate(
            Long slipId, ReportFormat format) {

        log.info("RPT-13 出荷検品レポート生成開始: slipId={}, format={}", slipId, format);

        OutboundSlip slip = outboundSlipRepository.findById(slipId)
                .orElseThrow(() -> new ResourceNotFoundException("OUTBOUND_SLIP_NOT_FOUND",
                        "出荷伝票が見つかりません: slipId=" + slipId));

        Warehouse warehouse = warehouseRepository.findById(slip.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + slip.getWarehouseId()));

        String warehouseName = warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")";

        List<Object[]> rows = outboundReportRepository.findShippingInspectionReportData(slipId);

        List<ShippingInspectionReportItem> items = rows.stream()
                .map(this::toReportItem)
                .toList();

        Map<String, Object> extraVars = new HashMap<>();
        extraVars.put("slipNumber", slip.getSlipNumber());
        extraVars.put("customerName", slip.getPartnerName());
        extraVars.put("plannedShipDate", slip.getPlannedDate());

        ReportMeta meta = new ReportMeta(
                "出荷検品レポート",
                "rpt-13-shipping-inspection",
                "shipping_inspection_" + todayFileDate(),
                warehouseName,
                getCurrentUserName(),
                "伝票No: " + slip.getSlipNumber(),
                CSV_HEADERS,
                row -> csvRowMapper((ShippingInspectionReportItem) row),
                extraVars
        );

        log.info("RPT-13 出荷検品レポート生成完了: slipId={}, 件数={}", slipId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private ShippingInspectionReportItem toReportItem(Object[] row) {
        ShippingInspectionReportItem item = new ShippingInspectionReportItem();
        item.setSlipNumber((String) row[COL_SLIP_NUMBER]);
        item.setCustomerName((String) row[COL_PARTNER_NAME]);
        item.setPlannedShipDate(row[COL_PLANNED_DATE] != null
                ? ((java.sql.Date) row[COL_PLANNED_DATE]).toLocalDate() : null);
        item.setProductCode((String) row[COL_PRODUCT_CODE]);
        item.setProductName((String) row[COL_PRODUCT_NAME]);
        item.setUnitType((String) row[COL_UNIT_TYPE]);

        Integer pickedQty = row[COL_ORDERED_QTY] != null
                ? ((Number) row[COL_ORDERED_QTY]).intValue() : 0;
        Integer inspectedQty = row[COL_INSPECTED_QTY] != null
                ? ((Number) row[COL_INSPECTED_QTY]).intValue() : null;

        item.setPickedQuantity(pickedQty);
        item.setInspectedQuantity(inspectedQty);
        item.setDiffQuantity(inspectedQty != null ? inspectedQty - pickedQty : null);
        return item;
    }

    private String[] csvRowMapper(ShippingInspectionReportItem item) {
        return new String[]{
                item.getSlipNumber(),
                fmtOrDash(item.getCustomerName()),
                fmtDate(item.getPlannedShipDate()),
                item.getProductCode(),
                item.getProductName(),
                fmtOrDash(item.getUnitType()),
                fmtInteger(item.getPickedQuantity()),
                fmtInteger(item.getInspectedQuantity()),
                fmtInteger(item.getDiffQuantity())
        };
    }
}
