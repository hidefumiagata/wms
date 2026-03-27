package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnshippedRealtimeReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.OutboundReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.util.BusinessDateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.OUTBOUND_STATUS_LABELS;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-15: 未出荷リスト（リアルタイム）サービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UnshippedRealtimeReportService {

    private final OutboundReportRepository outboundReportRepository;
    private final WarehouseRepository warehouseRepository;
    private final BusinessDateProvider businessDateProvider;
    private final ReportExportService reportExportService;

    // --- ネイティブクエリのカラムインデックス定数 ---
    private static final int COL_SLIP_NUMBER = 0;
    private static final int COL_PARTNER_NAME = 1;
    private static final int COL_PLANNED_DATE = 2;
    private static final int COL_PRODUCT_CODE = 3;
    private static final int COL_PRODUCT_NAME = 4;
    private static final int COL_ORDERED_QTY = 5;
    private static final int COL_STATUS = 6;

    private static final String[] CSV_HEADERS = {
            "伝票番号", "出荷先名", "出荷予定日", "商品コード", "商品名",
            "数量", "ステータス", "遅延日数"
    };

    public ResponseEntity<List<UnshippedRealtimeReportItem>> generate(
            Long warehouseId, LocalDate asOfDate, ReportFormat format) {

        log.info("RPT-15 未出荷リスト（リアルタイム）生成開始: warehouseId={}, asOfDate={}, format={}",
                warehouseId, asOfDate, format);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        String warehouseName = warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")";

        LocalDate effectiveDate = asOfDate != null ? asOfDate : businessDateProvider.today();

        List<Object[]> rows = outboundReportRepository.findUnshippedRealtimeData(warehouseId, effectiveDate);

        List<UnshippedRealtimeReportItem> items = rows.stream()
                .map(row -> toReportItem(row, effectiveDate))
                .toList();

        ReportMeta meta = new ReportMeta(
                "未出荷リスト（リアルタイム）",
                "rpt-15-unshipped-realtime",
                "unshipped_realtime_" + todayFileDate(),
                warehouseName,
                getCurrentUserName(),
                "基準日: " + fmtDate(effectiveDate),
                CSV_HEADERS,
                row -> csvRowMapper((UnshippedRealtimeReportItem) row)
        );

        log.info("RPT-15 未出荷リスト（リアルタイム）生成完了: warehouseId={}, 件数={}",
                warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private UnshippedRealtimeReportItem toReportItem(Object[] row, LocalDate asOfDate) {
        LocalDate plannedDate = row[COL_PLANNED_DATE] != null
                ? ((java.sql.Date) row[COL_PLANNED_DATE]).toLocalDate() : null;
        String status = (String) row[COL_STATUS];

        UnshippedRealtimeReportItem item = new UnshippedRealtimeReportItem();
        item.setSlipNumber((String) row[COL_SLIP_NUMBER]);
        item.setCustomerName((String) row[COL_PARTNER_NAME]);
        item.setPlannedDate(plannedDate);
        item.setProductCode((String) row[COL_PRODUCT_CODE]);
        item.setProductName((String) row[COL_PRODUCT_NAME]);
        item.setOrderedQty(row[COL_ORDERED_QTY] != null
                ? ((Number) row[COL_ORDERED_QTY]).intValue() : 0);
        item.setStatus(status);
        item.setStatusLabel(OUTBOUND_STATUS_LABELS.getOrDefault(status, status));
        item.setDelayDays(plannedDate != null
                ? (int) ChronoUnit.DAYS.between(plannedDate, asOfDate) : 0);
        return item;
    }

    private String[] csvRowMapper(UnshippedRealtimeReportItem item) {
        return new String[]{
                item.getSlipNumber(),
                fmtOrDash(item.getCustomerName()),
                fmtDate(item.getPlannedDate()),
                item.getProductCode(),
                item.getProductName(),
                fmtInteger(item.getOrderedQty()),
                fmtOrDash(item.getStatusLabel()),
                item.getDelayDays() != null ? item.getDelayDays() + "日" : "\u2014"
        };
    }
}
