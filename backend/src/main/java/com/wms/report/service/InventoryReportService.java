package com.wms.report.service;

import com.wms.generated.model.InventoryReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.UnitType;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InventoryReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-07: 在庫一覧レポートサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InventoryReportService {

    private final InventoryReportRepository inventoryReportRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "ロケーションコード", "棟名", "エリア名", "商品コード", "商品名",
            "荷姿", "在庫数量", "ロット番号", "期限日"
    };

    public ResponseEntity<List<InventoryReportItem>> generate(
            Long warehouseId, String locationCodePrefix, Long productId,
            UnitType unitType, StorageCondition storageCondition, ReportFormat format) {

        log.info("RPT-07 在庫一覧レポート生成開始: warehouseId={}, format={}", warehouseId, format);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        String unitTypeStr = unitType != null ? unitType.getValue() : null;
        String storageConditionStr = storageCondition != null ? storageCondition.getValue() : null;

        List<Object[]> rows = inventoryReportRepository.findInventoryReportData(
                warehouseId, locationCodePrefix, productId, unitTypeStr, storageConditionStr);

        List<InventoryReportItem> items = rows.stream()
                .map(this::toReportItem)
                .toList();

        String conditionsSummary = buildConditionsSummary(locationCodePrefix, unitType, storageCondition);
        ReportMeta meta = new ReportMeta(
                "在庫一覧レポート",
                "rpt-07-inventory",
                "inventory_" + todayFileDate(),
                warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")",
                getCurrentUserName(),
                conditionsSummary,
                CSV_HEADERS,
                row -> csvRowMapper((InventoryReportItem) row)
        );

        log.info("RPT-07 在庫一覧レポート生成完了: warehouseId={}, 件数={}", warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private InventoryReportItem toReportItem(Object[] row) {
        int quantity = ((Number) row[7]).intValue();
        int allocatedQty = ((Number) row[8]).intValue();

        InventoryReportItem item = new InventoryReportItem();
        item.setLocationCode((String) row[11]);
        item.setBuildingName((String) row[12]);
        item.setAreaName((String) row[13]);
        item.setProductCode((String) row[14]);
        item.setProductName((String) row[15]);
        item.setUnitType((String) row[4]);
        item.setQuantity(quantity);
        item.setAllocatedQty(allocatedQty);
        item.setAvailableQty(quantity - allocatedQty);
        item.setLotNumber((String) row[5]);
        item.setExpiryDate(row[6] != null ? LocalDate.parse(row[6].toString()) : null);
        return item;
    }

    private String buildConditionsSummary(String locationCodePrefix, UnitType unitType,
                                           StorageCondition storageCondition) {
        StringBuilder sb = new StringBuilder();
        if (locationCodePrefix != null) {
            sb.append("ロケーション: ").append(locationCodePrefix).append("*");
        }
        if (unitType != null) {
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append("荷姿: ").append(unitType.getValue());
        }
        if (storageCondition != null) {
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append("保管条件: ").append(storageCondition.getValue());
        }
        return sb.toString();
    }

    private String[] csvRowMapper(InventoryReportItem item) {
        return new String[]{
                item.getLocationCode(),
                item.getBuildingName(),
                item.getAreaName(),
                item.getProductCode(),
                item.getProductName(),
                fmtOrDash(item.getUnitType()),
                fmtInteger(item.getQuantity()),
                fmtOrDash(item.getLotNumber()),
                fmtDate(item.getExpiryDate())
        };
    }
}
