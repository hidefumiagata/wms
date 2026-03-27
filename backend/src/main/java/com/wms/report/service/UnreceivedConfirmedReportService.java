package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnreceivedConfirmedReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.entity.UnreceivedListRecord;
import com.wms.report.repository.UnreceivedListRecordRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;

/**
 * RPT-06: 未入荷リスト（確定）サービス。
 * 日替処理で確定した unreceived_list_records テーブルのデータを出力する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UnreceivedConfirmedReportService {

    private final UnreceivedListRecordRepository unreceivedListRecordRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "バッチ処理営業日", "伝票番号", "仕入先名", "入荷予定日",
            "商品コード", "商品名", "予定数(ケース)", "バッチ時点ステータス"
    };

    private static final java.util.Map<String, String> STATUS_LABELS = java.util.Map.of(
            "PLANNED", "入荷予定",
            "CONFIRMED", "確定",
            "INSPECTING", "検品中",
            "INSPECTED", "検品済",
            "PARTIAL_STORED", "一部入庫"
    );

    public ResponseEntity<List<UnreceivedConfirmedReportItem>> generate(
            Long warehouseId, LocalDate batchBusinessDate, ReportFormat format) {

        log.info("RPT-06 未入荷リスト（確定）生成開始: warehouseId={}, batchBusinessDate={}, format={}",
                warehouseId, batchBusinessDate, format);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        List<UnreceivedListRecord> records = unreceivedListRecordRepository
                .findByBatchBusinessDateAndWarehouseCode(batchBusinessDate, warehouse.getWarehouseCode());

        List<UnreceivedConfirmedReportItem> items = records.stream()
                .map(record -> toReportItem(record))
                .toList();

        ReportMeta meta = new ReportMeta(
                "未入荷リスト（確定）",
                "rpt-06-unreceived-confirmed",
                "unreceived_confirmed_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")",
                getCurrentUserName(),
                "営業日基準日: " + fmtDate(batchBusinessDate) + "（日替確定）",
                CSV_HEADERS,
                row -> csvRowMapper((UnreceivedConfirmedReportItem) row)
        );

        log.info("RPT-06 未入荷リスト（確定）生成完了: warehouseId={}, 件数={}", warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private UnreceivedConfirmedReportItem toReportItem(UnreceivedListRecord record) {
        UnreceivedConfirmedReportItem item = new UnreceivedConfirmedReportItem();
        item.setBatchBusinessDate(record.getBatchBusinessDate());
        item.setSlipNumber(record.getSlipNumber());
        item.setSupplierName(record.getPartnerName());
        item.setPlannedDate(record.getPlannedDate());
        item.setProductCode(record.getProductCode());
        item.setProductName(record.getProductName());
        item.setPlannedQuantityCas(record.getPlannedQty());
        item.setStatusAtBatch(record.getCurrentStatus());
        return item;
    }

    private String[] csvRowMapper(UnreceivedConfirmedReportItem item) {
        return new String[]{
                fmtDate(item.getBatchBusinessDate()),
                item.getSlipNumber(),
                fmtOrDash(item.getSupplierName()),
                fmtDate(item.getPlannedDate()),
                item.getProductCode(),
                item.getProductName(),
                fmtInteger(item.getPlannedQuantityCas()),
                STATUS_LABELS.getOrDefault(item.getStatusAtBatch(), fmtOrDash(item.getStatusAtBatch()))
        };
    }

    private String getCurrentUserName() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
