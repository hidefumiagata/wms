package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.entity.StocktakeLine;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.entity.Location;
import com.wms.master.repository.LocationRepository;
import com.wms.master.service.AreaService;
import com.wms.master.service.BuildingService;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.security.WmsUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StocktakeService {

    private final StocktakeHeaderRepository stocktakeHeaderRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final WarehouseService warehouseService;
    private final BuildingService buildingService;
    private final AreaService areaService;

    public record StartResult(Long id, String stocktakeNumber, String targetDescription,
                               String status, int totalLines, OffsetDateTime startedAt) {}

    @Transactional
    public StartResult startStocktake(Long warehouseId, Long buildingId, Long areaId,
                                       LocalDate stocktakeDate, String note) {
        // 存在チェック
        warehouseService.findById(warehouseId);
        Building building = buildingService.findById(buildingId);

        Area area = null;
        if (areaId != null) {
            area = areaService.findById(areaId);
            if (!area.getBuildingId().equals(buildingId)) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "指定エリアは指定棟に属していません");
            }
        }

        // 対象ロケーション取得
        List<Location> locations = locationRepository.findActiveByWarehouseAndBuilding(
                warehouseId, buildingId, areaId);

        if (locations.isEmpty()) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "対象ロケーションが存在しません");
        }

        // 棚卸ロックチェック
        for (Location loc : locations) {
            if (Boolean.TRUE.equals(loc.getIsStocktakingLocked())) {
                throw new BusinessRuleViolationException("INVENTORY_STOCKTAKE_IN_PROGRESS",
                        "ロケーション " + loc.getLocationCode() + " が既に棚卸ロック中です");
            }
        }

        // targetDescription 生成
        String targetDescription = area != null
                ? building.getBuildingName() + " " + area.getAreaName()
                : building.getBuildingName() + " 全エリア";

        // 棚卸番号採番
        String year = String.valueOf(stocktakeDate.getYear());
        Integer maxSeq = stocktakeHeaderRepository.findMaxSequenceByYear(year);
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        String stocktakeNumber = String.format("ST-%s-%05d", year, nextSeq);

        // ヘッダ作成
        Long userId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        StocktakeHeader header = StocktakeHeader.builder()
                .stocktakeNumber(stocktakeNumber)
                .warehouseId(warehouseId)
                .targetDescription(targetDescription)
                .stocktakeDate(stocktakeDate)
                .status("STARTED")
                .note(note)
                .startedAt(now)
                .startedBy(userId)
                .build();

        // 在庫スナップショット → 明細作成
        List<Long> locationIds = locations.stream().map(Location::getId).toList();
        int lineCount = 0;
        for (Long locId : locationIds) {
            Location loc = locations.stream().filter(l -> l.getId().equals(locId)).findFirst().orElse(null);
            if (loc == null) continue;

            List<Inventory> inventories = inventoryRepository.findAll().stream()
                    .filter(inv -> inv.getLocationId().equals(locId) && inv.getQuantity() > 0)
                    .toList();

            for (Inventory inv : inventories) {
                StocktakeLine line = StocktakeLine.builder()
                        .locationId(locId)
                        .locationCode(loc.getLocationCode())
                        .productId(inv.getProductId())
                        .productCode("") // 後で解決
                        .productName("")
                        .unitType(inv.getUnitType())
                        .lotNumber(inv.getLotNumber())
                        .expiryDate(inv.getExpiryDate())
                        .quantityBefore(inv.getQuantity())
                        .isCounted(false)
                        .build();
                header.addLine(line);
                lineCount++;
            }
        }

        StocktakeHeader saved = stocktakeHeaderRepository.save(header);

        // ロケーション棚卸ロック
        for (Location loc : locations) {
            loc.setIsStocktakingLocked(true);
        }
        locationRepository.saveAll(locations);

        log.info("Stocktake started: number={}, warehouse={}, target={}, lines={}",
                stocktakeNumber, warehouseId, targetDescription, lineCount);

        return new StartResult(saved.getId(), stocktakeNumber, targetDescription,
                "STARTED", lineCount, now);
    }

    private Long getCurrentUserId() {
        WmsUserDetails ud = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ud.getUserId();
    }
}
