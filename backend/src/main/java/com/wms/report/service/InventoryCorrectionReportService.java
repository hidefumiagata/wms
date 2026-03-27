package com.wms.report.service;

import com.wms.generated.model.InventoryCorrectionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.report.repository.InventoryMovementReportRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.util.BusinessDateProvider;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-09: 在庫訂正一覧サービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InventoryCorrectionReportService {

    private final InventoryMovementReportRepository movementRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final BusinessDateProvider businessDateProvider;
    private final ReportExportService reportExportService;

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private static final String[] CSV_HEADERS = {
            "訂正日", "ロケーションコード", "商品コード", "商品名", "荷姿",
            "訂正前数量", "訂正後数量", "変動数", "訂正理由", "実施者"
    };

    public ResponseEntity<List<InventoryCorrectionReportItem>> generate(
            Long warehouseId, LocalDate correctionDateFrom, LocalDate correctionDateTo,
            ReportFormat format) {

        log.info("RPT-09 在庫訂正一覧生成開始: warehouseId={}, format={}", warehouseId, format);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                        "倉庫が見つかりません: warehouseId=" + warehouseId));

        LocalDate effectiveDateFrom = correctionDateFrom != null ? correctionDateFrom
                : businessDateProvider.today().withDayOfMonth(1);
        LocalDate effectiveDateTo = correctionDateTo != null ? correctionDateTo
                : businessDateProvider.today();

        OffsetDateTime fromOdt = effectiveDateFrom.atStartOfDay(JST).toOffsetDateTime();
        OffsetDateTime toOdt = effectiveDateTo.plusDays(1).atStartOfDay(JST).toOffsetDateTime();

        List<InventoryMovement> movements = movementRepository.findCorrectionReportData(
                warehouseId, fromOdt, toOdt);

        // 実施者名のバルクロード
        Map<Long, User> userMap = loadUserMap(movements);

        List<InventoryCorrectionReportItem> items = movements.stream()
                .map(m -> toReportItem(m, userMap))
                .toList();

        String conditionsSummary = "期間: " + fmtDate(effectiveDateFrom) + " ～ " + fmtDate(effectiveDateTo);
        ReportMeta meta = new ReportMeta(
                "在庫訂正一覧",
                "rpt-09-inventory-correction",
                "inventory_correction_" + todayFileDate(),
                warehouse.getWarehouseName() + " (" + warehouse.getWarehouseCode() + ")",
                getCurrentUserName(),
                conditionsSummary,
                CSV_HEADERS,
                row -> csvRowMapper((InventoryCorrectionReportItem) row)
        );

        log.info("RPT-09 在庫訂正一覧生成完了: warehouseId={}, 件数={}", warehouseId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private InventoryCorrectionReportItem toReportItem(InventoryMovement m, Map<Long, User> userMap) {
        LocalDate correctionDate = m.getExecutedAt().atZoneSameInstant(JST).toLocalDate();
        int quantityBefore = m.getQuantityAfter() - m.getQuantity();

        User user = userMap.get(m.getExecutedBy());
        String operatorName = user != null ? user.getFullName() : null;

        InventoryCorrectionReportItem item = new InventoryCorrectionReportItem();
        item.setCorrectionDate(correctionDate);
        item.setLocationCode(m.getLocationCode());
        item.setProductCode(m.getProductCode());
        item.setProductName(m.getProductName());
        item.setUnitType(m.getUnitType());
        item.setQuantityBefore(quantityBefore);
        item.setQuantityAfter(m.getQuantityAfter());
        item.setQuantityChange(m.getQuantity());
        item.setReason(m.getCorrectionReason());
        item.setOperatorName(operatorName);
        return item;
    }

    private Map<Long, User> loadUserMap(List<InventoryMovement> movements) {
        List<Long> userIds = movements.stream()
                .map(InventoryMovement::getExecutedBy)
                .distinct()
                .toList();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String[] csvRowMapper(InventoryCorrectionReportItem item) {
        return new String[]{
                fmtDate(item.getCorrectionDate()),
                fmtOrDash(item.getLocationCode()),
                item.getProductCode(),
                item.getProductName(),
                fmtOrDash(item.getUnitType()),
                fmtInteger(item.getQuantityBefore()),
                fmtInteger(item.getQuantityAfter()),
                fmtInteger(item.getQuantityChange()),
                fmtOrDash(item.getReason()),
                fmtOrDash(item.getOperatorName())
        };
    }
}
