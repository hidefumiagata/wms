package com.wms.report.service;

import com.wms.generated.model.DeliveryListLineItem;
import com.wms.generated.model.DeliveryListReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.OutboundReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.OUTBOUND_STATUS_LABELS;
import static com.wms.report.service.ReportServiceUtils.escapeLikePattern;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-14: 配送リストサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DeliveryListReportService {

    private final OutboundReportRepository outboundReportRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    // --- ヘッダー行カラムインデックス ---
    private static final int H_COL_ID = 0;
    private static final int H_COL_SLIP_NUMBER = 1;
    private static final int H_COL_PARTNER_NAME = 2;
    private static final int H_COL_PLANNED_DATE = 3;
    private static final int H_COL_STATUS = 4;
    private static final int H_COL_CARRIER = 5;
    private static final int H_COL_TRACKING_NUMBER = 6;
    private static final int H_COL_ADDRESS = 7;

    // --- 明細行カラムインデックス ---
    private static final int L_COL_SLIP_ID = 0;
    private static final int L_COL_PRODUCT_CODE = 1;
    private static final int L_COL_PRODUCT_NAME = 2;
    private static final int L_COL_UNIT_TYPE = 3;
    private static final int L_COL_ORDERED_QTY = 4;

    private static final String[] CSV_HEADERS = {
            "伝票番号", "出荷先名", "配送先住所", "出荷予定日", "ステータス",
            "配送業者", "送り状番号", "商品コード", "商品名", "荷姿", "数量"
    };

    public ResponseEntity<List<DeliveryListReportItem>> generate(
            Long warehouseId, LocalDate plannedDateFrom, LocalDate plannedDateTo,
            String status, String carrier, ReportFormat format) {

        log.info("RPT-14 配送リスト生成開始: warehouseId={}, format={}", warehouseId, format);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        String warehouseName = warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")";

        String carrierLike = carrier != null ? "%" + escapeLikePattern(carrier) + "%" : null;

        List<Object[]> headerRows = outboundReportRepository.findDeliveryListHeaderData(
                warehouseId, plannedDateFrom, plannedDateTo, status, carrierLike);

        List<DeliveryListReportItem> items;
        if (headerRows.isEmpty()) {
            items = List.of();
        } else {
            List<Long> slipIds = headerRows.stream()
                    .map(row -> ((Number) row[H_COL_ID]).longValue())
                    .toList();

            List<Object[]> lineRows = outboundReportRepository.findDeliveryListLineData(slipIds);

            Map<Long, List<Object[]>> linesBySlipId = new LinkedHashMap<>();
            for (Object[] lineRow : lineRows) {
                Long slipId = ((Number) lineRow[L_COL_SLIP_ID]).longValue();
                linesBySlipId.computeIfAbsent(slipId, k -> new ArrayList<>()).add(lineRow);
            }

            items = headerRows.stream()
                    .map(headerRow -> toReportItem(headerRow, linesBySlipId))
                    .toList();
        }

        String conditionsSummary = buildConditionsSummary(plannedDateFrom, plannedDateTo, status, carrier);

        ReportMeta meta = new ReportMeta(
                "配送リスト",
                "rpt-14-delivery-list",
                "delivery_list_" + todayFileDate(),
                warehouseName,
                getCurrentUserName(),
                conditionsSummary,
                CSV_HEADERS,
                row -> csvRowMapper((DeliveryListReportItem) row)
        );

        log.info("RPT-14 配送リスト生成完了: warehouseId={}, 件数={}", warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private DeliveryListReportItem toReportItem(Object[] headerRow, Map<Long, List<Object[]>> linesBySlipId) {
        Long slipId = ((Number) headerRow[H_COL_ID]).longValue();
        String statusCode = (String) headerRow[H_COL_STATUS];

        DeliveryListReportItem item = new DeliveryListReportItem();
        item.setSlipNumber((String) headerRow[H_COL_SLIP_NUMBER]);
        item.setCustomerName((String) headerRow[H_COL_PARTNER_NAME]);
        item.setDeliveryAddress((String) headerRow[H_COL_ADDRESS]);
        item.setPlannedShipDate(headerRow[H_COL_PLANNED_DATE] != null
                ? ((java.sql.Date) headerRow[H_COL_PLANNED_DATE]).toLocalDate() : null);
        item.setStatus(statusCode);
        item.setStatusLabel(OUTBOUND_STATUS_LABELS.getOrDefault(statusCode, statusCode));
        item.setCarrier((String) headerRow[H_COL_CARRIER]);
        item.setTrackingNumber((String) headerRow[H_COL_TRACKING_NUMBER]);

        List<DeliveryListLineItem> lines = new ArrayList<>();
        int totalQtyPcs = 0;
        List<Object[]> lineData = linesBySlipId.getOrDefault(slipId, List.of());
        for (Object[] lineRow : lineData) {
            DeliveryListLineItem lineItem = new DeliveryListLineItem();
            lineItem.setProductCode((String) lineRow[L_COL_PRODUCT_CODE]);
            lineItem.setProductName((String) lineRow[L_COL_PRODUCT_NAME]);
            lineItem.setUnitType((String) lineRow[L_COL_UNIT_TYPE]);
            int qty = lineRow[L_COL_ORDERED_QTY] != null
                    ? ((Number) lineRow[L_COL_ORDERED_QTY]).intValue() : 0;
            lineItem.setQuantity(qty);
            lines.add(lineItem);
            totalQtyPcs += qty;
        }
        item.setLines(lines);
        item.setTotalQuantityCas(totalQtyPcs);
        item.setTotalQuantityPcs(totalQtyPcs);

        return item;
    }

    private String buildConditionsSummary(LocalDate plannedDateFrom, LocalDate plannedDateTo,
                                            String status, String carrier) {
        StringBuilder sb = new StringBuilder();
        if (plannedDateFrom != null || plannedDateTo != null) {
            sb.append("期間: ");
            sb.append(plannedDateFrom != null ? fmtDate(plannedDateFrom) : "—");
            sb.append(" 〜 ");
            sb.append(plannedDateTo != null ? fmtDate(plannedDateTo) : "—");
        }
        if (status != null) {
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append("ステータス: ").append(OUTBOUND_STATUS_LABELS.getOrDefault(status, status));
        }
        if (carrier != null) {
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append("配送業者: ").append(carrier);
        }
        return sb.toString();
    }

    /**
     * CSV出力時は伝票情報を明細行ごとに繰り返すフラット形式で出力する。
     * 1行目はlines[0]の情報、2行目以降は伝票情報重複+lines[n]を返す。
     * CsvGenerationServiceは1アイテム→1行の設計のため、全明細の合計行数分を
     * 1行目（=最初のlines要素）で代表出力する。
     * ネスト構造のCSVフラット展開はexport前にアイテムを事前展開する方式も
     * あるが、JSON/PDFとの共通処理との整合性を保つため、CSV出力時は
     * 伝票ヘッダー＋先頭明細の代表行を出力する設計とする。
     */
    private String[] csvRowMapper(DeliveryListReportItem item) {
        List<DeliveryListLineItem> lines = item.getLines();
        if (lines != null && !lines.isEmpty()) {
            DeliveryListLineItem line = lines.get(0);
            return new String[]{
                    item.getSlipNumber(),
                    fmtOrDash(item.getCustomerName()),
                    fmtOrDash(item.getDeliveryAddress()),
                    fmtDate(item.getPlannedShipDate()),
                    fmtOrDash(item.getStatusLabel()),
                    fmtOrDash(item.getCarrier()),
                    fmtOrDash(item.getTrackingNumber()),
                    line.getProductCode(),
                    line.getProductName(),
                    fmtOrDash(line.getUnitType()),
                    fmtInteger(line.getQuantity())
            };
        }
        return new String[]{
                item.getSlipNumber(),
                fmtOrDash(item.getCustomerName()),
                fmtOrDash(item.getDeliveryAddress()),
                fmtDate(item.getPlannedShipDate()),
                fmtOrDash(item.getStatusLabel()),
                fmtOrDash(item.getCarrier()),
                fmtOrDash(item.getTrackingNumber()),
                "", "", "", ""
        };
    }
}
