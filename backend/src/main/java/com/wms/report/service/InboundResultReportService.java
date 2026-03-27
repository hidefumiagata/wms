package com.wms.report.service;

import com.wms.generated.model.InboundResultReportItem;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;

/**
 * RPT-04: 入庫実績レポートサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InboundResultReportService {

    private final InboundReportRepository inboundReportRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final ReportExportService reportExportService;

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private static final String[] CSV_HEADERS = {
            "伝票番号", "入庫日", "仕入先名", "商品コード", "商品名",
            "予定数(ケース)", "検品数(ケース)", "差異(ケース)", "返品数量", "格納ロケーション"
    };

    public ResponseEntity<List<InboundResultReportItem>> generate(
            Long warehouseId, LocalDate storedDateFrom, LocalDate storedDateTo,
            Long partnerId, ReportFormat format) {

        log.info("RPT-04 入庫実績レポート生成開始: warehouseId={}, format={}", warehouseId, format);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        OffsetDateTime fromOdt = storedDateFrom != null
                ? storedDateFrom.atStartOfDay(JST).toOffsetDateTime() : null;
        OffsetDateTime toOdt = storedDateTo != null
                ? storedDateTo.plusDays(1).atStartOfDay(JST).toOffsetDateTime() : null;

        List<InboundSlipLine> lines = inboundReportRepository.findResultReportData(
                warehouseId, fromOdt, toOdt, partnerId);

        Map<Long, Product> productMap = loadProductMap(lines);

        List<InboundResultReportItem> items = lines.stream()
                .map(line -> toReportItem(line, productMap))
                .toList();

        String conditionsSummary = buildConditionsSummary(storedDateFrom, storedDateTo);
        ReportMeta meta = new ReportMeta(
                "入庫実績レポート",
                "rpt-04-inbound-result",
                "inbound_result_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")",
                getCurrentUserName(),
                conditionsSummary,
                CSV_HEADERS,
                row -> csvRowMapper((InboundResultReportItem) row)
        );

        log.info("RPT-04 入庫実績レポート生成完了: warehouseId={}, 件数={}", warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private InboundResultReportItem toReportItem(InboundSlipLine line, Map<Long, Product> productMap) {
        var slip = line.getInboundSlip();
        Product product = productMap.get(line.getProductId());
        int caseQuantity = product != null ? product.getCaseQuantity() : 1;

        int plannedCas = caseQuantity > 0 ? line.getPlannedQty() / caseQuantity : 0;
        Integer inspectedCas = line.getInspectedQty() != null && caseQuantity > 0
                ? line.getInspectedQty() / caseQuantity : null;
        Integer diffCas = inspectedCas != null ? inspectedCas - plannedCas : null;

        LocalDate storedDate = line.getStoredAt() != null
                ? line.getStoredAt().atZoneSameInstant(JST).toLocalDate() : null;

        InboundResultReportItem item = new InboundResultReportItem();
        item.setSlipNumber(slip.getSlipNumber());
        item.setStoredDate(storedDate);
        item.setSupplierName(slip.getPartnerName());
        item.setProductCode(line.getProductCode());
        item.setProductName(line.getProductName());
        item.setPlannedQuantityCas(plannedCas);
        item.setInspectedQuantityCas(inspectedCas);
        item.setDiffQuantityCas(diffCas);
        item.setStoredLocationCode(line.getPutawayLocationCode());
        // returnQuantity は現在未実装（返品モジュール連携待ち）
        item.setReturnQuantity(null);
        return item;
    }

    private Map<Long, Product> loadProductMap(List<InboundSlipLine> lines) {
        List<Long> productIds = lines.stream()
                .map(InboundSlipLine::getProductId)
                .distinct()
                .toList();
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private String buildConditionsSummary(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("期間: ");
        sb.append(from != null ? fmtDate(from) : "");
        sb.append(" ～ ");
        sb.append(to != null ? fmtDate(to) : "");
        return sb.toString();
    }

    private String[] csvRowMapper(InboundResultReportItem item) {
        return new String[]{
                item.getSlipNumber(),
                fmtDate(item.getStoredDate()),
                item.getSupplierName(),
                item.getProductCode(),
                item.getProductName(),
                fmtInteger(item.getPlannedQuantityCas()),
                fmtInteger(item.getInspectedQuantityCas()),
                fmtInteger(item.getDiffQuantityCas()),
                fmtInteger(item.getReturnQuantity()),
                fmtOrDash(item.getStoredLocationCode())
        };
    }

    private String getCurrentUserName() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
