package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.ReturnReason;
import com.wms.generated.model.ReturnType;
import com.wms.generated.model.ReturnsReportItem;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.ReturnsReportRepository;
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
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-18: 返品レポートサービス。
 * return_slips テーブルから返品データを取得し、返品種別ごとにグルーピングして返す。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReturnsReportService {

    private final ReturnsReportRepository returnsReportRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    private static final int COL_RETURN_NUMBER = 0;
    private static final int COL_RETURN_TYPE = 1;
    private static final int COL_RETURN_DATE = 2;
    private static final int COL_PRODUCT_CODE = 3;
    private static final int COL_PRODUCT_NAME = 4;
    private static final int COL_QUANTITY = 5;
    private static final int COL_UNIT_TYPE = 6;
    private static final int COL_RETURN_REASON = 7;
    private static final int COL_RETURN_REASON_NOTE = 8;
    private static final int COL_RELATED_SLIP_NUMBER = 9;
    private static final int COL_PARTNER_NAME = 10;

    static final Map<String, String> RETURN_TYPE_LABELS = Map.of(
            "INBOUND", "入荷返品",
            "OUTBOUND", "出荷返品",
            "INVENTORY", "在庫返品"
    );

    static final Map<String, String> RETURN_REASON_LABELS = Map.of(
            "QUALITY_DEFECT", "品質不良",
            "EXCESS_QUANTITY", "数量過剰",
            "WRONG_DELIVERY", "誤配送",
            "EXPIRED", "期限切れ",
            "DAMAGED", "破損",
            "OTHER", "その他"
    );

    private static final String[] CSV_HEADERS = {
            "返品伝票番号", "返品種別", "返品種別名", "返品日",
            "商品コード", "商品名", "数量", "荷姿",
            "返品理由", "返品理由名", "返品理由備考",
            "関連伝票番号", "仕入先名"
    };

    public ResponseEntity<List<ReturnsReportItem>> generate(
            Long warehouseId, ReturnType returnType,
            LocalDate returnDateFrom, LocalDate returnDateTo,
            Long productId, Long partnerId,
            ReturnReason returnReason, ReportFormat format) {

        log.info("RPT-18 返品レポート生成開始: warehouseId={}, returnType={}, " +
                        "dateFrom={}, dateTo={}, productId={}, partnerId={}, returnReason={}, format={}",
                warehouseId, returnType, returnDateFrom, returnDateTo,
                productId, partnerId, returnReason, format);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        String warehouseName = warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")";

        String returnTypeStr = returnType != null ? returnType.getValue() : null;
        String returnReasonStr = returnReason != null ? returnReason.getValue() : null;

        List<Object[]> rows = returnsReportRepository.findReturnsReportData(
                warehouseId, returnTypeStr, returnDateFrom, returnDateTo,
                productId, partnerId, returnReasonStr);

        List<ReturnsReportItem> items = rows.stream()
                .map(this::toReportItem)
                .toList();

        String conditions = buildConditionsSummary(returnDateFrom, returnDateTo, returnType, returnReason);

        ReportMeta meta = new ReportMeta(
                "返品レポート",
                "rpt-18-returns",
                "returns_" + todayFileDate(),
                warehouseName,
                getCurrentUserName(),
                conditions,
                CSV_HEADERS,
                row -> csvRowMapper((ReturnsReportItem) row)
        );

        log.info("RPT-18 返品レポート生成完了: warehouseId={}, 件数={}",
                warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private ReturnsReportItem toReportItem(Object[] row) {
        ReturnsReportItem item = new ReturnsReportItem();
        item.setReturnNumber((String) row[COL_RETURN_NUMBER]);

        String returnTypeStr = (String) row[COL_RETURN_TYPE];
        if (returnTypeStr != null) {
            item.setReturnType(ReturnType.fromValue(returnTypeStr));
            item.setReturnTypeLabel(RETURN_TYPE_LABELS.getOrDefault(returnTypeStr, returnTypeStr));
        }

        item.setReturnDate(row[COL_RETURN_DATE] != null
                ? ((java.sql.Date) row[COL_RETURN_DATE]).toLocalDate() : null);
        item.setProductCode((String) row[COL_PRODUCT_CODE]);
        item.setProductName((String) row[COL_PRODUCT_NAME]);
        item.setQuantity(row[COL_QUANTITY] != null
                ? ((Number) row[COL_QUANTITY]).intValue() : 0);
        item.setUnitType(toUnitTypeLabel((String) row[COL_UNIT_TYPE]));

        String returnReasonStr = (String) row[COL_RETURN_REASON];
        if (returnReasonStr != null) {
            item.setReturnReason(ReturnReason.fromValue(returnReasonStr));
            item.setReturnReasonLabel(RETURN_REASON_LABELS.getOrDefault(returnReasonStr, returnReasonStr));
        }

        item.setReturnReasonNote((String) row[COL_RETURN_REASON_NOTE]);
        item.setRelatedSlipNumber((String) row[COL_RELATED_SLIP_NUMBER]);
        item.setPartnerName((String) row[COL_PARTNER_NAME]);
        return item;
    }

    static String toUnitTypeLabel(String unitType) {
        if (unitType == null) {
            return null;
        }
        return switch (unitType) {
            case "CASE" -> "CAS";
            case "BALL" -> "BAL";
            case "PIECE" -> "PCS";
            default -> unitType;
        };
    }

    private String buildConditionsSummary(LocalDate dateFrom, LocalDate dateTo,
                                          ReturnType returnType, ReturnReason returnReason) {
        StringBuilder sb = new StringBuilder();
        if (dateFrom != null || dateTo != null) {
            sb.append("期間: ");
            sb.append(dateFrom != null ? fmtDate(dateFrom) : "—");
            sb.append(" ～ ");
            sb.append(dateTo != null ? fmtDate(dateTo) : "—");
        }
        if (returnType != null) {
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append("返品種別: ").append(RETURN_TYPE_LABELS.getOrDefault(
                    returnType.getValue(), returnType.getValue()));
        }
        if (returnReason != null) {
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append("返品理由: ").append(RETURN_REASON_LABELS.getOrDefault(
                    returnReason.getValue(), returnReason.getValue()));
        }
        return sb.toString();
    }

    private String[] csvRowMapper(ReturnsReportItem item) {
        return new String[]{
                fmtOrDash(item.getReturnNumber()),
                item.getReturnType() != null ? item.getReturnType().getValue() : "—",
                fmtOrDash(item.getReturnTypeLabel()),
                fmtDate(item.getReturnDate()),
                fmtOrDash(item.getProductCode()),
                fmtOrDash(item.getProductName()),
                fmtInteger(item.getQuantity()),
                fmtOrDash(item.getUnitType()),
                item.getReturnReason() != null ? item.getReturnReason().getValue() : "—",
                fmtOrDash(item.getReturnReasonLabel()),
                fmtOrDash(item.getReturnReasonNote()),
                fmtOrDash(item.getRelatedSlipNumber()),
                fmtOrDash(item.getPartnerName())
        };
    }
}
