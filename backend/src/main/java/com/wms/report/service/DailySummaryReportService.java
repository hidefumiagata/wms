package com.wms.report.service;

import com.wms.generated.model.DailySummaryReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.report.repository.BatchExecutionLogRepository;
import com.wms.report.repository.DailySummaryRecordRepository;
import com.wms.report.repository.projection.DailySummaryReportRow;
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

    static final String BATCH_STATUS_SUCCESS = "SUCCESS";

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
                .existsByTargetBusinessDateAndStatus(targetBusinessDate, BATCH_STATUS_SUCCESS);
        if (!batchCompleted) {
            throw ResourceNotFoundException.of("BATCH_EXECUTION_NOT_FOUND",
                    "日替処理結果", targetBusinessDate);
        }

        List<DailySummaryReportRow> rows = dailySummaryRecordRepository.findDailySummaryData(targetBusinessDate);

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

    private DailySummaryReportItem toReportItem(DailySummaryReportRow row) {
        DailySummaryReportItem item = new DailySummaryReportItem();
        item.setBusinessDate(row.getBusinessDate());
        item.setWarehouseId(row.getWarehouseId());
        item.setWarehouseName(row.getWarehouseName());
        item.setInboundCount(toInt(row.getInboundCount()));
        item.setInboundLineCount(toInt(row.getInboundLineCount()));
        item.setInboundQuantityTotal(toInt(row.getInboundQuantityTotal()));
        item.setOutboundCount(toInt(row.getOutboundCount()));
        item.setOutboundLineCount(toInt(row.getOutboundLineCount()));
        item.setOutboundQuantityTotal(toInt(row.getOutboundQuantityTotal()));
        item.setReturnCount(toInt(row.getReturnCount()));
        item.setReturnQuantityTotal(toInt(row.getReturnQuantityTotal()));
        item.setInventoryQuantityTotal(toInt(row.getInventoryQuantityTotal()));
        item.setUnreceivedCount(toInt(row.getUnreceivedCount()));
        item.setUnshippedCount(toInt(row.getUnshippedCount()));
        return item;
    }

    private static Integer toInt(Number value) {
        return value != null ? value.intValue() : 0;
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
