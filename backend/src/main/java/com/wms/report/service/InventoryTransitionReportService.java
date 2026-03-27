package com.wms.report.service;

import com.wms.generated.model.InventoryTransitionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.ProductRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InventoryMovementReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.util.BusinessDateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.JST;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-08: 在庫推移レポートサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InventoryTransitionReportService {

    private final InventoryMovementReportRepository movementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final BusinessDateProvider businessDateProvider;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "変動日", "変動種別", "ロケーションコード", "荷姿",
            "変動前数量", "変動数", "変動後数量", "参照番号", "ロット番号"
    };

    private static final Map<String, String> MOVEMENT_TYPE_LABELS = Map.of(
            "INBOUND", "入庫",
            "OUTBOUND", "出庫",
            "MOVE_OUT", "在庫移動出",
            "MOVE_IN", "在庫移動入",
            "BREAKDOWN_OUT", "ばらし出",
            "BREAKDOWN_IN", "ばらし入",
            "CORRECTION", "在庫訂正",
            "STOCKTAKE_ADJUSTMENT", "棚卸調整",
            "INBOUND_CANCEL", "入荷キャンセル"
    );

    public ResponseEntity<List<InventoryTransitionReportItem>> generate(
            Long warehouseId, Long productId, LocalDate dateFrom, LocalDate dateTo,
            ReportFormat format) {

        log.info("RPT-08 在庫推移レポート生成開始: warehouseId={}, productId={}, format={}",
                warehouseId, productId, format);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND",
                        "商品が見つかりません: productId=" + productId));

        LocalDate effectiveDateFrom = dateFrom != null ? dateFrom
                : businessDateProvider.today().withDayOfMonth(1);
        LocalDate effectiveDateTo = dateTo != null ? dateTo
                : businessDateProvider.today();

        OffsetDateTime fromOdt = effectiveDateFrom.atStartOfDay(JST).toOffsetDateTime();
        OffsetDateTime toOdt = effectiveDateTo.plusDays(1).atStartOfDay(JST).toOffsetDateTime();

        List<InventoryMovement> movements = movementRepository.findTransitionReportData(
                warehouseId, productId, fromOdt, toOdt);

        List<InventoryTransitionReportItem> items = movements.stream()
                .map(this::toReportItem)
                .toList();

        String conditionsSummary = "期間: " + fmtDate(effectiveDateFrom) + " ～ " + fmtDate(effectiveDateTo);
        ReportMeta meta = new ReportMeta(
                "在庫推移レポート",
                "rpt-08-inventory-transition",
                "inventory_transition_" + todayFileDate(),
                warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")",
                getCurrentUserName(),
                "対象商品: " + product.getProductCode() + " " + product.getProductName()
                        + " / " + conditionsSummary,
                CSV_HEADERS,
                row -> csvRowMapper((InventoryTransitionReportItem) row)
        );

        log.info("RPT-08 在庫推移レポート生成完了: warehouseId={}, productId={}, 件数={}",
                warehouseId, productId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private InventoryTransitionReportItem toReportItem(InventoryMovement m) {
        LocalDate movementDate = m.getExecutedAt().atZoneSameInstant(JST).toLocalDate();
        int quantityBefore = m.getQuantityAfter() - m.getQuantity();

        InventoryTransitionReportItem item = new InventoryTransitionReportItem();
        item.setMovementDate(movementDate);
        item.setMovementType(m.getMovementType());
        item.setMovementTypeLabel(
                MOVEMENT_TYPE_LABELS.getOrDefault(m.getMovementType(), m.getMovementType()));
        item.setLocationCode(m.getLocationCode());
        item.setUnitType(m.getUnitType());
        item.setQuantityBefore(quantityBefore);
        item.setQuantityChange(m.getQuantity());
        item.setQuantityAfter(m.getQuantityAfter());
        item.setReferenceNumber(m.getReferenceType() != null
                ? m.getReferenceType() + (m.getReferenceId() != null ? "-" + m.getReferenceId() : "")
                : null);
        item.setLotNumber(m.getLotNumber());
        return item;
    }

    private String[] csvRowMapper(InventoryTransitionReportItem item) {
        return new String[]{
                fmtDate(item.getMovementDate()),
                fmtOrDash(item.getMovementTypeLabel()),
                fmtOrDash(item.getLocationCode()),
                fmtOrDash(item.getUnitType()),
                fmtInteger(item.getQuantityBefore()),
                fmtInteger(item.getQuantityChange()),
                fmtInteger(item.getQuantityAfter()),
                fmtOrDash(item.getReferenceNumber()),
                fmtOrDash(item.getLotNumber())
        };
    }
}
