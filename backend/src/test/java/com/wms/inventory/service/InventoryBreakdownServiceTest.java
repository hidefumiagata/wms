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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryBreakdownService")
class InventoryBreakdownServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;
    @Mock private LocationService locationService;
    @Mock private ProductService productService;
    @InjectMocks private InventoryBreakdownService service;

    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); }

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
                var field = clazz.getDeclaredField(fieldName);
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
        loc.setWarehouseId(1L);
        loc.setIsStocktakingLocked(locked);
        return loc;
    }

    private Product createProduct(Long id, int caseQty, int ballQty) {
        Product p = new Product();
        setField(p, "id", id);
        p.setProductCode("P-001");
        p.setProductName("テスト商品");
        p.setCaseQuantity(caseQty);
        p.setBallQuantity(ballQty);
        return p;
    }

    private Inventory createInventory(Long id, Long locationId, Long productId, String unitType, int qty, int allocated) {
        Inventory inv = Inventory.builder()
                .warehouseId(1L).locationId(locationId).productId(productId)
                .unitType(unitType).quantity(qty).allocatedQty(allocated).build();
        setField(inv, "id", id);
        return inv;
    }

    @Nested
    @DisplayName("breakdown")
    class BreakdownTests {

        @Test
        @DisplayName("正常系: CASE→BALL ばらし成功")
        void breakdown_caseToBall_success() {
            setUpSecurityContext();
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, 12, 6));

            Inventory fromInv = createInventory(10L, 1L, 100L, "CASE", 5, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "BALL", null, null)).thenReturn(Optional.empty());
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> {
                Inventory inv = i.getArgument(0);
                if (inv.getId() == null) setField(inv, "id", 20L);
                return inv;
            });
            when(inventoryMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.breakdown(1L, 100L, "CASE", 2, "BALL", 1L);

            assertThat(result.breakdownQty()).isEqualTo(2);
            assertThat(result.convertedQty()).isEqualTo(24); // 2 * 12
            assertThat(result.fromQuantityAfter()).isEqualTo(3);
            verify(inventoryMovementRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("正常系: CASE→PIECE ばらし成功")
        void breakdown_caseToPiece_success() {
            setUpSecurityContext();
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, 12, 6));

            Inventory fromInv = createInventory(10L, 1L, 100L, "CASE", 1, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "PIECE", null, null)).thenReturn(Optional.empty());
            when(inventoryRepository.save(any())).thenAnswer(i -> {
                Inventory inv = i.getArgument(0);
                if (inv.getId() == null) setField(inv, "id", 20L);
                return inv;
            });
            when(inventoryMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.breakdown(1L, 100L, "CASE", 1, "PIECE", 1L);

            assertThat(result.convertedQty()).isEqualTo(72); // 1 * 12 * 6
        }

        @Test
        @DisplayName("正常系: BALL→PIECE ばらし成功")
        void breakdown_ballToPiece_success() {
            setUpSecurityContext();
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, 12, 6));

            Inventory fromInv = createInventory(10L, 1L, 100L, "BALL", 3, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "BALL", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "PIECE", null, null)).thenReturn(Optional.empty());
            when(inventoryRepository.save(any())).thenAnswer(i -> {
                Inventory inv = i.getArgument(0);
                if (inv.getId() == null) setField(inv, "id", 20L);
                return inv;
            });
            when(inventoryMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.breakdown(1L, 100L, "BALL", 2, "PIECE", 1L);
            assertThat(result.convertedQty()).isEqualTo(12); // 2 * 6
        }

        @Test
        @DisplayName("正常系: 異なるロケーションへのばらし")
        void breakdown_differentLocation_success() {
            setUpSecurityContext();
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, 12, 6));

            Inventory fromInv = createInventory(10L, 1L, 100L, "CASE", 5, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    2L, 100L, "BALL", null, null)).thenReturn(Optional.empty());
            when(inventoryRepository.save(any())).thenAnswer(i -> {
                Inventory inv = i.getArgument(0);
                if (inv.getId() == null) setField(inv, "id", 20L);
                return inv;
            });
            when(inventoryMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.breakdown(1L, 100L, "CASE", 1, "BALL", 2L);
            assertThat(result.fromQuantityAfter()).isEqualTo(4);
        }

        @Test
        @DisplayName("正常系: 既存のばらし先在庫に加算")
        void breakdown_existingTarget_success() {
            setUpSecurityContext();
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, 12, 6));

            Inventory fromInv = createInventory(10L, 1L, 100L, "CASE", 5, 0);
            Inventory toInv = createInventory(20L, 1L, 100L, "BALL", 10, 0);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "BALL", null, null)).thenReturn(Optional.of(toInv));
            when(inventoryRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(toInv));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(inventoryMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.breakdown(1L, 100L, "CASE", 1, "BALL", 1L);
            assertThat(result.toQuantityAfter()).isEqualTo(22); // 10 + 12
        }

        @Test
        @DisplayName("PIECEからのばらしはエラー")
        void breakdown_fromPiece_throws() {
            assertThatThrownBy(() -> service.breakdown(1L, 100L, "PIECE", 1, "BALL", 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("荷姿順序不正（BALL→CASE）はエラー")
        void breakdown_wrongOrder_throws() {
            assertThatThrownBy(() -> service.breakdown(1L, 100L, "BALL", 1, "CASE", 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("breakdownQty < 1 はエラー")
        void breakdown_zeroQty_throws() {
            assertThatThrownBy(() -> service.breakdown(1L, 100L, "CASE", 0, "BALL", 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("在庫不足の場合エラー")
        void breakdown_insufficient_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(productService.findById(100L)).thenReturn(createProduct(100L, 12, 6));

            Inventory fromInv = createInventory(10L, 1L, 100L, "CASE", 2, 1); // available=1
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    1L, 100L, "CASE", null, null)).thenReturn(Optional.of(fromInv));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fromInv));

            assertThatThrownBy(() -> service.breakdown(1L, 100L, "CASE", 2, "BALL", 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_INSUFFICIENT");
        }

        @Test
        @DisplayName("棚卸ロック中（元）はエラー")
        void breakdown_fromLocked_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", true));

            assertThatThrownBy(() -> service.breakdown(1L, 100L, "CASE", 1, "BALL", 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }

        @Test
        @DisplayName("棚卸ロック中（先）はエラー")
        void breakdown_toLocked_throws() {
            when(locationService.findById(1L)).thenReturn(createLocation(1L, "A-01", false));
            when(locationService.findById(2L)).thenReturn(createLocation(2L, "B-01", true));

            assertThatThrownBy(() -> service.breakdown(1L, 100L, "CASE", 1, "BALL", 2L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }
    }

    @Nested
    @DisplayName("getConversionRate")
    class ConversionRateTests {
        @Test void caseToBall() {
            assertThat(service.getConversionRate("CASE", "BALL", createProduct(1L, 12, 6))).isEqualTo(12);
        }
        @Test void caseToPiece() {
            assertThat(service.getConversionRate("CASE", "PIECE", createProduct(1L, 12, 6))).isEqualTo(72);
        }
        @Test void ballToPiece() {
            assertThat(service.getConversionRate("BALL", "PIECE", createProduct(1L, 12, 6))).isEqualTo(6);
        }
        @Test void invalidConversion() {
            assertThat(service.getConversionRate("PIECE", "CASE", createProduct(1L, 12, 6))).isEqualTo(0);
        }
    }
}
