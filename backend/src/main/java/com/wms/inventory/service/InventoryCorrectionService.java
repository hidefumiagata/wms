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
public class InventoryCorrectionService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final LocationService locationService;
    private final ProductService productService;

    public record CorrectionResult(
            Long inventoryId, String locationCode,
            String productCode, String productName, String unitType,
            int quantityBefore, int quantityAfter, String reason) {}

    @Transactional
    public CorrectionResult correct(Long locationId, Long productId, String unitType,
                                     String lotNumber, LocalDate expiryDate,
                                     int newQty, String reason) {
        // バリデーション
        if (newQty < 0) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR", "訂正後数量は0以上を指定してください");
        }
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR", "訂正理由は1〜200文字で入力してください");
        }

        // ロケーション・棚卸ロック
        Location location = locationService.findById(locationId);
        if (Boolean.TRUE.equals(location.getIsStocktakingLocked())) {
            throw new BusinessRuleViolationException("INVENTORY_STOCKTAKE_IN_PROGRESS",
                    "ロケーションが棚卸ロック中です");
        }

        // 商品
        Product product = productService.findById(productId);

        // 在庫ロック
        Inventory inv = inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        locationId, productId, unitType, lotNumber, expiryDate)
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "対象在庫が存在しません"));

        Inventory locked = inventoryRepository.findByIdForUpdate(inv.getId())
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "在庫が見つかりません"));

        // 引当数チェック
        if (newQty < locked.getAllocatedQty()) {
            throw new BusinessRuleViolationException("CORRECTION_BELOW_ALLOCATED",
                    "訂正後の数量が引当数を下回っています");
        }

        int quantityBefore = locked.getQuantity();
        locked.setQuantity(newQty);
        inventoryRepository.save(locked);

        // movement記録
        Long userId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        inventoryMovementRepository.save(InventoryMovement.builder()
                .warehouseId(location.getWarehouseId())
                .locationId(locationId).locationCode(location.getLocationCode())
                .productId(productId).productCode(product.getProductCode()).productName(product.getProductName())
                .unitType(unitType).movementType("CORRECTION")
                .quantity(newQty - quantityBefore)
                .quantityAfter(newQty)
                .correctionReason(reason)
                .executedAt(now).executedBy(userId).build());

        log.info("Inventory corrected: inventoryId={}, before={}, after={}, reason={}",
                locked.getId(), quantityBefore, newQty, reason);

        return new CorrectionResult(locked.getId(), location.getLocationCode(),
                product.getProductCode(), product.getProductName(), unitType,
                quantityBefore, newQty, reason);
    }

    private Long getCurrentUserId() {
        WmsUserDetails ud = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ud.getUserId();
    }
}
