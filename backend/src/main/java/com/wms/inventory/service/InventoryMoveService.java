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

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryMoveService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final LocationService locationService;
    private final ProductService productService;

    public record MoveResult(
            Long fromInventoryId, Long toInventoryId,
            String fromLocationCode, String toLocationCode,
            String productCode, String productName, String unitType,
            int movedQty, int fromQuantityAfter, int toQuantityAfter) {}

    @Transactional
    public MoveResult moveInventory(Long fromLocationId, Long productId, String unitType,
                                     String lotNumber, LocalDate expiryDate,
                                     Long toLocationId, int moveQty) {
        // 移動元 = 移動先チェック
        if (fromLocationId.equals(toLocationId)) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "移動元と移動先が同一です");
        }

        // ロケーション存在チェック
        Location fromLocation = locationService.findById(fromLocationId);
        Location toLocation = locationService.findById(toLocationId);

        // 棚卸ロックチェック
        if (Boolean.TRUE.equals(fromLocation.getIsStocktakingLocked())) {
            throw new BusinessRuleViolationException("INVENTORY_STOCKTAKE_IN_PROGRESS",
                    "移動元ロケーションが棚卸ロック中です");
        }
        if (Boolean.TRUE.equals(toLocation.getIsStocktakingLocked())) {
            throw new BusinessRuleViolationException("INVENTORY_STOCKTAKE_IN_PROGRESS",
                    "移動先ロケーションが棚卸ロック中です");
        }

        // 商品存在チェック
        Product product = productService.findById(productId);

        // 移動元在庫を悲観的ロック
        Inventory fromInv = inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        fromLocationId, productId, unitType, lotNumber, expiryDate)
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "移動元に対象在庫が存在しません"));

        Inventory lockedFrom = inventoryRepository.findByIdForUpdate(fromInv.getId())
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "移動元在庫が見つかりません"));

        // 有効在庫チェック
        int available = lockedFrom.getQuantity() - lockedFrom.getAllocatedQty();
        if (available < moveQty) {
            throw new BusinessRuleViolationException("INVENTORY_INSUFFICIENT",
                    "在庫が不足しています (available=" + available + ", required=" + moveQty + ")");
        }

        // 移動先の単一商品チェック
        if (inventoryRepository.existsByLocationIdAndProductIdNot(toLocationId, productId)) {
            throw new BusinessRuleViolationException("LOCATION_PRODUCT_MISMATCH",
                    "移動先ロケーションに既に別商品の在庫が存在します");
        }

        // 移動元更新
        lockedFrom.setQuantity(lockedFrom.getQuantity() - moveQty);
        inventoryRepository.save(lockedFrom);

        // 移動先UPSERT
        Inventory toInv = inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        toLocationId, productId, unitType, lotNumber, expiryDate)
                .orElse(null);

        if (toInv != null) {
            Inventory lockedTo = inventoryRepository.findByIdForUpdate(toInv.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                            "移動先在庫が見つかりません"));
            lockedTo.setQuantity(lockedTo.getQuantity() + moveQty);
            inventoryRepository.save(lockedTo);
            toInv = lockedTo;
        } else {
            toInv = Inventory.builder()
                    .warehouseId(lockedFrom.getWarehouseId())
                    .locationId(toLocationId)
                    .productId(productId)
                    .unitType(unitType)
                    .lotNumber(lotNumber)
                    .expiryDate(expiryDate)
                    .quantity(moveQty)
                    .allocatedQty(0)
                    .build();
            toInv = inventoryRepository.save(toInv);
        }

        // inventory_movements 記録
        Long currentUserId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        inventoryMovementRepository.save(InventoryMovement.builder()
                .warehouseId(lockedFrom.getWarehouseId())
                .locationId(fromLocationId)
                .locationCode(fromLocation.getLocationCode())
                .productId(productId)
                .productCode(product.getProductCode())
                .productName(product.getProductName())
                .unitType(unitType)
                .movementType("MOVE_OUT")
                .quantity(-moveQty)
                .quantityAfter(lockedFrom.getQuantity())
                .executedAt(now)
                .executedBy(currentUserId)
                .build());

        inventoryMovementRepository.save(InventoryMovement.builder()
                .warehouseId(lockedFrom.getWarehouseId())
                .locationId(toLocationId)
                .locationCode(toLocation.getLocationCode())
                .productId(productId)
                .productCode(product.getProductCode())
                .productName(product.getProductName())
                .unitType(unitType)
                .movementType("MOVE_IN")
                .quantity(moveQty)
                .quantityAfter(toInv.getQuantity())
                .executedAt(now)
                .executedBy(currentUserId)
                .build());

        log.info("Inventory moved: productId={}, unitType={}, qty={}, from={} to={}",
                productId, unitType, moveQty, fromLocation.getLocationCode(), toLocation.getLocationCode());

        return new MoveResult(
                lockedFrom.getId(), toInv.getId(),
                fromLocation.getLocationCode(), toLocation.getLocationCode(),
                product.getProductCode(), product.getProductName(), unitType,
                moveQty, lockedFrom.getQuantity(), toInv.getQuantity());
    }

    private Long getCurrentUserId() {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }
}
