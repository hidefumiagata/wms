package com.wms.inventory.controller;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.service.InventoryQueryService;
import com.wms.master.entity.Product;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InventoryController")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryQueryService inventoryQueryService;
    @MockitoBean
    private com.wms.inventory.service.InventoryMoveService inventoryMoveService;
    @MockitoBean
    private com.wms.inventory.service.InventoryBreakdownService inventoryBreakdownService;
    @MockitoBean
    private com.wms.inventory.service.InventoryCorrectionService inventoryCorrectionService;
    @MockitoBean
    private com.wms.inventory.service.StocktakeQueryService stocktakeQueryService;
    @MockitoBean
    private com.wms.inventory.service.StocktakeService stocktakeService;
    @MockitoBean
    private com.wms.master.service.WarehouseService warehouseService;
    @MockitoBean
    private com.wms.system.service.UserService userService;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

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
    @DisplayName("GET /api/v1/inventory (LOCATION)")
    class LocationViewTests {

        @Test
        @DisplayName("ロケーション別在庫一覧を返す")
        void listInventory_location_returns200() throws Exception {
            Inventory inv = Inventory.builder()
                    .warehouseId(1L).locationId(10L).productId(100L)
                    .unitType("CASE").quantity(20).allocatedQty(5).build();
            setField(inv, "id", 1L);
            setField(inv, "updatedAt", OffsetDateTime.now());

            when(inventoryQueryService.searchByLocation(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(inv)));
            when(inventoryQueryService.getLocationCodeMap(Set.of(10L)))
                    .thenReturn(Map.of(10L, "A-01-01"));

            Product product = new Product();
            setField(product, "id", 100L);
            product.setProductCode("P-001");
            product.setProductName("テスト商品");
            when(inventoryQueryService.getProductMap(Set.of(100L)))
                    .thenReturn(Map.of(100L, product));

            mockMvc.perform(get("/api/v1/inventory").param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].locationCode").value("A-01-01"))
                    .andExpect(jsonPath("$.content[0].productCode").value("P-001"))
                    .andExpect(jsonPath("$.content[0].quantity").value(20))
                    .andExpect(jsonPath("$.content[0].allocatedQty").value(5))
                    .andExpect(jsonPath("$.content[0].availableQty").value(15));
        }

        @Test
        @DisplayName("フィルタ条件指定でロケーション別在庫を返す")
        void listInventory_withFilters_returns200() throws Exception {
            when(inventoryQueryService.searchByLocation(
                    eq(1L), eq("A-01"), eq(100L), eq("CASE"), eq("AMBIENT"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(inventoryQueryService.getLocationCodeMap(any())).thenReturn(Map.of());
            when(inventoryQueryService.getProductMap(any())).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/inventory")
                            .param("warehouseId", "1")
                            .param("locationCodePrefix", "A-01")
                            .param("productId", "100")
                            .param("unitType", "CASE")
                            .param("storageCondition", "AMBIENT"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("商品情報がない在庫を空文字で返す")
        void listInventory_productNotFound_returnsEmptyStrings() throws Exception {
            Inventory inv = Inventory.builder()
                    .warehouseId(1L).locationId(10L).productId(999L)
                    .unitType("CASE").quantity(5).allocatedQty(0).build();
            setField(inv, "id", 1L);
            setField(inv, "updatedAt", OffsetDateTime.now());

            when(inventoryQueryService.searchByLocation(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(inv)));
            when(inventoryQueryService.getLocationCodeMap(any())).thenReturn(Map.of());
            when(inventoryQueryService.getProductMap(any())).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/inventory").param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].productCode").value(""))
                    .andExpect(jsonPath("$.content[0].productName").value(""));
        }

        @Test
        @DisplayName("倉庫が存在しない場合404を返す")
        void listInventory_warehouseNotFound_returns404() throws Exception {
            when(inventoryQueryService.searchByLocation(
                    eq(999L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫が見つかりません"));

            mockMvc.perform(get("/api/v1/inventory").param("warehouseId", "999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/inventory (PRODUCT_SUMMARY)")
    class ProductSummaryTests {

        @Test
        @DisplayName("商品別集計を返す")
        void listInventory_productSummary_returns200() throws Exception {
            Object[] row = { 100L, 10L, 5L, 24L, 3L, 36L };
            List<Object[]> rows = java.util.Collections.singletonList(row);
            when(inventoryQueryService.searchProductSummary(
                    eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(rows));

            Product product = new Product();
            setField(product, "id", 100L);
            product.setProductCode("P-001");
            product.setProductName("テスト商品");
            product.setStorageCondition("NORMAL");
            product.setCaseQuantity(24);
            product.setBallQuantity(6);
            when(inventoryQueryService.getProductMap(Set.of(100L)))
                    .thenReturn(Map.of(100L, product));

            mockMvc.perform(get("/api/v1/inventory")
                            .param("warehouseId", "1")
                            .param("viewType", "PRODUCT_SUMMARY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].productCode").value("P-001"))
                    .andExpect(jsonPath("$.content[0].caseQuantity").value(10))
                    .andExpect(jsonPath("$.content[0].ballQuantity").value(5))
                    .andExpect(jsonPath("$.content[0].pieceQuantity").value(24));
        }

        @Test
        @DisplayName("商品情報がない場合のPRODUCT_SUMMARY")
        void listInventory_productSummary_productNotFound() throws Exception {
            Object[] row = { 999L, 1L, 0L, 0L, 0L, 1L };
            List<Object[]> rows = java.util.Collections.singletonList(row);
            when(inventoryQueryService.searchProductSummary(
                    eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(rows));
            when(inventoryQueryService.getProductMap(any())).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/inventory")
                            .param("warehouseId", "1")
                            .param("viewType", "PRODUCT_SUMMARY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].productCode").value(""));
        }

        @Test
        @DisplayName("ソートdesc指定で商品別集計を返す")
        void listInventory_productSummary_sortDesc() throws Exception {
            when(inventoryQueryService.searchProductSummary(
                    eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(inventoryQueryService.getProductMap(any()))
                    .thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/inventory")
                            .param("warehouseId", "1")
                            .param("viewType", "PRODUCT_SUMMARY")
                            .param("sort", "productCode,desc"))
                    .andExpect(status().isOk());
        }
    }
}
