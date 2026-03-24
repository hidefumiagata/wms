package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.entity.Location;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.LocationRepository;
import com.wms.master.service.AreaService;
import com.wms.master.service.BuildingService;
import com.wms.master.entity.Product;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StocktakeService")
class StocktakeServiceTest {

    @Mock private StocktakeHeaderRepository headerRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private BuildingService buildingService;
    @Mock private AreaService areaService;
    @Mock private ProductService productService;
    @InjectMocks private StocktakeService service;

    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); }

    void setUpSecurity() {
        WmsUserDetails ud = new WmsUserDetails(10L, "user", "pw", "WH-001",
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }

    static void setField(Object obj, String name, Object value) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, value); return; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        throw new RuntimeException("Field not found: " + name);
    }

    Location loc(Long id, String code, boolean locked) {
        Location l = new Location(); setField(l, "id", id);
        l.setLocationCode(code); l.setWarehouseId(1L); l.setIsStocktakingLocked(locked); return l;
    }

    Building building(Long id, String name) {
        Building b = new Building(); setField(b, "id", id); b.setBuildingName(name); return b;
    }

    Area area(Long id, Long buildingId, String name) {
        Area a = new Area(); setField(a, "id", id); a.setBuildingId(buildingId); a.setAreaName(name); return a;
    }

    @Test @DisplayName("正常系: 棚卸開始（棟全体）")
    void start_wholeBldg_success() {
        setUpSecurity();
        when(warehouseService.findById(1L)).thenReturn(new Warehouse());
        when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
        Location l1 = loc(10L, "A-01-01", false);
        when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, null)).thenReturn(List.of(l1));

        Inventory inv = Inventory.builder().warehouseId(1L).locationId(10L).productId(100L)
                .unitType("CASE").quantity(5).allocatedQty(0).build();
        setField(inv, "id", 1L);
        Product prod = new Product(); setField(prod, "id", 100L);
        prod.setProductCode("P-001"); prod.setProductName("テスト商品");
        when(inventoryRepository.findByLocationIdsWithPositiveQty(any())).thenReturn(List.of(inv));
        when(productService.findAllByIds(any())).thenReturn(List.of(prod));

        when(headerRepository.findMaxSequenceByYear("2026")).thenReturn(null);
        when(headerRepository.save(any(StocktakeHeader.class))).thenAnswer(i -> {
            StocktakeHeader h = i.getArgument(0); setField(h, "id", 42L); return h;
        });
        when(locationRepository.saveAll(any())).thenReturn(List.of());

        var result = service.startStocktake(1L, 2L, null, LocalDate.of(2026, 3, 13), "月次");

        assertThat(result.stocktakeNumber()).isEqualTo("ST-2026-00001");
        assertThat(result.targetDescription()).isEqualTo("A棟 全エリア");
        assertThat(result.totalLines()).isEqualTo(1);
        verify(locationRepository).saveAll(any());
    }

    @Test @DisplayName("正常系: 棚卸開始（エリア指定）")
    void start_areaSpecified_success() {
        setUpSecurity();
        when(warehouseService.findById(1L)).thenReturn(new Warehouse());
        when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
        when(areaService.findById(5L)).thenReturn(area(5L, 2L, "冷蔵エリア"));
        Location l1 = loc(10L, "A-01-01", false);
        when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, 5L)).thenReturn(List.of(l1));

        when(inventoryRepository.findByLocationIdsWithPositiveQty(any())).thenReturn(List.of());
        when(productService.findAllByIds(any())).thenReturn(List.of());
        when(headerRepository.findMaxSequenceByYear("2026")).thenReturn(3);
        when(headerRepository.save(any())).thenAnswer(i -> { StocktakeHeader h = i.getArgument(0); setField(h, "id", 42L); return h; });
        when(locationRepository.saveAll(any())).thenReturn(List.of());

        var result = service.startStocktake(1L, 2L, 5L, LocalDate.of(2026, 3, 13), null);

        assertThat(result.stocktakeNumber()).isEqualTo("ST-2026-00004");
        assertThat(result.targetDescription()).isEqualTo("A棟 冷蔵エリア");
    }

    @Test @DisplayName("エリアが棟に属さない場合エラー")
    void start_areaMismatch_throws() {
        when(warehouseService.findById(1L)).thenReturn(new Warehouse());
        when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
        when(areaService.findById(5L)).thenReturn(area(5L, 99L, "他棟エリア"));

        assertThatThrownBy(() -> service.startStocktake(1L, 2L, 5L, LocalDate.of(2026, 3, 13), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo("AREA_NOT_FOUND");
    }

    @Test @DisplayName("対象ロケーションなしの場合エラー")
    void start_noLocations_throws() {
        when(warehouseService.findById(1L)).thenReturn(new Warehouse());
        when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
        when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, null)).thenReturn(List.of());

        assertThatThrownBy(() -> service.startStocktake(1L, 2L, null, LocalDate.of(2026, 3, 13), null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test @DisplayName("棚卸ロック中のロケーションがある場合エラー")
    void start_locked_throws() {
        when(warehouseService.findById(1L)).thenReturn(new Warehouse());
        when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
        when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, null))
                .thenReturn(List.of(loc(10L, "A-01-01", true)));

        assertThatThrownBy(() -> service.startStocktake(1L, 2L, null, LocalDate.of(2026, 3, 13), null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
    }
}
