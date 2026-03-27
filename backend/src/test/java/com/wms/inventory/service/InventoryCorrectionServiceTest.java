package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryCorrectionService")
class InventoryCorrectionServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;
    @Mock private LocationService locationService;
    @Mock private ProductService productService;
    @InjectMocks private InventoryCorrectionService service;

    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); }

    void setUpSecurityContext() {
        WmsUserDetails ud = new WmsUserDetails(10L, "user", "pw", "WH-001",
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }

    static void setField(Object obj, String fieldName, Object value) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try { var f = clazz.getDeclaredField(fieldName); f.setAccessible(true); f.set(obj, value); return; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }

    private Location loc(Long id, boolean locked) {
        Location l = new Location(); setField(l, "id", id);
        l.setLocationCode("A-01"); l.setWarehouseId(1L); l.setIsStocktakingLocked(locked); return l;
    }

    private Product prod(Long id) {
        Product p = new Product(); setField(p, "id", id);
        p.setProductCode("P-001"); p.setProductName("テスト商品"); return p;
    }

    private Inventory inv(Long id, int qty, int allocated) {
        Inventory i = Inventory.builder().warehouseId(1L).locationId(1L).productId(100L)
                .unitType("CASE").quantity(qty).allocatedQty(allocated).build();
        setField(i, "id", id); return i;
    }

    @Test @DisplayName("正常系: 在庫訂正成功")
    void correct_success() {
        setUpSecurityContext();
        when(locationService.findById(1L)).thenReturn(loc(1L, false));
        when(productService.findById(100L)).thenReturn(prod(100L));
        Inventory i = inv(10L, 5, 2);
        when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                1L, 100L, "CASE", null, null)).thenReturn(Optional.of(i));
        when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(i));
        when(inventoryRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        when(inventoryMovementRepository.save(any())).thenAnswer(a -> a.getArgument(0));

        var result = service.correct(1L, 100L, "CASE", null, null, 3, "棚卸結果");

        assertThat(result.quantityBefore()).isEqualTo(5);
        assertThat(result.quantityAfter()).isEqualTo(3);
        assertThat(result.reason()).isEqualTo("棚卸結果");
        verify(inventoryMovementRepository).save(any());
    }

    @Test @DisplayName("newQty < 0 はエラー")
    void correct_negativeQty_throws() {
        assertThatThrownBy(() -> service.correct(1L, 100L, "CASE", null, null, -1, "理由"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test @DisplayName("reason が空はエラー")
    void correct_emptyReason_throws() {
        assertThatThrownBy(() -> service.correct(1L, 100L, "CASE", null, null, 3, ""))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test @DisplayName("reason が null はエラー")
    void correct_nullReason_throws() {
        assertThatThrownBy(() -> service.correct(1L, 100L, "CASE", null, null, 3, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test @DisplayName("reason が 201文字以上はエラー")
    void correct_longReason_throws() {
        assertThatThrownBy(() -> service.correct(1L, 100L, "CASE", null, null, 3, "a".repeat(201)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test @DisplayName("棚卸ロック中はエラー")
    void correct_locked_throws() {
        when(locationService.findById(1L)).thenReturn(loc(1L, true));
        assertThatThrownBy(() -> service.correct(1L, 100L, "CASE", null, null, 3, "理由"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
    }

    @Test @DisplayName("在庫が存在しない場合エラー")
    void correct_notFound_throws() {
        when(locationService.findById(1L)).thenReturn(loc(1L, false));
        when(productService.findById(100L)).thenReturn(prod(100L));
        when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                1L, 100L, "CASE", null, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.correct(1L, 100L, "CASE", null, null, 3, "理由"))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo("INVENTORY_NOT_FOUND");
    }

    @Test @DisplayName("訂正後数量が引当数を下回る場合エラー")
    void correct_belowAllocated_throws() {
        when(locationService.findById(1L)).thenReturn(loc(1L, false));
        when(productService.findById(100L)).thenReturn(prod(100L));
        Inventory i = inv(10L, 5, 3);
        when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                1L, 100L, "CASE", null, null)).thenReturn(Optional.of(i));
        when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.correct(1L, 100L, "CASE", null, null, 2, "理由"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("CORRECTION_BELOW_ALLOCATED");
    }
}
