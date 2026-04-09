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
import java.util.List;

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
     * 指定した商品の在庫（quantity > 0）が存在するかチェックする。
     */
    public boolean hasInventoryByProductId(Long productId) {
        return inventoryRepository.existsByProductIdWithPositiveQty(productId);
    }

    /**
     * 指定した倉庫に在庫（quantity > 0）が存在するかチェックする。
     * 倉庫無効化時の業務ルール検証に使用する。
     */
    public boolean hasInventoryByWarehouseId(Long warehouseId) {
        return inventoryRepository.existsByWarehouseIdWithPositiveQty(warehouseId);
    }

    /**
     * 指定したロケーションに在庫（quantity > 0）が存在するかチェックする。
     * ロケーション無効化時の業務ルール検証に使用する。
     */
    public boolean hasInventoryByLocationId(Long locationId) {
        return inventoryRepository.existsByLocationIdWithPositiveQty(locationId);
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
                            .orElseThrow(() -> {
                                log.info("Inventory not found after collision retry: locationId={}, productId={}",
                                        cmd.locationId(), cmd.productId());
                                return new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                                        "在庫が見つかりません");
                            });
                    newQty = inventory.getQuantity() + cmd.quantity();
                    inventory.setQuantity(newQty);
                    inventoryRepository.save(inventory);
                }
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Inventory optimistic lock conflict on storeInbound: locationId={}, productId={}",
                    cmd.locationId(), cmd.productId());
            throw OptimisticLockConflictException.standard();
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
                .orElseThrow(() -> {
                    log.info("Inventory not found on rollback: locationId={}, productId={}",
                            cmd.locationId(), cmd.productId());
                    return new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                            "在庫が見つかりません");
                });

        int newQty = inventory.getQuantity() - cmd.quantity();
        if (newQty < 0) {
            log.warn("Inventory rollback would make quantity negative: inventoryId={}, quantity={}, rollback={}",
                    inventory.getId(), inventory.getQuantity(), cmd.quantity());
            throw new BusinessRuleViolationException("INVENTORY_INSUFFICIENT",
                    "在庫ロールバックで在庫数が負になります");
        }
        if (newQty < inventory.getAllocatedQty()) {
            log.warn("Inventory rollback would exceed allocated: inventoryId={}, allocatedQty={}, newQuantity={}",
                    inventory.getId(), inventory.getAllocatedQty(), newQty);
            throw new BusinessRuleViolationException("INVENTORY_ALLOCATED",
                    "引当済み数量が在庫ロールバック後の数量を超えます");
        }
        inventory.setQuantity(newQty);
        try {
            inventoryRepository.save(inventory);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Inventory optimistic lock conflict on rollback: locationId={}, productId={}",
                    cmd.locationId(), cmd.productId());
            throw OptimisticLockConflictException.standard();
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

    public record DeductReturnCommand(
            Long warehouseId, Long locationId, String locationCode,
            Long productId, String productCode, String productName,
            String unitType, int quantity, Long referenceId,
            Long userId, OffsetDateTime executedAt) {}

    /**
     * 在庫返品時に在庫を減算し、RETURN_OUT移動記録を作成する。
     * 在庫チェックは location_id + product_id + unit_type の3フィールドで検索する。
     */
    @Transactional
    public void deductReturnStock(DeductReturnCommand cmd) {
        List<Inventory> inventories = inventoryRepository
                .findByLocationIdAndProductIdAndUnitType(
                        cmd.locationId(), cmd.productId(), cmd.unitType());

        if (inventories.isEmpty()) {
            log.info("Inventory not found on return deduction: locationId={}, productId={}",
                    cmd.locationId(), cmd.productId());
            throw new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                    "在庫が見つかりません");
        }

        boolean hasAllocated = inventories.stream().anyMatch(i -> i.getAllocatedQty() > 0);
        if (hasAllocated) {
            throw new BusinessRuleViolationException("RETURN_ALLOCATED_INVENTORY",
                    "引当済み在庫は返品できません");
        }

        int totalAvailable = inventories.stream()
                .mapToInt(i -> i.getQuantity() - i.getAllocatedQty()).sum();
        if (cmd.quantity() > totalAvailable) {
            log.warn("Return quantity exceeds available stock: locationId={}, productId={}, available={}, requested={}",
                    cmd.locationId(), cmd.productId(), totalAvailable, cmd.quantity());
            throw new BusinessRuleViolationException("RETURN_INSUFFICIENT_QUANTITY",
                    "返品数量が在庫数を超えています");
        }

        // 複数在庫レコード（ロット違い等）に対してID順に分散減算する
        int remaining = cmd.quantity();
        for (Inventory inventory : inventories) {
            if (remaining <= 0) {
                break;
            }
            int deduct = Math.min(remaining, inventory.getQuantity());
            if (deduct <= 0) {
                continue;
            }
            int newQty = inventory.getQuantity() - deduct;
            inventory.setQuantity(newQty);
            try {
                inventoryRepository.save(inventory);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Inventory optimistic lock conflict on return deduction: locationId={}, productId={}, inventoryId={}",
                        cmd.locationId(), cmd.productId(), inventory.getId());
                throw OptimisticLockConflictException.standard();
            }

            InventoryMovement movement = InventoryMovement.builder()
                    .warehouseId(cmd.warehouseId())
                    .locationId(cmd.locationId())
                    .locationCode(cmd.locationCode())
                    .productId(cmd.productId())
                    .productCode(cmd.productCode())
                    .productName(cmd.productName())
                    .unitType(cmd.unitType())
                    .movementType("RETURN_OUT")
                    .quantity(-deduct)
                    .quantityAfter(newQty)
                    .referenceId(cmd.referenceId())
                    .referenceType("RETURN_SLIP")
                    .executedAt(cmd.executedAt())
                    .executedBy(cmd.userId())
                    .build();
            inventoryMovementRepository.save(movement);

            log.info("Inventory return deducted: locationId={}, productId={}, inventoryId={}, qty=-{}, after={}",
                    cmd.locationId(), cmd.productId(), inventory.getId(), deduct, newQty);
            remaining -= deduct;
        }
    }
}
