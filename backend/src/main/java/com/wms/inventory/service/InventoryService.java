package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    public record StoreInboundCommand(
            Long warehouseId, Long locationId, String locationCode,
            Long productId, String productCode, String productName,
            String unitType, String lotNumber, LocalDate expiryDate,
            int quantity, Long referenceId, Long userId, OffsetDateTime executedAt) {}

    public record RollbackInboundCommand(
            Long warehouseId, Long locationId, String locationCode,
            Long productId, String productCode, String productName,
            String unitType, String lotNumber, LocalDate expiryDate,
            int quantity, Long referenceId, Long userId, OffsetDateTime executedAt) {}

    /**
     * 同一ロケーションに異なる商品の在庫が存在するかチェックする。
     */
    public boolean existsDifferentProductAtLocation(Long locationId, Long productId) {
        return inventoryRepository.existsByLocationIdAndProductIdNot(locationId, productId);
    }

    /**
     * 入荷格納時に在庫をUPSERTし、INBOUND移動記録を作成する。
     */
    @Transactional
    public void storeInboundStock(StoreInboundCommand cmd) {
        Inventory inventory = inventoryRepository
                .findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        cmd.locationId(), cmd.productId(), cmd.unitType(), cmd.lotNumber(), cmd.expiryDate())
                .orElse(null);

        int newQty;
        try {
            if (inventory != null) {
                newQty = inventory.getQuantity() + cmd.quantity();
                inventory.setQuantity(newQty);
                inventoryRepository.save(inventory);
            } else {
                try {
                    newQty = cmd.quantity();
                    inventory = Inventory.builder()
                            .warehouseId(cmd.warehouseId())
                            .locationId(cmd.locationId())
                            .productId(cmd.productId())
                            .unitType(cmd.unitType())
                            .lotNumber(cmd.lotNumber())
                            .expiryDate(cmd.expiryDate())
                            .quantity(newQty)
                            .allocatedQty(0)
                            .build();
                    inventoryRepository.save(inventory);
                } catch (DataIntegrityViolationException e) {
                    // Concurrent INSERT won the race — retry as UPDATE
                    log.warn("Inventory INSERT collision, retrying as UPDATE: locationId={}, productId={}",
                            cmd.locationId(), cmd.productId());
                    inventory = inventoryRepository
                            .findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                                    cmd.locationId(), cmd.productId(), cmd.unitType(), cmd.lotNumber(), cmd.expiryDate())
                            .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                                    "在庫が見つかりません (locationId=" + cmd.locationId() + ", productId=" + cmd.productId() + ")"));
                    newQty = inventory.getQuantity() + cmd.quantity();
                    inventory.setQuantity(newQty);
                    inventoryRepository.save(inventory);
                }
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "在庫の並行更新が検出されました (locationId=" + cmd.locationId() + ", productId=" + cmd.productId() + ")");
        }

        InventoryMovement movement = InventoryMovement.builder()
                .warehouseId(cmd.warehouseId())
                .locationId(cmd.locationId())
                .locationCode(cmd.locationCode())
                .productId(cmd.productId())
                .productCode(cmd.productCode())
                .productName(cmd.productName())
                .unitType(cmd.unitType())
                .lotNumber(cmd.lotNumber())
                .expiryDate(cmd.expiryDate())
                .movementType("INBOUND")
                .quantity(cmd.quantity())
                .quantityAfter(newQty)
                .referenceId(cmd.referenceId())
                .referenceType("INBOUND_SLIP")
                .executedAt(cmd.executedAt())
                .executedBy(cmd.userId())
                .build();
        inventoryMovementRepository.save(movement);

        log.info("Inventory stored: locationId={}, productId={}, qty=+{}, after={}",
                cmd.locationId(), cmd.productId(), cmd.quantity(), newQty);
    }

    /**
     * 在庫をロールバックする（入荷キャンセル時）。
     * 在庫数量を減算し、INBOUND_CANCEL移動記録を作成する。
     */
    @Transactional
    public void rollbackInboundStock(RollbackInboundCommand cmd) {
        Inventory inventory = inventoryRepository
                .findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        cmd.locationId(), cmd.productId(), cmd.unitType(), cmd.lotNumber(), cmd.expiryDate())
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "在庫が見つかりません (locationId=" + cmd.locationId() + ", productId=" + cmd.productId() + ")"));

        int newQty = inventory.getQuantity() - cmd.quantity();
        if (newQty < 0) {
            throw new BusinessRuleViolationException("INVENTORY_INSUFFICIENT",
                    "在庫ロールバックで在庫数が負になります (inventoryId=" + inventory.getId()
                            + ", quantity=" + inventory.getQuantity()
                            + ", rollback=" + cmd.quantity() + ")");
        }
        if (newQty < inventory.getAllocatedQty()) {
            throw new BusinessRuleViolationException("INVENTORY_ALLOCATED",
                    "引当済み数量が在庫ロールバック後の数量を超えます (inventoryId=" + inventory.getId()
                            + ", allocatedQty=" + inventory.getAllocatedQty()
                            + ", newQuantity=" + newQty + ")");
        }
        inventory.setQuantity(newQty);
        try {
            inventoryRepository.save(inventory);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "在庫の並行更新が検出されました (locationId=" + cmd.locationId() + ", productId=" + cmd.productId() + ")");
        }

        InventoryMovement movement = InventoryMovement.builder()
                .warehouseId(cmd.warehouseId())
                .locationId(cmd.locationId())
                .locationCode(cmd.locationCode())
                .productId(cmd.productId())
                .productCode(cmd.productCode())
                .productName(cmd.productName())
                .unitType(cmd.unitType())
                .lotNumber(cmd.lotNumber())
                .expiryDate(cmd.expiryDate())
                .movementType("INBOUND_CANCEL")
                .quantity(-cmd.quantity())
                .quantityAfter(newQty)
                .referenceId(cmd.referenceId())
                .referenceType("INBOUND_SLIP")
                .executedAt(cmd.executedAt())
                .executedBy(cmd.userId())
                .build();
        inventoryMovementRepository.save(movement);

        log.info("Inventory rollback: locationId={}, productId={}, qty=-{}, after={}",
                cmd.locationId(), cmd.productId(), cmd.quantity(), newQty);
    }
}
