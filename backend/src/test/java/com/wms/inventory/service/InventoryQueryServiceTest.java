package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.master.entity.Location;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.LocationService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryQueryService")
class InventoryQueryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private WarehouseService warehouseService;
    @Mock
    private LocationService locationService;
    @Mock
    private ProductService productService;

    @InjectMocks
    private InventoryQueryService inventoryQueryService;

    static void setField(Object obj, String fieldName, Object value) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }

    @Nested
    @DisplayName("searchByLocation")
    class SearchByLocationTests {

        @Test
        @DisplayName("正常にロケーション別在庫一覧を返す")
        void searchByLocation_success() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());

            Inventory inv = Inventory.builder()
                    .warehouseId(1L).locationId(10L).productId(100L)
                    .unitType("CASE").quantity(20).allocatedQty(5).build();
            setField(inv, "id", 1L);

            when(inventoryRepository.searchByLocation(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(inv)));

            Page<Inventory> result = inventoryQueryService.searchByLocation(
                    1L, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getQuantity()).isEqualTo(20);
        }

        @Test
        @DisplayName("フィルタ条件指定で検索できる")
        void searchByLocation_withFilters() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(inventoryRepository.searchByLocation(
                    eq(1L), eq("A-01"), eq(100L), eq("CASE"), eq("NORMAL"), any(Pageable.class)))
                    .thenReturn(Page.empty());

            Page<Inventory> result = inventoryQueryService.searchByLocation(
                    1L, "A-01", 100L, "CASE", "NORMAL", PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("倉庫が存在しない場合ResourceNotFoundException")
        void searchByLocation_warehouseNotFound() {
            when(warehouseService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫が見つかりません"));

            assertThatThrownBy(() -> inventoryQueryService.searchByLocation(
                    999L, null, null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("searchProductSummary")
    class SearchProductSummaryTests {

        @Test
        @DisplayName("正常に商品別集計を返す")
        void searchProductSummary_success() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());

            Object[] row = { 100L, 10L, 5L, 24L, 3L, 36L };
            List<Object[]> rows = java.util.Collections.singletonList(row);
            when(inventoryRepository.searchProductSummary(
                    eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(rows));

            Page<Object[]> result = inventoryQueryService.searchProductSummary(
                    1L, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("倉庫が存在しない場合ResourceNotFoundException")
        void searchProductSummary_warehouseNotFound() {
            when(warehouseService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫が見つかりません"));

            assertThatThrownBy(() -> inventoryQueryService.searchProductSummary(
                    999L, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getLocationCodeMap / getProductMap")
    class MapsTests {

        @Test
        @DisplayName("ロケーションコードマップを返す")
        void getLocationCodeMap_success() {
            Location loc = new Location();
            setField(loc, "id", 10L);
            loc.setLocationCode("A-01-01");
            when(locationService.findByIds(Set.of(10L))).thenReturn(Map.of(10L, loc));

            Map<Long, String> result = inventoryQueryService.getLocationCodeMap(Set.of(10L));

            assertThat(result).containsEntry(10L, "A-01-01");
        }

        @Test
        @DisplayName("空セットの場合空マップを返す")
        void getLocationCodeMap_empty() {
            Map<Long, String> result = inventoryQueryService.getLocationCodeMap(Set.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("商品マップを返す")
        void getProductMap_success() {
            Product product = new Product();
            setField(product, "id", 100L);
            product.setProductCode("P-001");
            when(productService.findAllByIds(Set.of(100L))).thenReturn(List.of(product));

            Map<Long, Product> result = inventoryQueryService.getProductMap(Set.of(100L));

            assertThat(result).containsKey(100L);
            assertThat(result.get(100L).getProductCode()).isEqualTo("P-001");
        }

        @Test
        @DisplayName("空セットの場合空マップを返す")
        void getProductMap_empty() {
            Map<Long, Product> result = inventoryQueryService.getProductMap(Set.of());
            assertThat(result).isEmpty();
        }
    }
}
