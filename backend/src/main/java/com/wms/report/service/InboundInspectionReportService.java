package com.wms.report.service;

import com.wms.generated.model.InboundInspectionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.master.entity.Product;
import com.wms.master.repository.ProductRepository;
import com.wms.report.repository.InboundReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.getCaseQuantity;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.loadProductMap;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-01: 入荷検品レポートサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InboundInspectionReportService {

    private final InboundReportRepository inboundReportRepository;
    private final ProductRepository productRepository;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "伝票番号", "仕入先名", "入荷予定日", "商品コード", "商品名",
            "ケース入数", "予定数(ケース)", "検品数(ケース)", "差異(ケース)",
            "予定数(バラ)", "検品数(バラ)", "差異(バラ)", "ロット番号", "期限日"
    };

    public ResponseEntity<List<InboundInspectionReportItem>> generate(Long slipId, ReportFormat format) {
        log.info("RPT-01 入荷検品レポート生成開始: slipId={}, format={}", slipId, format);
        List<InboundSlipLine> lines = inboundReportRepository.findInspectionReportData(slipId);
        if (lines.isEmpty()) {
            throw new ResourceNotFoundException("INBOUND_SLIP_NOT_FOUND",
                    "入荷伝票が見つかりません: slipId=" + slipId);
        }

        InboundSlip slip = lines.getFirst().getInboundSlip();
        Map<Long, Product> productMap = loadProductMap(lines, productRepository);

        List<InboundInspectionReportItem> items = lines.stream()
                .map(line -> toReportItem(slip, line, productMap))
                .toList();

        ReportMeta meta = new ReportMeta(
                "入荷検品レポート",
                "rpt-01-inbound-inspection",
                "inbound_inspection_" + todayFileDate(),
                slip.getWarehouseName() + " (" + slip.getWarehouseCode() + ")",
                getCurrentUserName(),
                "伝票番号: " + slip.getSlipNumber(),
                CSV_HEADERS,
                row -> csvRowMapper((InboundInspectionReportItem) row)
        );

        log.info("RPT-01 入荷検品レポート生成完了: slipId={}, 件数={}", slipId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private InboundInspectionReportItem toReportItem(
            InboundSlip slip, InboundSlipLine line, Map<Long, Product> productMap) {

        Product product = productMap.get(line.getProductId());
        int caseQuantity = getCaseQuantity(product);

        Integer plannedPcs = line.getPlannedQty();
        Integer inspectedPcs = line.getInspectedQty();

        int plannedCas = caseQuantity > 0 ? plannedPcs / caseQuantity : 0;
        Integer inspectedCas = inspectedPcs != null && caseQuantity > 0
                ? inspectedPcs / caseQuantity : null;

        Integer diffCas = inspectedCas != null ? inspectedCas - plannedCas : null;
        Integer diffPcs = inspectedPcs != null ? inspectedPcs - plannedPcs : null;

        InboundInspectionReportItem item = new InboundInspectionReportItem();
        item.setSlipNumber(slip.getSlipNumber());
        item.setSupplierName(slip.getPartnerName());
        item.setPlannedDate(slip.getPlannedDate());
        item.setProductCode(line.getProductCode());
        item.setProductName(line.getProductName());
        item.setCaseQuantity(product != null ? product.getCaseQuantity() : 1);
        item.setPlannedQuantityCas(plannedCas);
        item.setInspectedQuantityCas(inspectedCas);
        item.setDiffQuantityCas(diffCas);
        item.setPlannedQuantityPcs(plannedPcs);
        item.setInspectedQuantityPcs(inspectedPcs);
        item.setDiffQuantityPcs(diffPcs);
        item.setLotNumber(line.getLotNumber());
        item.setExpiryDate(line.getExpiryDate());
        return item;
    }

    private String[] csvRowMapper(InboundInspectionReportItem item) {
        return new String[]{
                item.getSlipNumber(),
                item.getSupplierName(),
                fmtDate(item.getPlannedDate()),
                item.getProductCode(),
                item.getProductName(),
                fmtInteger(item.getCaseQuantity()),
                fmtInteger(item.getPlannedQuantityCas()),
                fmtInteger(item.getInspectedQuantityCas()),
                fmtInteger(item.getDiffQuantityCas()),
                fmtInteger(item.getPlannedQuantityPcs()),
                fmtInteger(item.getInspectedQuantityPcs()),
                fmtInteger(item.getDiffQuantityPcs()),
                fmtOrDash(item.getLotNumber()),
                fmtDate(item.getExpiryDate())
        };
    }
}
