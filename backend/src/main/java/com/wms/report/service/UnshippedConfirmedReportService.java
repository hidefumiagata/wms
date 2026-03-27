package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnshippedConfirmedReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.entity.UnshippedListRecord;
import com.wms.report.repository.UnshippedListRecordRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.OUTBOUND_STATUS_LABELS;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-16: 未出荷リスト（確定）サービス。
 * 日替処理で確定した unshipped_list_records テーブルのデータを出力する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UnshippedConfirmedReportService {

    private final UnshippedListRecordRepository unshippedListRecordRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "バッチ処理営業日", "伝票番号", "出荷先名", "出荷予定日",
            "商品コード", "商品名", "数量", "バッチ時点ステータス"
    };

    public ResponseEntity<List<UnshippedConfirmedReportItem>> generate(
            Long warehouseId, LocalDate batchBusinessDate, ReportFormat format) {

        log.info("RPT-16 未出荷リスト（確定）生成開始: warehouseId={}, batchBusinessDate={}, format={}",
                warehouseId, batchBusinessDate, format);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        String warehouseName = warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")";

        List<UnshippedListRecord> records = unshippedListRecordRepository
                .findByBatchBusinessDateAndWarehouseCodeOrderByPlannedDateAscSlipNumberAscProductCodeAsc(
                        batchBusinessDate, warehouse.getWarehouseCode());

        List<UnshippedConfirmedReportItem> items = records.stream()
                .map(this::toReportItem)
                .toList();

        Map<String, Object> extraVars = Map.of("statusLabels", OUTBOUND_STATUS_LABELS);

        ReportMeta meta = new ReportMeta(
                "未出荷リスト（確定）",
                "rpt-16-unshipped-confirmed",
                "unshipped_confirmed_" + todayFileDate(),
                warehouseName,
                getCurrentUserName(),
                "バッチ処理営業日: " + fmtDate(batchBusinessDate) + "（日替確定）",
                CSV_HEADERS,
                row -> csvRowMapper((UnshippedConfirmedReportItem) row),
                extraVars
        );

        log.info("RPT-16 未出荷リスト（確定）生成完了: warehouseId={}, 件数={}", warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private UnshippedConfirmedReportItem toReportItem(UnshippedListRecord record) {
        UnshippedConfirmedReportItem item = new UnshippedConfirmedReportItem();
        item.setBatchBusinessDate(record.getBatchBusinessDate());
        item.setSlipNumber(record.getSlipNumber());
        item.setCustomerName(record.getPartnerName());
        item.setPlannedDate(record.getPlannedDate());
        item.setProductCode(record.getProductCode());
        item.setProductName(record.getProductName());
        item.setOrderedQty(record.getOrderedQty());
        item.setStatusAtBatch(record.getCurrentStatus());
        return item;
    }

    private String[] csvRowMapper(UnshippedConfirmedReportItem item) {
        return new String[]{
                fmtDate(item.getBatchBusinessDate()),
                item.getSlipNumber(),
                fmtOrDash(item.getCustomerName()),
                fmtDate(item.getPlannedDate()),
                item.getProductCode(),
                item.getProductName(),
                fmtInteger(item.getOrderedQty()),
                OUTBOUND_STATUS_LABELS.getOrDefault(item.getStatusAtBatch(), fmtOrDash(item.getStatusAtBatch()))
        };
    }
}
