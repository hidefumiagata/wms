package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    /**
     * 在庫をロールバックする（入荷キャンセル時）。
     * 在庫数量を減算し、INBOUND_CANCEL移動記録を作成する。
     */
    @Transactional
    public void rollbackInboundStock(Long warehouseId, Long locationId, String locationCode,
                                      Long productId, String productCode, String productName,
                                      String unitType, String lotNumber, LocalDate expiryDate,
                                      int rollbackQty, Long referenceId, Long userId,
                                      OffsetDateTime executedAt) {
        Inventory inventory = inventoryRepository
                .findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        locationId, productId, unitType, lotNumber, expiryDate)
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "在庫が見つかりません (locationId=" + locationId + ", productId=" + productId + ")"));

        int newQty = inventory.getQuantity() - rollbackQty;
        if (newQty < 0) {
            throw new BusinessRuleViolationException("INVENTORY_INSUFFICIENT",
                    "在庫ロールバックで在庫数が負になります (inventoryId=" + inventory.getId()
                            + ", quantity=" + inventory.getQuantity()
                            + ", rollback=" + rollbackQty + ")");
        }
        if (newQty < inventory.getAllocatedQty()) {
            throw new BusinessRuleViolationException("INVENTORY_ALLOCATED",
                    "引当済み数量が在庫ロールバック後の数量を超えます (inventoryId=" + inventory.getId()
                            + ", allocatedQty=" + inventory.getAllocatedQty()
                            + ", newQuantity=" + newQty + ")");
        }
        inventory.setQuantity(newQty);
        inventoryRepository.save(inventory);

        InventoryMovement movement = InventoryMovement.builder()
                .warehouseId(warehouseId)
                .locationId(locationId)
                .locationCode(locationCode)
                .productId(productId)
                .productCode(productCode)
                .productName(productName)
                .unitType(unitType)
                .lotNumber(lotNumber)
                .expiryDate(expiryDate)
                .movementType("INBOUND_CANCEL")
                .quantity(-rollbackQty)
                .quantityAfter(newQty)
                .referenceId(referenceId)
                .referenceType("INBOUND_SLIP")
                .executedAt(executedAt)
                .executedBy(userId)
                .build();
        inventoryMovementRepository.save(movement);

        log.info("Inventory rollback: locationId={}, productId={}, qty=-{}, after={}",
                locationId, productId, rollbackQty, newQty);
    }
}
