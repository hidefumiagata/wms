package com.wms.report.service;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.StocktakeResultReportItem;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.StocktakeReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.CsvGenerationService.fmtPercent;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-11: 棚卸結果レポートサービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StocktakeResultReportService {

    private final StocktakeReportRepository stocktakeReportRepository;
    private final StocktakeHeaderRepository stocktakeHeaderRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // --- ネイティブクエリのカラムインデックス定数 ---
    private static final int COL_LOCATION_CODE = 0;
    private static final int COL_PRODUCT_CODE = 1;
    private static final int COL_PRODUCT_NAME = 2;
    private static final int COL_UNIT_TYPE = 3;
    private static final int COL_LOT_NUMBER = 4;
    private static final int COL_SYSTEM_QUANTITY = 5;
    private static final int COL_ACTUAL_QUANTITY = 6;

    private static final String[] CSV_HEADERS = {
            "ロケーションコード", "商品コード", "商品名", "荷姿",
            "システム在庫数", "実数", "差異数", "差異率(%)", "ロット番号"
    };

    public ResponseEntity<List<StocktakeResultReportItem>> generate(
            Long stocktakeId, ReportFormat format) {

        log.info("RPT-11 棚卸結果レポート生成開始: stocktakeId={}, format={}", stocktakeId, format);

        StocktakeHeader header = stocktakeHeaderRepository.findById(stocktakeId)
                .orElseThrow(() -> new ResourceNotFoundException("STOCKTAKE_NOT_FOUND",
                        "棚卸が見つかりません: stocktakeId=" + stocktakeId));

        Warehouse warehouse = warehouseRepository.findById(header.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + header.getWarehouseId()));

        String warehouseName = warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")";

        String conditionsSummary = buildConditionsSummary(header);

        List<Object[]> rows = stocktakeReportRepository.findStocktakeResultByStocktakeId(stocktakeId);

        List<StocktakeResultReportItem> items = rows.stream()
                .map(this::toReportItem)
                .toList();

        Map<String, Object> extraVars = buildExtraVars(header, items);

        ReportMeta meta = new ReportMeta(
                "棚卸結果レポート",
                "rpt-11-stocktake-result",
                "stocktake_result_" + todayFileDate(),
                warehouseName,
                getCurrentUserName(),
                conditionsSummary,
                CSV_HEADERS,
                row -> csvRowMapper((StocktakeResultReportItem) row),
                extraVars
        );

        log.info("RPT-11 棚卸結果レポート生成完了: stocktakeId={}, 件数={}", stocktakeId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private String buildConditionsSummary(StocktakeHeader header) {
        StringBuilder sb = new StringBuilder();
        sb.append("棚卸番号: ").append(header.getStocktakeNumber());
        sb.append(" / ステータス: ").append("CONFIRMED".equals(header.getStatus()) ? "確定済" : "棚卸中");
        if (header.getStartedAt() != null) {
            sb.append(" / 開始: ").append(header.getStartedAt().format(DATETIME_FMT));
        }
        if (header.getConfirmedAt() != null) {
            sb.append(" / 確定: ").append(header.getConfirmedAt().format(DATETIME_FMT));
        }
        return sb.toString();
    }

    private Map<String, Object> buildExtraVars(StocktakeHeader header,
                                                List<StocktakeResultReportItem> items) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("stocktakeNumber", header.getStocktakeNumber());
        vars.put("status", "CONFIRMED".equals(header.getStatus()) ? "確定済" : "棚卸中");
        vars.put("startedAt", header.getStartedAt() != null
                ? header.getStartedAt().format(DATETIME_FMT) : "—");
        vars.put("confirmedAt", header.getConfirmedAt() != null
                ? header.getConfirmedAt().format(DATETIME_FMT) : "—");

        // 差異サマリー計算
        int surplusTotal = 0;
        int shortageTotal = 0;
        int diffCount = 0;
        for (StocktakeResultReportItem item : items) {
            if (item.getDiffQuantity() != null && item.getDiffQuantity() != 0) {
                diffCount++;
                if (item.getDiffQuantity() > 0) {
                    surplusTotal += item.getDiffQuantity();
                } else {
                    shortageTotal += item.getDiffQuantity();
                }
            }
        }
        vars.put("surplusTotal", surplusTotal);
        vars.put("shortageTotal", shortageTotal);
        vars.put("diffCount", diffCount);
        vars.put("totalCount", items.size());

        return vars;
    }

    private StocktakeResultReportItem toReportItem(Object[] row) {
        StocktakeResultReportItem item = new StocktakeResultReportItem();
        item.setLocationCode((String) row[COL_LOCATION_CODE]);
        item.setProductCode((String) row[COL_PRODUCT_CODE]);
        item.setProductName((String) row[COL_PRODUCT_NAME]);
        item.setUnitType((String) row[COL_UNIT_TYPE]);
        item.setLotNumber((String) row[COL_LOT_NUMBER]);

        Integer systemQty = row[COL_SYSTEM_QUANTITY] != null
                ? ((Number) row[COL_SYSTEM_QUANTITY]).intValue() : null;
        Integer actualQty = row[COL_ACTUAL_QUANTITY] != null
                ? ((Number) row[COL_ACTUAL_QUANTITY]).intValue() : null;

        item.setSystemQuantity(systemQty != null ? systemQty : 0);
        item.setActualQuantity(actualQty);

        if (actualQty != null) {
            int diff = actualQty - (systemQty != null ? systemQty : 0);
            item.setDiffQuantity(diff);
            if (systemQty != null && systemQty != 0) {
                item.setDiffRate(Math.round(diff * 1000.0 / systemQty) / 10.0);
            }
        }

        return item;
    }

    private String[] csvRowMapper(StocktakeResultReportItem item) {
        String diffQtyStr;
        if (item.getDiffQuantity() == null) {
            diffQtyStr = "\u2014";
        } else if (item.getDiffQuantity() > 0) {
            diffQtyStr = "+" + item.getDiffQuantity();
        } else {
            diffQtyStr = String.valueOf(item.getDiffQuantity());
        }

        return new String[]{
                item.getLocationCode(),
                item.getProductCode(),
                item.getProductName(),
                fmtOrDash(item.getUnitType()),
                fmtInteger(item.getSystemQuantity()),
                fmtInteger(item.getActualQuantity()),
                diffQtyStr,
                fmtPercent(item.getDiffRate()),
                fmtOrDash(item.getLotNumber())
        };
    }
}
