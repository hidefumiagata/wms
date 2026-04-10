package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.StocktakeListReportItem;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.master.entity.Building;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.BuildingRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.StocktakeReportRepository;
import com.wms.report.repository.projection.StocktakeListRow;
import com.wms.shared.exception.BusinessRuleViolationException;
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
import static com.wms.report.service.ReportServiceUtils.formatWarehouseName;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-10: 棚卸リストサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StocktakeListReportService {

    private final StocktakeReportRepository stocktakeReportRepository;
    private final StocktakeHeaderRepository stocktakeHeaderRepository;
    private final BuildingRepository buildingRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "ロケーションコード", "エリア名", "商品コード", "商品名",
            "荷姿", "システム在庫数", "実数", "ロット番号", "期限日"
    };

    public ResponseEntity<List<StocktakeListReportItem>> generate(
            Long stocktakeId, Long buildingId, Long areaId,
            Boolean hideBookQty, ReportFormat format) {

        log.info("RPT-10 棚卸リスト生成開始: stocktakeId={}, buildingId={}, hideBookQty={}, format={}",
                stocktakeId, buildingId, hideBookQty, format);

        if (stocktakeId == null && buildingId == null) {
            throw new BusinessRuleViolationException("REPORT_PARAMETER_REQUIRED",
                    "stocktakeId または buildingId のどちらか一方を指定してください");
        }

        List<StocktakeListRow> rows;
        String warehouseName;
        String conditionsSummary;

        if (stocktakeId != null) {
            StocktakeHeader header = stocktakeHeaderRepository.findById(stocktakeId)
                    .orElseThrow(() -> ResourceNotFoundException.of("STOCKTAKE_NOT_FOUND", "棚卸", stocktakeId));
            Warehouse warehouse = warehouseRepository.findById(header.getWarehouseId())
                    .orElseThrow(() -> ResourceNotFoundException.of("WAREHOUSE_NOT_FOUND", "倉庫", header.getWarehouseId()));
            warehouseName = formatWarehouseName(warehouse);
            conditionsSummary = "棚卸番号: " + header.getStocktakeNumber();
            if (header.getTargetDescription() != null) {
                conditionsSummary += " / 対象: " + header.getTargetDescription();
            }
            rows = stocktakeReportRepository.findStocktakeListByStocktakeId(stocktakeId);
        } else {
            Building building = buildingRepository.findById(buildingId)
                    .orElseThrow(() -> ResourceNotFoundException.of("BUILDING_NOT_FOUND", "棟", buildingId));
            Warehouse warehouse = warehouseRepository.findById(building.getWarehouseId())
                    .orElseThrow(() -> ResourceNotFoundException.of("WAREHOUSE_NOT_FOUND", "倉庫", building.getWarehouseId()));
            warehouseName = formatWarehouseName(warehouse);
            conditionsSummary = "棟: " + building.getBuildingName() + " (" + building.getBuildingCode() + ") [プレビュー]";
            rows = stocktakeReportRepository.findStocktakeListByBuildingId(buildingId, areaId);
        }

        List<StocktakeListReportItem> items = rows.stream()
                .map(this::toReportItem)
                .toList();

        ReportMeta meta = new ReportMeta(
                "棚卸リスト",
                "rpt-10-stocktake-list",
                "stocktake_list_" + todayFileDate(),
                warehouseName,
                getCurrentUserName(),
                conditionsSummary,
                CSV_HEADERS,
                row -> csvRowMapper((StocktakeListReportItem) row),
                Map.of("hideBookQty", !Boolean.FALSE.equals(hideBookQty))
        );

        log.info("RPT-10 棚卸リスト生成完了: 件数={}", items.size());
        return reportExportService.export(items, format, meta);
    }

    private StocktakeListReportItem toReportItem(StocktakeListRow row) {
        StocktakeListReportItem item = new StocktakeListReportItem();
        item.setLocationCode(row.getLocationCode());
        item.setAreaName(row.getAreaName());
        item.setProductCode(row.getProductCode());
        item.setProductName(row.getProductName());
        item.setUnitType(row.getUnitType());
        item.setLotNumber(row.getLotNumber());
        item.setExpiryDate(row.getExpiryDate());
        item.setSystemQuantity(row.getSystemQuantity());
        item.setActualQuantity(row.getActualQuantity());
        return item;
    }

    private String[] csvRowMapper(StocktakeListReportItem item) {
        return new String[]{
                item.getLocationCode(),
                item.getAreaName(),
                item.getProductCode(),
                item.getProductName(),
                fmtOrDash(item.getUnitType()),
                fmtInteger(item.getSystemQuantity()),
                fmtInteger(item.getActualQuantity()),
                fmtOrDash(item.getLotNumber()),
                fmtDate(item.getExpiryDate())
        };
    }
}
