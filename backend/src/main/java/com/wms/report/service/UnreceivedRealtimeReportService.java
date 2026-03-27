package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.UnreceivedRealtimeReportItem;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.ProductRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InboundReportRepository;
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
import java.util.Map;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.INBOUND_STATUS_LABELS;
import static com.wms.report.service.ReportServiceUtils.getCaseQuantity;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.loadProductMap;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-05: 未入荷リスト（リアルタイム）サービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UnreceivedRealtimeReportService {

    private final InboundReportRepository inboundReportRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final BusinessDateProvider businessDateProvider;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "伝票番号", "仕入先名", "入荷予定日", "商品コード", "商品名",
            "予定数(ケース)", "ステータス", "遅延日数"
    };

    public ResponseEntity<List<UnreceivedRealtimeReportItem>> generate(
            Long warehouseId, LocalDate asOfDate, ReportFormat format) {

        log.info("RPT-05 未入荷リスト（リアルタイム）生成開始: warehouseId={}, asOfDate={}, format={}",
                warehouseId, asOfDate, format);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        LocalDate effectiveDate = asOfDate != null ? asOfDate : businessDateProvider.today();

        List<InboundSlipLine> lines = inboundReportRepository.findUnreceivedRealtimeData(
                warehouseId, effectiveDate);

        Map<Long, Product> productMap = loadProductMap(lines, productRepository);

        List<UnreceivedRealtimeReportItem> items = lines.stream()
                .map(line -> toReportItem(line, effectiveDate, productMap))
                .toList();

        ReportMeta meta = new ReportMeta(
                "未入荷リスト（リアルタイム）",
                "rpt-05-unreceived-realtime",
                "unreceived_realtime_" + todayFileDate(),
                warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")",
                getCurrentUserName(),
                "基準日: " + fmtDate(effectiveDate),
                CSV_HEADERS,
                row -> csvRowMapper((UnreceivedRealtimeReportItem) row)
        );

        log.info("RPT-05 未入荷リスト（リアルタイム）生成完了: warehouseId={}, 件数={}", warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private UnreceivedRealtimeReportItem toReportItem(
            InboundSlipLine line, LocalDate asOfDate, Map<Long, Product> productMap) {

        var slip = line.getInboundSlip();
        int caseQuantity = getCaseQuantity(productMap.get(line.getProductId()));

        int plannedCas = caseQuantity > 0 ? line.getPlannedQty() / caseQuantity : 0;
        int delayDays = (int) ChronoUnit.DAYS.between(slip.getPlannedDate(), asOfDate);

        UnreceivedRealtimeReportItem item = new UnreceivedRealtimeReportItem();
        item.setSlipNumber(slip.getSlipNumber());
        item.setSupplierName(slip.getPartnerName());
        item.setPlannedDate(slip.getPlannedDate());
        item.setProductCode(line.getProductCode());
        item.setProductName(line.getProductName());
        item.setPlannedQuantityCas(plannedCas);
        item.setStatus(slip.getStatus());
        item.setStatusLabel(INBOUND_STATUS_LABELS.getOrDefault(slip.getStatus(), slip.getStatus()));
        item.setDelayDays(delayDays);
        return item;
    }

    private String[] csvRowMapper(UnreceivedRealtimeReportItem item) {
        return new String[]{
                item.getSlipNumber(),
                item.getSupplierName(),
                fmtDate(item.getPlannedDate()),
                item.getProductCode(),
                item.getProductName(),
                fmtInteger(item.getPlannedQuantityCas()),
                fmtOrDash(item.getStatusLabel()),
                item.getDelayDays() != null ? item.getDelayDays() + "日" : "\u2014"
        };
    }
}
