package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.master.entity.Location;
import com.wms.master.entity.Product;
import com.wms.master.service.LocationService;
import com.wms.master.service.ProductService;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryBreakdownService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final LocationService locationService;
    private final ProductService productService;

    public record BreakdownResult(
            Long fromInventoryId, Long toInventoryId,
            String productCode, String productName,
            String fromUnitType, String toUnitType,
            int breakdownQty, int convertedQty,
            int fromQuantityAfter, int toQuantityAfter) {}

    @Transactional
    public BreakdownResult breakdown(Long fromLocationId, Long productId,
                                      String fromUnitType, int breakdownQty,
                                      String toUnitType, Long toLocationId) {
        // バリデーション
        if (breakdownQty < 1) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR", "ばらし数量は1以上を指定してください");
        }
        if ("PIECE".equals(fromUnitType)) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR", "PIECEからのばらしはできません");
        }

        // 荷姿順序チェック
        int fromRank = unitRank(fromUnitType);
        int toRank = unitRank(toUnitType);
        if (fromRank <= toRank) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "荷姿の大小関係が不正です（CASE > BALL > PIECE）");
        }

        // ロケーション存在・棚卸ロックチェック
        Location fromLocation = locationService.findById(fromLocationId);
        Location toLocation = fromLocationId.equals(toLocationId) ? fromLocation : locationService.findById(toLocationId);

        if (Boolean.TRUE.equals(fromLocation.getIsStocktakingLocked())) {
            throw new BusinessRuleViolationException("INVENTORY_STOCKTAKE_IN_PROGRESS",
                    "ばらし元ロケーションが棚卸ロック中です");
        }
        if (!fromLocationId.equals(toLocationId) && Boolean.TRUE.equals(toLocation.getIsStocktakingLocked())) {
            throw new BusinessRuleViolationException("INVENTORY_STOCKTAKE_IN_PROGRESS",
                    "ばらし先ロケーションが棚卸ロック中です");
        }

        // 商品存在チェック・変換レート計算
        Product product = productService.findById(productId);
        int conversionRate = getConversionRate(fromUnitType, toUnitType, product);
        int convertedQty = breakdownQty * conversionRate;

        // ばらし元在庫ロック
        Inventory fromInv = inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        fromLocationId, productId, fromUnitType, null, null)
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "ばらし元に対象在庫が存在しません"));

        Inventory lockedFrom = inventoryRepository.findByIdForUpdate(fromInv.getId())
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "ばらし元在庫が見つかりません"));

        // 有効在庫チェック
        int available = lockedFrom.getQuantity() - lockedFrom.getAllocatedQty();
        if (available < breakdownQty) {
            throw new BusinessRuleViolationException("INVENTORY_INSUFFICIENT", "在庫が不足しています");
        }

        // ばらし元更新
        lockedFrom.setQuantity(lockedFrom.getQuantity() - breakdownQty);
        inventoryRepository.save(lockedFrom);

        // ばらし先UPSERT
        Inventory toInv = inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        toLocationId, productId, toUnitType, lockedFrom.getLotNumber(), lockedFrom.getExpiryDate())
                .orElse(null);

        if (toInv != null) {
            Inventory lockedTo = inventoryRepository.findByIdForUpdate(toInv.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND", "ばらし先在庫が見つかりません"));
            lockedTo.setQuantity(lockedTo.getQuantity() + convertedQty);
            inventoryRepository.save(lockedTo);
            toInv = lockedTo;
        } else {
            toInv = Inventory.builder()
                    .warehouseId(fromLocation.getWarehouseId())
                    .locationId(toLocationId)
                    .productId(productId)
                    .unitType(toUnitType)
                    .lotNumber(lockedFrom.getLotNumber())
                    .expiryDate(lockedFrom.getExpiryDate())
                    .quantity(convertedQty)
                    .allocatedQty(0)
                    .build();
            toInv = inventoryRepository.save(toInv);
        }

        // inventory_movements
        Long userId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        inventoryMovementRepository.save(InventoryMovement.builder()
                .warehouseId(fromLocation.getWarehouseId())
                .locationId(fromLocationId).locationCode(fromLocation.getLocationCode())
                .productId(productId).productCode(product.getProductCode()).productName(product.getProductName())
                .unitType(fromUnitType).movementType("BREAKDOWN_OUT")
                .quantity(-breakdownQty).quantityAfter(lockedFrom.getQuantity())
                .executedAt(now).executedBy(userId).build());

        inventoryMovementRepository.save(InventoryMovement.builder()
                .warehouseId(fromLocation.getWarehouseId())
                .locationId(toLocationId).locationCode(toLocation.getLocationCode())
                .productId(productId).productCode(product.getProductCode()).productName(product.getProductName())
                .unitType(toUnitType).movementType("BREAKDOWN_IN")
                .quantity(convertedQty).quantityAfter(toInv.getQuantity())
                .executedAt(now).executedBy(userId).build());

        log.info("Inventory breakdown: productId={}, {}x{} → {}x{}, from={} to={}",
                productId, fromUnitType, breakdownQty, toUnitType, convertedQty,
                fromLocation.getLocationCode(), toLocation.getLocationCode());

        return new BreakdownResult(lockedFrom.getId(), toInv.getId(),
                product.getProductCode(), product.getProductName(),
                fromUnitType, toUnitType, breakdownQty, convertedQty,
                lockedFrom.getQuantity(), toInv.getQuantity());
    }

    int getConversionRate(String fromUnitType, String toUnitType, Product product) {
        if ("CASE".equals(fromUnitType) && "BALL".equals(toUnitType)) {
            return product.getCaseQuantity();
        }
        if ("CASE".equals(fromUnitType) && "PIECE".equals(toUnitType)) {
            return product.getCaseQuantity() * product.getBallQuantity();
        }
        if ("BALL".equals(fromUnitType) && "PIECE".equals(toUnitType)) {
            return product.getBallQuantity();
        }
        throw new BusinessRuleViolationException("VALIDATION_ERROR",
                "無効な荷姿変換です (" + fromUnitType + " → " + toUnitType + ")");
    }

    private int unitRank(String unitType) {
        return switch (unitType) {
            case "CASE" -> 3;
            case "BALL" -> 2;
            case "PIECE" -> 1;
            default -> 0;
        };
    }

    private Long getCurrentUserId() {
        WmsUserDetails ud = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ud.getUserId();
    }
}
