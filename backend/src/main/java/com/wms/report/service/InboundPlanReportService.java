package com.wms.report.service;

import com.wms.generated.model.InboundPlanReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.ProductRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InboundReportRepository;
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
import static com.wms.report.service.ReportServiceUtils.INBOUND_STATUS_LABELS;
import static com.wms.report.service.ReportServiceUtils.getCaseQuantity;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.loadProductMap;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-03: 入荷予定レポートサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InboundPlanReportService {

    private final InboundReportRepository inboundReportRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "伝票番号", "仕入先名", "入荷予定日", "商品コード", "商品名",
            "予定数(ケース)", "予定数(バラ)", "ステータス"
    };

    public ResponseEntity<List<InboundPlanReportItem>> generate(
            Long warehouseId, LocalDate plannedDateFrom, LocalDate plannedDateTo,
            String status, Long partnerId, ReportFormat format) {

        log.info("RPT-03 入荷予定レポート生成開始: warehouseId={}, format={}", warehouseId, format);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        List<InboundSlipLine> lines = inboundReportRepository.findPlanReportData(
                warehouseId, plannedDateFrom, plannedDateTo, status, partnerId);

        Map<Long, Product> productMap = loadProductMap(lines, productRepository);

        List<InboundPlanReportItem> items = lines.stream()
                .map(line -> toReportItem(line, productMap))
                .toList();

        String conditionsSummary = buildConditionsSummary(plannedDateFrom, plannedDateTo, status);
        ReportMeta meta = new ReportMeta(
                "入荷予定レポート",
                "rpt-03-inbound-plan",
                "inbound_plan_" + todayFileDate(),
                warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")",
                getCurrentUserName(),
                conditionsSummary,
                CSV_HEADERS,
                row -> csvRowMapper((InboundPlanReportItem) row)
        );

        log.info("RPT-03 入荷予定レポート生成完了: warehouseId={}, 件数={}", warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private InboundPlanReportItem toReportItem(InboundSlipLine line, Map<Long, Product> productMap) {
        var slip = line.getInboundSlip();
        int caseQuantity = getCaseQuantity(productMap.get(line.getProductId()));

        int plannedPcs = line.getPlannedQty();
        int plannedCas = caseQuantity > 0 ? plannedPcs / caseQuantity : 0;

        InboundPlanReportItem item = new InboundPlanReportItem();
        item.setSlipNumber(slip.getSlipNumber());
        item.setSupplierName(slip.getPartnerName());
        item.setPlannedDate(slip.getPlannedDate());
        item.setProductCode(line.getProductCode());
        item.setProductName(line.getProductName());
        item.setPlannedQuantityCas(plannedCas);
        item.setPlannedQuantityPcs(plannedPcs);
        item.setStatus(slip.getStatus());
        item.setStatusLabel(INBOUND_STATUS_LABELS.getOrDefault(slip.getStatus(), slip.getStatus()));
        return item;
    }

    private String buildConditionsSummary(LocalDate from, LocalDate to, String status) {
        StringBuilder sb = new StringBuilder();
        if (from != null || to != null) {
            sb.append("期間: ");
            sb.append(from != null ? fmtDate(from) : "");
            sb.append(" ～ ");
            sb.append(to != null ? fmtDate(to) : "");
        }
        if (status != null) {
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append("ステータス: ").append(INBOUND_STATUS_LABELS.getOrDefault(status, status));
        }
        return sb.toString();
    }

    private String[] csvRowMapper(InboundPlanReportItem item) {
        return new String[]{
                item.getSlipNumber(),
                item.getSupplierName(),
                fmtDate(item.getPlannedDate()),
                item.getProductCode(),
                item.getProductName(),
                fmtInteger(item.getPlannedQuantityCas()),
                fmtInteger(item.getPlannedQuantityPcs()),
                fmtOrDash(item.getStatusLabel())
        };
    }
}
