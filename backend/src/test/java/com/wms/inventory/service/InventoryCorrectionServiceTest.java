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
import com.wms.system.service.UserService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    @Mock private UserService userService;
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

    // --- getCorrectionHistory ---

    private InventoryMovement movement(Long id, int quantity, int quantityAfter, String reason, Long executedBy, OffsetDateTime at) {
        return InventoryMovement.builder()
                .warehouseId(1L).locationId(1L).locationCode("A-01")
                .productId(100L).productCode("P-001").productName("テスト商品")
                .unitType("CASE").movementType("CORRECTION")
                .quantity(quantity).quantityAfter(quantityAfter)
                .correctionReason(reason).executedAt(at).executedBy(executedBy).build();
    }

    @Test @DisplayName("訂正履歴: 正常系 - 結果を返す")
    void getCorrectionHistory_success() {
        OffsetDateTime now = OffsetDateTime.now();
        List<InventoryMovement> movements = List.of(
                movement(1L, -2, 3, "棚卸差異", 10L, now),
                movement(2L, 5, 10, "入荷漏れ", 20L, now.minusDays(1)));
        when(inventoryMovementRepository
                .findTop5ByWarehouseIdAndLocationIdAndProductIdAndUnitTypeAndMovementTypeOrderByExecutedAtDesc(
                        1L, 1L, 100L, "CASE", "CORRECTION"))
                .thenReturn(movements);
        when(userService.getUserFullNameMap(java.util.Set.of(10L, 20L)))
                .thenReturn(Map.of(10L, "山田太郎", 20L, "鈴木花子"));

        var result = service.getCorrectionHistory(1L, 1L, 100L, "CASE");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).quantityBefore()).isEqualTo(5);  // quantityAfter(3) - quantity(-2) = 5
        assertThat(result.get(0).quantityAfter()).isEqualTo(3);
        assertThat(result.get(0).reason()).isEqualTo("棚卸差異");
        assertThat(result.get(0).executedByName()).isEqualTo("山田太郎");
        assertThat(result.get(1).quantityBefore()).isEqualTo(5);  // quantityAfter(10) - quantity(5) = 5
        assertThat(result.get(1).quantityAfter()).isEqualTo(10);
        assertThat(result.get(1).executedByName()).isEqualTo("鈴木花子");
    }

    @Test @DisplayName("訂正履歴: 履歴なしの場合は空リスト")
    void getCorrectionHistory_empty() {
        when(inventoryMovementRepository
                .findTop5ByWarehouseIdAndLocationIdAndProductIdAndUnitTypeAndMovementTypeOrderByExecutedAtDesc(
                        1L, 1L, 100L, "CASE", "CORRECTION"))
                .thenReturn(List.of());

        var result = service.getCorrectionHistory(1L, 1L, 100L, "CASE");

        assertThat(result).isEmpty();
    }

    @Test @DisplayName("訂正履歴: ユーザー名が解決できない場合は空文字")
    void getCorrectionHistory_unknownUser() {
        OffsetDateTime now = OffsetDateTime.now();
        List<InventoryMovement> movements = List.of(
                movement(1L, -2, 3, "棚卸差異", 99L, now));
        when(inventoryMovementRepository
                .findTop5ByWarehouseIdAndLocationIdAndProductIdAndUnitTypeAndMovementTypeOrderByExecutedAtDesc(
                        1L, 1L, 100L, "CASE", "CORRECTION"))
                .thenReturn(movements);
        when(userService.getUserFullNameMap(java.util.Set.of(99L)))
                .thenReturn(Map.of());

        var result = service.getCorrectionHistory(1L, 1L, 100L, "CASE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).executedByName()).isEmpty();
    }
}
