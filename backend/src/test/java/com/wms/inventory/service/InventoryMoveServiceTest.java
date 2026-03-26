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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryMoveService")
class InventoryMoveServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;
    @Mock private LocationService locationService;
    @Mock private ProductService productService;

    @InjectMocks private InventoryMoveService inventoryMoveService;

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    void setUpSecurityContext() {
        WmsUserDetails ud = new WmsUserDetails(10L, "user", "pw", "WH-001",
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }

    static void setField(Object obj, String fieldName, Object value) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }

    private Location createLocation(Long id, String code, boolean locked) {
        Location loc = new Location();
        setField(loc, "id", id);
        loc.setLocationCode(code);
        loc.setIsStocktakingLocked(locked);
        return loc;
    }

    private Product createProduct(Long id, String code) {
        Product p = new Product();
        setField(p, "id", id);
        p.setProductCode(code);
        p.setProductName("商品" + code);
        return p;
    }

    private Inventory createInventory(Long id, Long locationId, Long productId, int qty, int allocated) {
        Inventory inv = Inventory.builder()
                .warehouseId(1L).locationId(locationId).productId(productId)
                .unitType("CASE").quantity(qty).allocatedQty(allocated).build();
        setField(inv, "id", id);
        return inv;
    }

    @Nested
    @DisplayName("moveInventory")
    class MoveTests {

        @Test
        @DisplayName("正常系: 在庫移動成功（移動先に既存在庫あり）")
        void move_success_existingTarget() {
            setUpSecurityContext();
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, "P-001"));

            Inventory fromInv = createInventory(10L, 1L, 100L, 20, 5);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.existsByLocationIdAndProductIdNot(2L, 100L)).thenReturn(false);

            Inventory toInv = createInventory(20L, 2L, 100L, 3, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    2L, 100L, "CASE", null, null)).thenReturn(Optional.of(toInv));
            when(inventoryRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(toInv));

            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));
            when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(i -> i.getArgument(0));

            InventoryMoveService.MoveResult result = inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 5);

            assertThat(result.movedQty()).isEqualTo(5);
            assertThat(result.fromQuantityAfter()).isEqualTo(15);
            assertThat(result.toQuantityAfter()).isEqualTo(8);
            verify(inventoryMovementRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("正常系: 在庫移動成功（移動先に在庫なし→新規作成）")
        void move_success_newTarget() {
            setUpSecurityContext();
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, "P-001"));

            Inventory fromInv = createInventory(10L, 1L, 100L, 10, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.existsByLocationIdAndProductIdNot(2L, 100L)).thenReturn(false);

            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    2L, 100L, "CASE", null, null)).thenReturn(Optional.empty());
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> {
                Inventory inv = i.getArgument(0);
                if (inv.getId() == null) setField(inv, "id", 30L);
                return inv;
            });
            when(inventoryMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InventoryMoveService.MoveResult result = inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 3);

            assertThat(result.toQuantityAfter()).isEqualTo(3);
            assertThat(result.toInventoryId()).isEqualTo(30L);
        }

        @Test
        @DisplayName("moveQtyが0以下の場合エラー")
        void move_zeroQty_throws() {
            assertThatThrownBy(() -> inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 0))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("正常系: 在庫移動成功（移動先に既存在庫あり、fromId > toId のロック順序）")
        void move_success_existingTarget_reverseLockOrder() {
            setUpSecurityContext();
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, "P-001"));

            // fromInv.id=30 > toInv.id=5 → reverse lock order
            Inventory fromInv = createInventory(30L, 1L, 100L, 20, 5);
            Inventory toInv = createInventory(5L, 2L, 100L, 3, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    2L, 100L, "CASE", null, null)).thenReturn(Optional.of(toInv));
            when(inventoryRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(toInv));
            when(inventoryRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.existsByLocationIdAndProductIdNot(2L, 100L)).thenReturn(false);

            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));
            when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(i -> i.getArgument(0));

            InventoryMoveService.MoveResult result = inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 5);

            assertThat(result.movedQty()).isEqualTo(5);
            assertThat(result.fromQuantityAfter()).isEqualTo(15);
            assertThat(result.toQuantityAfter()).isEqualTo(8);
        }

        @Test
        @DisplayName("移動元と移動先が同一の場合エラー")
        void move_sameLocation_throws() {
            assertThatThrownBy(() -> inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 1L, 5))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("移動元ロケーションが棚卸ロック中の場合エラー")
        void move_fromLocked_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", true));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));

            assertThatThrownBy(() -> inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 5))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }

        @Test
        @DisplayName("移動先ロケーションが棚卸ロック中の場合エラー")
        void move_toLocked_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", true));

            assertThatThrownBy(() -> inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 5))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }

        @Test
        @DisplayName("移動元在庫が存在しない場合エラー")
        void move_inventoryNotFound_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, "P-001"));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 5))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_NOT_FOUND");
        }

        @Test
        @DisplayName("在庫不足の場合エラー")
        void move_insufficient_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, "P-001"));

            Inventory fromInv = createInventory(10L, 1L, 100L, 5, 3); // available = 2
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));

            assertThatThrownBy(() -> inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 5))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_INSUFFICIENT");
        }

        @Test
        @DisplayName("lockInventoryでfindByIdForUpdateが空を返す場合ResourceNotFoundException")
        void move_lockFailed_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, "P-001"));

            Inventory fromInv = createInventory(10L, 1L, 100L, 10, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    2L, 100L, "CASE", null, null)).thenReturn(Optional.empty());
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 5))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_NOT_FOUND");
        }

        @Test
        @DisplayName("移動先に別商品がある場合エラー")
        void move_productMismatch_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, "P-001"));

            Inventory fromInv = createInventory(10L, 1L, 100L, 10, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.existsByLocationIdAndProductIdNot(2L, 100L)).thenReturn(true);

            assertThatThrownBy(() -> inventoryMoveService.moveInventory(
                    1L, 100L, "CASE", null, null, 2L, 5))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("LOCATION_PRODUCT_MISMATCH");
        }
    }
}
