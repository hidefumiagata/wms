package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.ResourceNotFoundException;
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

            inventoryService.rollbackInboundStock(
                    WAREHOUSE_ID, LOCATION_ID, LOCATION_CODE,
                    PRODUCT_ID, PRODUCT_CODE, PRODUCT_NAME,
                    UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE,
                    ROLLBACK_QTY, REFERENCE_ID, USER_ID, EXECUTED_AT);

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

            assertThatThrownBy(() -> inventoryService.rollbackInboundStock(
                    WAREHOUSE_ID, LOCATION_ID, LOCATION_CODE,
                    PRODUCT_ID, PRODUCT_CODE, PRODUCT_NAME,
                    UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE,
                    ROLLBACK_QTY, REFERENCE_ID, USER_ID, EXECUTED_AT))
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

            assertThatThrownBy(() -> inventoryService.rollbackInboundStock(
                    WAREHOUSE_ID, LOCATION_ID, LOCATION_CODE,
                    PRODUCT_ID, PRODUCT_CODE, PRODUCT_NAME,
                    UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE,
                    ROLLBACK_QTY, REFERENCE_ID, USER_ID, EXECUTED_AT))
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

            assertThatThrownBy(() -> inventoryService.rollbackInboundStock(
                    WAREHOUSE_ID, LOCATION_ID, LOCATION_CODE,
                    PRODUCT_ID, PRODUCT_CODE, PRODUCT_NAME,
                    UNIT_TYPE, LOT_NUMBER, EXPIRY_DATE,
                    30, REFERENCE_ID, USER_ID, EXECUTED_AT)) // rollback 30: 50-30=20 < 25
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_ALLOCATED");
        }

        private static void setField(Object obj, String fieldName, Object value) {
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
