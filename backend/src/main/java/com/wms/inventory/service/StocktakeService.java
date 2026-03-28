package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.entity.StocktakeLine;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.inventory.repository.StocktakeLineRepository;
import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.entity.Location;
import com.wms.master.entity.Product;
import com.wms.master.repository.LocationRepository;
import com.wms.master.service.AreaService;
import com.wms.master.service.BuildingService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StocktakeService {

    private static final int MAX_STOCKTAKE_LINES = 2000;

    private final StocktakeHeaderRepository stocktakeHeaderRepository;
    private final StocktakeLineRepository stocktakeLineRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final LocationRepository locationRepository;
    private final WarehouseService warehouseService;
    private final BuildingService buildingService;
    private final AreaService areaService;
    private final ProductService productService;

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
                throw new ResourceNotFoundException("AREA_NOT_FOUND",
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
                .buildingId(buildingId)
                .targetDescription(targetDescription)
                .stocktakeDate(stocktakeDate)
                .status("STARTED")
                .note(note)
                .startedAt(now)
                .startedBy(userId)
                .build();

        // 在庫スナップショット取得（ロケーションIDでフィルタ）
        List<Long> locationIds = locations.stream().map(Location::getId).toList();
        List<Inventory> inventories = inventoryRepository.findByLocationIdsWithPositiveQty(locationIds);

        // 2000行上限チェック
        if (inventories.size() > MAX_STOCKTAKE_LINES) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "棚卸対象の在庫明細が" + MAX_STOCKTAKE_LINES + "行を超えています。エリアを絞ってください");
        }

        // 商品情報バッチ取得
        Set<Long> productIds = inventories.stream().map(Inventory::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // ロケーションコードマップ
        Map<Long, String> locCodeMap = locations.stream()
                .collect(Collectors.toMap(Location::getId, Location::getLocationCode));

        // 明細作成
        for (Inventory inv : inventories) {
            Product product = productMap.get(inv.getProductId());
            StocktakeLine line = StocktakeLine.builder()
                    .locationId(inv.getLocationId())
                    .locationCode(locCodeMap.getOrDefault(inv.getLocationId(), ""))
                    .productId(inv.getProductId())
                    .productCode(product != null ? product.getProductCode() : "")
                    .productName(product != null ? product.getProductName() : "")
                    .unitType(inv.getUnitType())
                    .lotNumber(inv.getLotNumber())
                    .expiryDate(inv.getExpiryDate())
                    .quantityBefore(inv.getQuantity())
                    .isCounted(false)
                    .build();
            header.addLine(line);
        }

        StocktakeHeader saved = stocktakeHeaderRepository.save(header);

        // ロケーション棚卸ロック
        for (Location loc : locations) {
            loc.setIsStocktakingLocked(true);
        }
        locationRepository.saveAll(locations);

        log.info("Stocktake started: number={}, warehouse={}, target={}, lines={}",
                stocktakeNumber, warehouseId, targetDescription, inventories.size());

        return new StartResult(saved.getId(), stocktakeNumber, targetDescription,
                "STARTED", inventories.size(), now);
    }

    // --- INV-014: 棚卸実数入力 ---

    public record InputResult(int updatedCount, long totalLines, long countedLines) {}

    @Transactional
    public InputResult saveStocktakeLines(Long headerId, java.util.List<LineInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR", "明細を1件以上指定してください");
        }

        StocktakeHeader header = stocktakeHeaderRepository.findById(headerId)
                .orElseThrow(() -> new ResourceNotFoundException("STOCKTAKE_NOT_FOUND",
                        "棚卸が見つかりません (id=" + headerId + ")"));

        if (!"STARTED".equals(header.getStatus())) {
            throw new com.wms.shared.exception.InvalidStateTransitionException("STOCKTAKE_INVALID_STATUS",
                    "棚卸が実施中ではありません (status=" + header.getStatus() + ")");
        }

        Long userId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();
        int updated = 0;

        for (LineInput input : inputs) {
            if (input.actualQty() < 0) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "実数は0以上を指定してください");
            }

            com.wms.inventory.entity.StocktakeLine line = stocktakeLineRepository.findById(input.lineId())
                    .orElseThrow(() -> new ResourceNotFoundException("STOCKTAKE_LINE_NOT_FOUND",
                            "棚卸明細が見つかりません (lineId=" + input.lineId() + ")"));

            if (!line.getStocktakeHeader().getId().equals(headerId)) {
                throw new ResourceNotFoundException("STOCKTAKE_LINE_NOT_FOUND",
                        "棚卸明細が指定棚卸に属していません (lineId=" + input.lineId() + ")");
            }

            line.setQuantityCounted(input.actualQty());
            line.setCounted(true);
            line.setCountedAt(now);
            line.setCountedBy(userId);
            updated++;
        }

        stocktakeHeaderRepository.save(header);

        long totalLines = stocktakeLineRepository.countByHeaderId(headerId);
        long countedLines = stocktakeLineRepository.countCountedByHeaderId(headerId);

        log.info("Stocktake lines saved: headerId={}, updated={}", headerId, updated);

        return new InputResult(updated, totalLines, countedLines);
    }

    public record LineInput(Long lineId, int actualQty) {}

    // --- INV-015: 棚卸確定 ---

    public record ConfirmResult(Long id, String stocktakeNumber, String status,
                                 int totalLines, int adjustedLines, OffsetDateTime confirmedAt) {}

    @Transactional
    public ConfirmResult confirmStocktake(Long headerId) {
        StocktakeHeader header = stocktakeHeaderRepository.findByIdWithLines(headerId)
                .orElseThrow(() -> new ResourceNotFoundException("STOCKTAKE_NOT_FOUND",
                        "棚卸が見つかりません (id=" + headerId + ")"));

        if (!"STARTED".equals(header.getStatus())) {
            throw new com.wms.shared.exception.InvalidStateTransitionException("STOCKTAKE_INVALID_STATUS",
                    "棚卸が実施中ではありません (status=" + header.getStatus() + ")");
        }

        // 全明細入力済みチェック
        boolean allCounted = header.getLines().stream().allMatch(StocktakeLine::isCounted);
        if (!allCounted) {
            throw new com.wms.shared.exception.InvalidStateTransitionException(
                    "INVENTORY_STOCKTAKE_NOT_ALL_COUNTED",
                    "未入力の実数があります");
        }

        Long userId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();
        int adjustedCount = 0;

        // 差異計算
        for (StocktakeLine line : header.getLines()) {
            int diff = line.getQuantityCounted() - line.getQuantityBefore();
            line.setQuantityDiff(diff);
        }

        // 在庫IDを事前収集してID昇順でロック（デッドロック防止）
        java.util.TreeMap<Long, StocktakeLine> invIdToLine = new java.util.TreeMap<>();
        for (StocktakeLine line : header.getLines()) {
            if (line.getQuantityDiff() != 0) {
                inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                                line.getLocationId(), line.getProductId(), line.getUnitType(),
                                line.getLotNumber(), line.getExpiryDate())
                        .ifPresentOrElse(
                                inv -> invIdToLine.put(inv.getId(), line),
                                () -> log.warn("Stocktake confirm: inventory not found for line={}, loc={}, product={}",
                                        line.getId(), line.getLocationCode(), line.getProductCode())
                        );
            }
        }

        // ID昇順で在庫ロック→更新
        for (var entry : invIdToLine.entrySet()) {
            Long invId = entry.getKey();
            StocktakeLine line = entry.getValue();
            Inventory locked = inventoryRepository.findByIdForUpdate(invId).orElse(null);
            if (locked != null) {
                locked.setQuantity(line.getQuantityCounted());
                inventoryRepository.save(locked);
            } else {
                log.warn("Stocktake confirm: failed to lock inventory id={}", invId);
            }

            // movement 記録
            inventoryMovementRepository.save(com.wms.inventory.entity.InventoryMovement.builder()
                    .warehouseId(header.getWarehouseId())
                    .locationId(line.getLocationId())
                    .locationCode(line.getLocationCode())
                    .productId(line.getProductId())
                    .productCode(line.getProductCode())
                    .productName(line.getProductName())
                    .unitType(line.getUnitType())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .movementType("STOCKTAKE_ADJUSTMENT")
                    .quantity(line.getQuantityDiff())
                    .quantityAfter(line.getQuantityCounted())
                    .referenceId(header.getId())
                    .referenceType("STOCKTAKE_HEADER")
                    .executedAt(now)
                    .executedBy(userId)
                    .build());

            adjustedCount++;
        }

        // ロケーション棚卸ロック解除
        Set<Long> locationIds = header.getLines().stream()
                .map(StocktakeLine::getLocationId)
                .collect(Collectors.toSet());
        List<Location> locations = locationRepository.findAllById(locationIds);
        for (Location loc : locations) {
            loc.setIsStocktakingLocked(false);
        }
        locationRepository.saveAll(locations);

        // ヘッダ更新
        header.setStatus("CONFIRMED");
        header.setConfirmedAt(now);
        header.setConfirmedBy(userId);
        stocktakeHeaderRepository.save(header);

        log.info("Stocktake confirmed: number={}, adjustedLines={}",
                header.getStocktakeNumber(), adjustedCount);

        return new ConfirmResult(header.getId(), header.getStocktakeNumber(),
                "CONFIRMED", header.getLines().size(), adjustedCount, now);
    }

    private Long getCurrentUserId() {
        WmsUserDetails ud = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ud.getUserId();
    }
}
