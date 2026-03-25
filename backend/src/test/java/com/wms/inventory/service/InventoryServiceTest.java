package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @InjectMocks
    private InventoryService inventoryService;

    static void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }

    @Nested
    @DisplayName("rollbackInboundStock")
    class RollbackInboundStockTests {

        private static final Long WAREHOUSE_ID = 1L;
        private static final Long LOCATION_ID = 200L;
        private static final String LOCATION_CODE = "LOC-A01";
        private static final Long PRODUCT_ID = 100L;
        private static final String PRODUCT_CODE = "PRD-0001";
        private static final String PRODUCT_NAME = "商品A";
        private static final String UNIT_TYPE = "CASE";
        private static final String LOT_NUMBER = "LOT-001";
        private static final LocalDate EXPIRY_DATE = LocalDate.of(2027, 3, 22);
        private static final int ROLLBACK_QTY = 48;
        private static final Long REFERENCE_ID = 1L;
        private static final Long USER_ID = 10L;
        private static final OffsetDateTime EXECUTED_AT = OffsetDateTime.now();

        private InventoryService.RollbackInboundCommand defaultCmd() {
            return new InventoryService.RollbackInboundCommand(
                    WAREHOUSE_ID, LOCATION_ID, LOCATION_CODE,
                    PRODUCT_ID, PRODUCT_CODE, PRODUCT_NAME,
                    UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE,
                    ROLLBACK_QTY, REFERENCE_ID, USER_ID, EXECUTED_AT);
        }

        @Test
        @DisplayName("正常系: 在庫数量が減算され、INBOUND_CANCEL移動記録が作成される")
        void rollbackInboundStock_success() {
            Inventory inventory = Inventory.builder()
                    .warehouseId(WAREHOUSE_ID)
                    .locationId(LOCATION_ID)
                    .productId(PRODUCT_ID)
                    .unitType(UNIT_TYPE)
                    .lotNumber(LOT_NUMBER)
                    .expiryDate(EXPIRY_DATE)
                    .quantity(148)
                    .allocatedQty(0)
                    .build();
            setField(inventory, "id", 500L);

            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            inventoryService.rollbackInboundStock(defaultCmd());

            assertThat(inventory.getQuantity()).isEqualTo(100); // 148 - 48

            ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
            verify(inventoryMovementRepository).save(movementCaptor.capture());
            InventoryMovement savedMovement = movementCaptor.getValue();
            assertThat(savedMovement.getMovementType()).isEqualTo("INBOUND_CANCEL");
            assertThat(savedMovement.getQuantity()).isEqualTo(-48);
            assertThat(savedMovement.getQuantityAfter()).isEqualTo(100);
            assertThat(savedMovement.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(savedMovement.getLocationId()).isEqualTo(LOCATION_ID);
            assertThat(savedMovement.getLocationCode()).isEqualTo(LOCATION_CODE);
            assertThat(savedMovement.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(savedMovement.getProductCode()).isEqualTo(PRODUCT_CODE);
            assertThat(savedMovement.getProductName()).isEqualTo(PRODUCT_NAME);
            assertThat(savedMovement.getReferenceId()).isEqualTo(REFERENCE_ID);
            assertThat(savedMovement.getReferenceType()).isEqualTo("INBOUND_SLIP");
            assertThat(savedMovement.getExecutedBy()).isEqualTo(USER_ID);
            assertThat(savedMovement.getExecutedAt()).isEqualTo(EXECUTED_AT);
        }

        @Test
        @DisplayName("在庫が見つからない場合ResourceNotFoundExceptionをスローする")
        void rollbackInboundStock_inventoryNotFound_throws() {
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.rollbackInboundStock(defaultCmd()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_NOT_FOUND");
        }

        @Test
        @DisplayName("在庫不足の場合BusinessRuleViolationExceptionをスローする")
        void rollbackInboundStock_insufficientInventory_throws() {
            Inventory inventory = Inventory.builder()
                    .warehouseId(WAREHOUSE_ID)
                    .locationId(LOCATION_ID)
                    .productId(PRODUCT_ID)
                    .unitType(UNIT_TYPE)
                    .lotNumber(LOT_NUMBER)
                    .expiryDate(EXPIRY_DATE)
                    .quantity(10) // quantity < rollbackQty (48)
                    .allocatedQty(0)
                    .build();
            setField(inventory, "id", 500L);

            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.of(inventory));

            assertThatThrownBy(() -> inventoryService.rollbackInboundStock(defaultCmd()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_INSUFFICIENT");
        }

        @Test
        @DisplayName("引当済み数量超過の場合BusinessRuleViolationExceptionをスローする")
        void rollbackInboundStock_allocatedExceeds_throws() {
            Inventory inventory = Inventory.builder()
                    .warehouseId(WAREHOUSE_ID)
                    .locationId(LOCATION_ID)
                    .productId(PRODUCT_ID)
                    .unitType(UNIT_TYPE)
                    .lotNumber(LOT_NUMBER)
                    .expiryDate(EXPIRY_DATE)
                    .quantity(50) // newQty=50-30=20, allocatedQty=25 -> newQty < allocatedQty
                    .allocatedQty(25)
                    .build();
            setField(inventory, "id", 500L);

            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.of(inventory));

            InventoryService.RollbackInboundCommand cmd = new InventoryService.RollbackInboundCommand(
                    WAREHOUSE_ID, LOCATION_ID, LOCATION_CODE,
                    PRODUCT_ID, PRODUCT_CODE, PRODUCT_NAME,
                    UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE,
                    30, REFERENCE_ID, USER_ID, EXECUTED_AT); // rollback 30: 50-30=20 < 25
            assertThatThrownBy(() -> inventoryService.rollbackInboundStock(cmd))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_ALLOCATED");
        }

        @Test
        @DisplayName("楽観的ロック衝突時にOptimisticLockConflictExceptionをスローする")
        void rollbackInboundStock_optimisticLock_throws() {
            Inventory existing = Inventory.builder()
                    .warehouseId(WAREHOUSE_ID).locationId(LOCATION_ID).productId(PRODUCT_ID)
                    .unitType(UNIT_TYPE).quantity(148).allocatedQty(0).build();
            setField(existing, "id", 1L);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.of(existing));
            when(inventoryRepository.save(any(Inventory.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class.getName(), 1L));

            assertThatThrownBy(() -> inventoryService.rollbackInboundStock(defaultCmd()))
                    .isInstanceOf(OptimisticLockConflictException.class)
                    .extracting("errorCode").isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }
    }

    @Nested
    @DisplayName("storeInboundStock")
    class StoreInboundStockTests {

        private static final Long WAREHOUSE_ID = 1L;
        private static final Long LOCATION_ID = 200L;
        private static final String LOCATION_CODE = "LOC-A01";
        private static final Long PRODUCT_ID = 100L;
        private static final String PRODUCT_CODE = "PRD-0001";
        private static final String PRODUCT_NAME = "商品A";
        private static final String UNIT_TYPE = "CASE";
        private static final String LOT_NUMBER = "LOT-001";
        private static final LocalDate EXPIRY_DATE = LocalDate.of(2027, 3, 22);
        private static final int STORE_QTY = 48;
        private static final Long REFERENCE_ID = 1L;
        private static final Long USER_ID = 10L;
        private static final OffsetDateTime EXECUTED_AT = OffsetDateTime.now();

        private InventoryService.StoreInboundCommand defaultCmd() {
            return new InventoryService.StoreInboundCommand(
                    WAREHOUSE_ID, LOCATION_ID, LOCATION_CODE,
                    PRODUCT_ID, PRODUCT_CODE, PRODUCT_NAME,
                    UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE,
                    STORE_QTY, REFERENCE_ID, USER_ID, EXECUTED_AT);
        }

        @Test
        @DisplayName("正常系: 既存在庫に加算され、INBOUND移動記録が作成される")
        void storeInboundStock_existingInventory_success() {
            Inventory inventory = Inventory.builder()
                    .warehouseId(WAREHOUSE_ID)
                    .locationId(LOCATION_ID)
                    .productId(PRODUCT_ID)
                    .unitType(UNIT_TYPE)
                    .lotNumber(LOT_NUMBER)
                    .expiryDate(EXPIRY_DATE)
                    .quantity(100)
                    .allocatedQty(0)
                    .build();
            setField(inventory, "id", 500L);

            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            inventoryService.storeInboundStock(defaultCmd());

            assertThat(inventory.getQuantity()).isEqualTo(148); // 100 + 48

            ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
            verify(inventoryMovementRepository).save(movementCaptor.capture());
            InventoryMovement savedMovement = movementCaptor.getValue();
            assertThat(savedMovement.getMovementType()).isEqualTo("INBOUND");
            assertThat(savedMovement.getQuantity()).isEqualTo(48);
            assertThat(savedMovement.getQuantityAfter()).isEqualTo(148);
            assertThat(savedMovement.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(savedMovement.getLocationId()).isEqualTo(LOCATION_ID);
            assertThat(savedMovement.getLocationCode()).isEqualTo(LOCATION_CODE);
            assertThat(savedMovement.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(savedMovement.getProductCode()).isEqualTo(PRODUCT_CODE);
            assertThat(savedMovement.getProductName()).isEqualTo(PRODUCT_NAME);
            assertThat(savedMovement.getReferenceId()).isEqualTo(REFERENCE_ID);
            assertThat(savedMovement.getReferenceType()).isEqualTo("INBOUND_SLIP");
            assertThat(savedMovement.getExecutedBy()).isEqualTo(USER_ID);
            assertThat(savedMovement.getExecutedAt()).isEqualTo(EXECUTED_AT);
        }

        @Test
        @DisplayName("正常系: 在庫が存在しない場合、新規作成される")
        void storeInboundStock_newInventory_success() {
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.empty());
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            inventoryService.storeInboundStock(defaultCmd());

            ArgumentCaptor<Inventory> inventoryCaptor = ArgumentCaptor.forClass(Inventory.class);
            verify(inventoryRepository).save(inventoryCaptor.capture());
            Inventory saved = inventoryCaptor.getValue();
            assertThat(saved.getQuantity()).isEqualTo(48);
            assertThat(saved.getAllocatedQty()).isEqualTo(0);
            assertThat(saved.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(saved.getLocationId()).isEqualTo(LOCATION_ID);
            assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(saved.getUnitType()).isEqualTo(UNIT_TYPE);
            assertThat(saved.getLotNumber()).isEqualTo(LOT_NUMBER);
            assertThat(saved.getExpiryDate()).isEqualTo(EXPIRY_DATE);

            ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
            verify(inventoryMovementRepository).save(movementCaptor.capture());
            InventoryMovement savedMovement = movementCaptor.getValue();
            assertThat(savedMovement.getMovementType()).isEqualTo("INBOUND");
            assertThat(savedMovement.getQuantity()).isEqualTo(48);
            assertThat(savedMovement.getQuantityAfter()).isEqualTo(48);
        }

        @Test
        @DisplayName("INSERT競合時にリトライしてUPDATEで成功する")
        void storeInboundStock_insertCollision_retryAsUpdate() {
            // First call: no existing inventory
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(Inventory.builder()
                            .warehouseId(WAREHOUSE_ID).locationId(LOCATION_ID).productId(PRODUCT_ID)
                            .unitType(UNIT_TYPE).lotNumber(LOT_NUMBER).expiryDate(EXPIRY_DATE)
                            .quantity(48).allocatedQty(0).build()));

            // First save throws DataIntegrityViolationException, second save succeeds
            when(inventoryRepository.save(any(Inventory.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            inventoryService.storeInboundStock(defaultCmd());

            // Verify save was called twice (first INSERT failed, then UPDATE succeeded)
            verify(inventoryRepository, times(2)).save(any(Inventory.class));
            // Verify findBy was called twice (initial lookup + retry lookup)
            verify(inventoryRepository, times(2)).findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE);

            ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
            verify(inventoryMovementRepository).save(movementCaptor.capture());
            InventoryMovement savedMovement = movementCaptor.getValue();
            assertThat(savedMovement.getMovementType()).isEqualTo("INBOUND");
            assertThat(savedMovement.getQuantity()).isEqualTo(48);
            assertThat(savedMovement.getQuantityAfter()).isEqualTo(96); // 48 (existing) + 48 (store)
        }

        @Test
        @DisplayName("INSERT競合リトライ時に在庫が見つからない場合ResourceNotFoundExceptionをスローする")
        void storeInboundStock_insertCollision_retryNotFound_throws() {
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    LOCATION_ID, PRODUCT_ID, UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty());

            when(inventoryRepository.save(any(Inventory.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            assertThatThrownBy(() -> inventoryService.storeInboundStock(defaultCmd()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_NOT_FOUND");
        }

        @Test
        @DisplayName("楽観的ロック衝突時にOptimisticLockConflictExceptionをスローする")
        void storeInboundStock_optimisticLock_throws() {
            Inventory existing = Inventory.builder()
                    .warehouseId(1L).locationId(200L).productId(100L).unitType("CASE")
                    .quantity(100).allocatedQty(0).build();
            setField(existing, "id", 1L);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    200L, 100L, "CASE", null, null)).thenReturn(Optional.of(existing));
            when(inventoryRepository.save(any(Inventory.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class.getName(), 1L));

            InventoryService.StoreInboundCommand cmd = new InventoryService.StoreInboundCommand(
                    1L, 200L, "LOC-A01", 100L, "PRD-0001", "商品A",
                    "CASE", null, null, 48, 1L, 10L, OffsetDateTime.now());
            assertThatThrownBy(() -> inventoryService.storeInboundStock(cmd))
                    .isInstanceOf(OptimisticLockConflictException.class)
                    .extracting("errorCode").isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        }
    }

    @Nested
    @DisplayName("existsDifferentProductAtLocation")
    class ExistsDifferentProductAtLocationTests {

        @Test
        @DisplayName("異なる商品が存在する場合trueを返す")
        void existsDifferentProduct_true() {
            when(inventoryRepository.existsByLocationIdAndProductIdNot(200L, 100L)).thenReturn(true);

            assertThat(inventoryService.existsDifferentProductAtLocation(200L, 100L)).isTrue();
        }

        @Test
        @DisplayName("異なる商品が存在しない場合falseを返す")
        void existsDifferentProduct_false() {
            when(inventoryRepository.existsByLocationIdAndProductIdNot(200L, 100L)).thenReturn(false);

            assertThat(inventoryService.existsDifferentProductAtLocation(200L, 100L)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasInventoryByProductId")
    class HasInventoryByProductIdTests {

        @Test
        @DisplayName("在庫ありでtrueを返す")
        void hasInventory_exists_returnsTrue() {
            when(inventoryRepository.existsByProductIdWithPositiveQty(1L)).thenReturn(true);

            assertThat(inventoryService.hasInventoryByProductId(1L)).isTrue();
        }

        @Test
        @DisplayName("在庫なしでfalseを返す")
        void hasInventory_notExists_returnsFalse() {
            when(inventoryRepository.existsByProductIdWithPositiveQty(1L)).thenReturn(false);

            assertThat(inventoryService.hasInventoryByProductId(1L)).isFalse();
        }
    }
}
