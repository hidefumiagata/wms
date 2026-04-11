package com.wms.report.service;

import com.wms.generated.model.DeliveryListLineItem;
import com.wms.generated.model.DeliveryListReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.OutboundReportRepository;
import com.wms.report.repository.projection.DeliveryListHeaderRow;
import com.wms.report.repository.projection.DeliveryListLineRow;
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
import java.util.stream.Collectors;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.OUTBOUND_STATUS_LABELS;
import static com.wms.report.service.ReportServiceUtils.escapeLikePattern;
import static com.wms.report.service.ReportServiceUtils.formatWarehouseName;
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

    private static final String[] CSV_HEADERS = {
            "伝票番号", "出荷先名", "配送先住所", "出荷予定日", "ステータス",
            "配送業者", "送り状番号", "商品コード", "商品名", "荷姿", "数量"
    };

    public ResponseEntity<List<DeliveryListReportItem>> generate(
            Long warehouseId, LocalDate plannedDateFrom, LocalDate plannedDateTo,
            String status, String carrier, ReportFormat format) {

        log.info("RPT-14 配送リスト生成開始: warehouseId={}, format={}", warehouseId, format);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ResourceNotFoundException.of("WAREHOUSE_NOT_FOUND", "倉庫", warehouseId));

        String warehouseName = formatWarehouseName(warehouse);

        String carrierLike = carrier != null ? "%" + escapeLikePattern(carrier) + "%" : null;

        List<DeliveryListHeaderRow> headerRows = outboundReportRepository.findDeliveryListHeaderData(
                warehouseId, plannedDateFrom, plannedDateTo, status, carrierLike);

        List<DeliveryListReportItem> items;
        if (headerRows.isEmpty()) {
            items = List.of();
        } else {
            List<Long> slipIds = headerRows.stream()
                    .map(DeliveryListHeaderRow::getId)
                    .toList();

            List<DeliveryListLineRow> lineRows = outboundReportRepository.findDeliveryListLineData(slipIds);

            Map<Long, List<DeliveryListLineRow>> linesBySlipId = lineRows.stream()
                    .collect(Collectors.groupingBy(
                            DeliveryListLineRow::getOutboundSlipId, LinkedHashMap::new, Collectors.toList()));

            items = headerRows.stream()
                    .map(headerRow -> toReportItem(headerRow, linesBySlipId))
                    .toList();
        }

        String conditionsSummary = buildConditionsSummary(plannedDateFrom, plannedDateTo, status, carrier);

        // CSV出力時はネスト構造をフラット展開（1明細行=1CSV行）
        List<DeliveryListReportItem> exportItems =
                format == ReportFormat.CSV ? flattenForCsv(items) : items;

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
        return reportExportService.export(exportItems, format, meta);
    }

    private DeliveryListReportItem toReportItem(DeliveryListHeaderRow headerRow,
                                                 Map<Long, List<DeliveryListLineRow>> linesBySlipId) {
        Long slipId = headerRow.getId();
        String statusCode = headerRow.getStatus();

        DeliveryListReportItem item = new DeliveryListReportItem();
        item.setSlipNumber(headerRow.getSlipNumber());
        item.setCustomerName(headerRow.getPartnerName());
        item.setDeliveryAddress(headerRow.getAddress());
        item.setPlannedShipDate(headerRow.getPlannedDate());
        item.setStatus(statusCode);
        item.setStatusLabel(OUTBOUND_STATUS_LABELS.getOrDefault(statusCode, statusCode));
        item.setCarrier(headerRow.getCarrier());
        item.setTrackingNumber(headerRow.getTrackingNumber());

        List<DeliveryListLineItem> lines = new ArrayList<>();
        int totalQtyPcs = 0;
        List<DeliveryListLineRow> lineData = linesBySlipId.getOrDefault(slipId, List.of());
        for (DeliveryListLineRow lineRow : lineData) {
            DeliveryListLineItem lineItem = new DeliveryListLineItem();
            lineItem.setProductCode(lineRow.getProductCode());
            lineItem.setProductName(lineRow.getProductName());
            lineItem.setUnitType(lineRow.getUnitType());
            int qty = lineRow.getOrderedQty() != null ? lineRow.getOrderedQty() : 0;
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
     * CSV出力用にネスト構造をフラット展開する。
     * 1伝票×N明細 → N行（伝票情報を繰り返し、各行に1明細を設定）。
     * 明細0件の伝票はヘッダーのみ1行で出力する。
     */
    static List<DeliveryListReportItem> flattenForCsv(List<DeliveryListReportItem> items) {
        List<DeliveryListReportItem> flat = new ArrayList<>();
        for (DeliveryListReportItem item : items) {
            List<DeliveryListLineItem> lines = item.getLines();
            if (lines == null || lines.isEmpty()) {
                flat.add(item);
            } else {
                for (DeliveryListLineItem line : lines) {
                    DeliveryListReportItem row = new DeliveryListReportItem();
                    row.setSlipNumber(item.getSlipNumber());
                    row.setCustomerName(item.getCustomerName());
                    row.setDeliveryAddress(item.getDeliveryAddress());
                    row.setPlannedShipDate(item.getPlannedShipDate());
                    row.setStatus(item.getStatus());
                    row.setStatusLabel(item.getStatusLabel());
                    row.setCarrier(item.getCarrier());
                    row.setTrackingNumber(item.getTrackingNumber());
                    row.setTotalQuantityCas(item.getTotalQuantityCas());
                    row.setTotalQuantityPcs(item.getTotalQuantityPcs());
                    row.setLines(List.of(line));
                    flat.add(row);
                }
            }
        }
        return flat;
    }

    /**
     * CSV出力時の行マッパー。flattenForCsv で事前展開済みのため、
     * lines[0] を安全に参照できる。
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
