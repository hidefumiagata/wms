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
import com.wms.shared.exception.BusinessRuleViolationException;
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

    // --- ネイティブクエリのカラムインデックス定数 ---
    private static final int COL_LOCATION_CODE = 0;
    private static final int COL_AREA_NAME = 1;
    private static final int COL_PRODUCT_CODE = 2;
    private static final int COL_PRODUCT_NAME = 3;
    private static final int COL_UNIT_TYPE = 4;
    private static final int COL_LOT_NUMBER = 5;
    private static final int COL_EXPIRY_DATE = 6;
    private static final int COL_SYSTEM_QUANTITY = 7;
    private static final int COL_ACTUAL_QUANTITY = 8;

    private static final String[] CSV_HEADERS = {
            "ロケーションコード", "エリア名", "商品コード", "商品名",
            "荷姿", "システム在庫数", "実数", "ロット番号", "期限日"
    };

    public ResponseEntity<List<StocktakeListReportItem>> generate(
            Long stocktakeId, Long buildingId, Long areaId,
            Boolean hideBookQty, ReportFormat format) {

        log.info("RPT-10 棚卸リスト生成開始: stocktakeId={}, buildingId={}, format={}",
                stocktakeId, buildingId, format);

        if (stocktakeId == null && buildingId == null) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "stocktakeId または buildingId のどちらか一方を指定してください");
        }

        List<Object[]> rows;
        String warehouseName;
        String conditionsSummary;

        if (stocktakeId != null) {
            StocktakeHeader header = stocktakeHeaderRepository.findById(stocktakeId)
                    .orElseThrow(() -> new ResourceNotFoundException("STOCKTAKE_NOT_FOUND",
                            "棚卸が見つかりません: stocktakeId=" + stocktakeId));
            Warehouse warehouse = warehouseRepository.findById(header.getWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                            "倉庫が見つかりません: warehouseId=" + header.getWarehouseId()));
            warehouseName = warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")";
            conditionsSummary = "棚卸番号: " + header.getStocktakeNumber();
            if (header.getTargetDescription() != null) {
                conditionsSummary += " / 対象: " + header.getTargetDescription();
            }
            rows = stocktakeReportRepository.findStocktakeListByStocktakeId(stocktakeId);
        } else {
            Building building = buildingRepository.findById(buildingId)
                    .orElseThrow(() -> new ResourceNotFoundException("BUILDING_NOT_FOUND",
                            "棟が見つかりません: buildingId=" + buildingId));
            Warehouse warehouse = warehouseRepository.findById(building.getWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                            "倉庫が見つかりません: warehouseId=" + building.getWarehouseId()));
            warehouseName = warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")";
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
                row -> csvRowMapper((StocktakeListReportItem) row)
        );

        log.info("RPT-10 棚卸リスト生成完了: 件数={}", items.size());
        return reportExportService.export(items, format, meta);
    }

    private StocktakeListReportItem toReportItem(Object[] row) {
        StocktakeListReportItem item = new StocktakeListReportItem();
        item.setLocationCode((String) row[COL_LOCATION_CODE]);
        item.setAreaName((String) row[COL_AREA_NAME]);
        item.setProductCode((String) row[COL_PRODUCT_CODE]);
        item.setProductName((String) row[COL_PRODUCT_NAME]);
        item.setUnitType((String) row[COL_UNIT_TYPE]);
        item.setLotNumber((String) row[COL_LOT_NUMBER]);
        item.setExpiryDate(row[COL_EXPIRY_DATE] != null
                ? LocalDate.parse(row[COL_EXPIRY_DATE].toString()) : null);
        item.setSystemQuantity(row[COL_SYSTEM_QUANTITY] != null
                ? ((Number) row[COL_SYSTEM_QUANTITY]).intValue() : null);
        item.setActualQuantity(row[COL_ACTUAL_QUANTITY] != null
                ? ((Number) row[COL_ACTUAL_QUANTITY]).intValue() : null);
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
