package com.wms.report.service;

import com.wms.generated.model.DailySummaryReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.report.repository.BatchExecutionLogRepository;
import com.wms.report.repository.DailySummaryRecordRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-17: 日次集計レポートサービス。
 * daily_summary_records テーブルから対象営業日のデータを取得し、
 * 倉庫ごとの入荷・出荷・返品・在庫・未処理アラートのサマリーを返す。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DailySummaryReportService {

    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final DailySummaryRecordRepository dailySummaryRecordRepository;
    private final ReportExportService reportExportService;

    private static final int COL_BUSINESS_DATE = 0;
    private static final int COL_WAREHOUSE_ID = 1;
    private static final int COL_WAREHOUSE_NAME = 2;
    private static final int COL_INBOUND_COUNT = 3;
    private static final int COL_INBOUND_LINE_COUNT = 4;
    private static final int COL_INBOUND_QTY_TOTAL = 5;
    private static final int COL_OUTBOUND_COUNT = 6;
    private static final int COL_OUTBOUND_LINE_COUNT = 7;
    private static final int COL_OUTBOUND_QTY_TOTAL = 8;
    private static final int COL_RETURN_COUNT = 9;
    private static final int COL_RETURN_QTY_TOTAL = 10;
    private static final int COL_INVENTORY_QTY_TOTAL = 11;
    private static final int COL_UNRECEIVED_COUNT = 12;
    private static final int COL_UNSHIPPED_COUNT = 13;

    private static final String[] CSV_HEADERS = {
            "対象営業日", "倉庫ID", "倉庫名",
            "入荷件数", "入荷明細行数", "入荷数量合計",
            "出荷件数", "出荷明細行数", "出荷数量合計",
            "返品件数", "返品数量合計",
            "在庫数量合計", "未入荷件数", "未出荷件数"
    };

    public ResponseEntity<List<DailySummaryReportItem>> generate(
            LocalDate targetBusinessDate, ReportFormat format) {

        log.info("RPT-17 日次集計レポート生成開始: targetBusinessDate={}, format={}",
                targetBusinessDate, format);

        // 日替処理の SUCCESS 完了チェック
        boolean batchCompleted = batchExecutionLogRepository
                .existsByTargetBusinessDateAndStatus(targetBusinessDate, "SUCCESS");
        if (!batchCompleted) {
            throw new ResourceNotFoundException("BATCH_EXECUTION_NOT_FOUND",
                    "指定日の日替処理が完了していません: targetBusinessDate=" + targetBusinessDate);
        }

        List<Object[]> rows = dailySummaryRecordRepository.findDailySummaryData(targetBusinessDate);

        List<DailySummaryReportItem> items = rows.stream()
                .map(this::toReportItem)
                .toList();

        ReportMeta meta = new ReportMeta(
                "日次集計レポート",
                "rpt-17-daily-summary",
                "daily_summary_" + todayFileDate(),
                "全倉庫",
                getCurrentUserName(),
                "対象営業日: " + fmtDate(targetBusinessDate),
                CSV_HEADERS,
                row -> csvRowMapper((DailySummaryReportItem) row)
        );

        log.info("RPT-17 日次集計レポート生成完了: targetBusinessDate={}, 倉庫数={}",
                targetBusinessDate, items.size());
        return reportExportService.export(items, format, meta);
    }

    private DailySummaryReportItem toReportItem(Object[] row) {
        DailySummaryReportItem item = new DailySummaryReportItem();
        item.setBusinessDate(row[COL_BUSINESS_DATE] != null
                ? ((java.sql.Date) row[COL_BUSINESS_DATE]).toLocalDate() : null);
        item.setWarehouseId(row[COL_WAREHOUSE_ID] != null
                ? ((Number) row[COL_WAREHOUSE_ID]).longValue() : null);
        item.setWarehouseName((String) row[COL_WAREHOUSE_NAME]);
        item.setInboundCount(toInt(row[COL_INBOUND_COUNT]));
        item.setInboundLineCount(toInt(row[COL_INBOUND_LINE_COUNT]));
        item.setInboundQuantityTotal(toInt(row[COL_INBOUND_QTY_TOTAL]));
        item.setOutboundCount(toInt(row[COL_OUTBOUND_COUNT]));
        item.setOutboundLineCount(toInt(row[COL_OUTBOUND_LINE_COUNT]));
        item.setOutboundQuantityTotal(toInt(row[COL_OUTBOUND_QTY_TOTAL]));
        item.setReturnCount(toInt(row[COL_RETURN_COUNT]));
        item.setReturnQuantityTotal(toInt(row[COL_RETURN_QTY_TOTAL]));
        item.setInventoryQuantityTotal(toInt(row[COL_INVENTORY_QTY_TOTAL]));
        item.setUnreceivedCount(toInt(row[COL_UNRECEIVED_COUNT]));
        item.setUnshippedCount(toInt(row[COL_UNSHIPPED_COUNT]));
        return item;
    }

    private static Integer toInt(Object value) {
        return value != null ? ((Number) value).intValue() : 0;
    }

    private String[] csvRowMapper(DailySummaryReportItem item) {
        return new String[]{
                fmtDate(item.getBusinessDate()),
                fmtOrDash(item.getWarehouseId()),
                fmtOrDash(item.getWarehouseName()),
                fmtInteger(item.getInboundCount()),
                fmtInteger(item.getInboundLineCount()),
                fmtInteger(item.getInboundQuantityTotal()),
                fmtInteger(item.getOutboundCount()),
                fmtInteger(item.getOutboundLineCount()),
                fmtInteger(item.getOutboundQuantityTotal()),
                fmtInteger(item.getReturnCount()),
                fmtInteger(item.getReturnQuantityTotal()),
                fmtInteger(item.getInventoryQuantityTotal()),
                fmtInteger(item.getUnreceivedCount()),
                fmtInteger(item.getUnshippedCount())
        };
    }
}
