package com.wms.inventory.controller;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.entity.StocktakeLine;
import com.wms.inventory.service.InventoryBreakdownService;
import com.wms.inventory.service.InventoryCorrectionService;
import com.wms.inventory.service.InventoryMoveService;
import com.wms.inventory.service.InventoryQueryService;
import com.wms.inventory.service.StocktakeService;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        @DisplayName("storageConditionがnullの商品でstorageCondition=nullを返す")
        void listInventory_productSummary_nullStorageCondition() throws Exception {
            Object[] row = { 100L, 1L, 0L, 0L, 0L, 1L };
            when(inventoryQueryService.searchProductSummary(
                    eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(java.util.Collections.singletonList(row)));

            Product product = new Product();
            setField(product, "id", 100L);
            product.setProductCode("P-001");
            product.setProductName("テスト商品");
            product.setStorageCondition(null);
            product.setCaseQuantity(1);
            product.setBallQuantity(1);
            when(inventoryQueryService.getProductMap(Set.of(100L)))
                    .thenReturn(Map.of(100L, product));

            mockMvc.perform(get("/api/v1/inventory")
                            .param("warehouseId", "1")
                            .param("viewType", "PRODUCT_SUMMARY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].storageCondition").doesNotExist());
        }

        @Test
        @DisplayName("不正なstorageCondition値でnullを返す（IllegalArgumentExceptionをキャッチ）")
        void listInventory_productSummary_invalidStorageCondition() throws Exception {
            Object[] row = { 100L, 1L, 0L, 0L, 0L, 1L };
            when(inventoryQueryService.searchProductSummary(
                    eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(java.util.Collections.singletonList(row)));

            Product product = new Product();
            setField(product, "id", 100L);
            product.setProductCode("P-001");
            product.setProductName("テスト商品");
            product.setStorageCondition("INVALID_VALUE");
            product.setCaseQuantity(1);
            product.setBallQuantity(1);
            when(inventoryQueryService.getProductMap(Set.of(100L)))
                    .thenReturn(Map.of(100L, product));

            mockMvc.perform(get("/api/v1/inventory")
                            .param("warehouseId", "1")
                            .param("viewType", "PRODUCT_SUMMARY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].storageCondition").doesNotExist());
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

    // ========== moveInventory ==========

    @Nested
    @DisplayName("POST /api/v1/inventory/move")
    class MoveInventoryTests {

        @Test
        @DisplayName("在庫移動が成功し200を返す")
        void moveInventory_returns200() throws Exception {
            InventoryMoveService.MoveResult result = new InventoryMoveService.MoveResult(
                    1L, 2L, "A-01-01", "B-02-01",
                    "P-001", "テスト商品", "CASE",
                    10, 90, 10);

            when(inventoryMoveService.moveInventory(
                    eq(10L), eq(100L), eq("CASE"), isNull(), isNull(), eq(20L), eq(10)))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/inventory/move")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "fromLocationId": 10,
                                        "productId": 100,
                                        "unitType": "CASE",
                                        "toLocationId": 20,
                                        "moveQty": 10
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fromInventoryId").value(1))
                    .andExpect(jsonPath("$.toInventoryId").value(2))
                    .andExpect(jsonPath("$.fromLocationCode").value("A-01-01"))
                    .andExpect(jsonPath("$.toLocationCode").value("B-02-01"))
                    .andExpect(jsonPath("$.productCode").value("P-001"))
                    .andExpect(jsonPath("$.productName").value("テスト商品"))
                    .andExpect(jsonPath("$.unitType").value("CASE"))
                    .andExpect(jsonPath("$.movedQty").value(10))
                    .andExpect(jsonPath("$.fromQuantityAfter").value(90))
                    .andExpect(jsonPath("$.toQuantityAfter").value(10));
        }

        @Test
        @DisplayName("ロット番号・賞味期限を指定した在庫移動")
        void moveInventory_withLotAndExpiry_returns200() throws Exception {
            InventoryMoveService.MoveResult result = new InventoryMoveService.MoveResult(
                    1L, 2L, "A-01-01", "B-02-01",
                    "P-001", "テスト商品", "PIECE",
                    5, 15, 5);

            when(inventoryMoveService.moveInventory(
                    eq(10L), eq(100L), eq("PIECE"),
                    eq("LOT-001"), eq(LocalDate.of(2026, 12, 31)),
                    eq(20L), eq(5)))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/inventory/move")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "fromLocationId": 10,
                                        "productId": 100,
                                        "unitType": "PIECE",
                                        "lotNumber": "LOT-001",
                                        "expiryDate": "2026-12-31",
                                        "toLocationId": 20,
                                        "moveQty": 5
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.movedQty").value(5));
        }
    }

    // ========== breakdownInventory ==========

    @Nested
    @DisplayName("POST /api/v1/inventory/breakdown")
    class BreakdownInventoryTests {

        @Test
        @DisplayName("在庫ばらしが成功し200を返す")
        void breakdownInventory_returns200() throws Exception {
            InventoryBreakdownService.BreakdownResult result = new InventoryBreakdownService.BreakdownResult(
                    1L, 2L, "P-001", "テスト商品",
                    "CASE", "BALL", 3, 12, 7, 12);

            when(inventoryBreakdownService.breakdown(
                    eq(10L), eq(100L), eq("CASE"), eq(3), eq("BALL"), eq(10L)))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/inventory/breakdown")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "fromLocationId": 10,
                                        "productId": 100,
                                        "fromUnitType": "CASE",
                                        "breakdownQty": 3,
                                        "toUnitType": "BALL",
                                        "toLocationId": 10
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fromInventoryId").value(1))
                    .andExpect(jsonPath("$.toInventoryId").value(2))
                    .andExpect(jsonPath("$.productCode").value("P-001"))
                    .andExpect(jsonPath("$.productName").value("テスト商品"))
                    .andExpect(jsonPath("$.fromUnitType").value("CASE"))
                    .andExpect(jsonPath("$.toUnitType").value("BALL"))
                    .andExpect(jsonPath("$.breakdownQty").value(3))
                    .andExpect(jsonPath("$.convertedQty").value(12))
                    .andExpect(jsonPath("$.fromQuantityAfter").value(7))
                    .andExpect(jsonPath("$.toQuantityAfter").value(12));
        }
    }

    // ========== correctInventory ==========

    @Nested
    @DisplayName("POST /api/v1/inventory/correction")
    class CorrectionInventoryTests {

        @Test
        @DisplayName("在庫訂正が成功し200を返す")
        void correctInventory_returns200() throws Exception {
            InventoryCorrectionService.CorrectionResult result = new InventoryCorrectionService.CorrectionResult(
                    1L, "A-01-01", "P-001", "テスト商品", "CASE",
                    20, 15, "棚卸差異");

            when(inventoryCorrectionService.correct(
                    eq(10L), eq(100L), eq("CASE"), isNull(), isNull(), eq(15), eq("棚卸差異")))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/inventory/correction")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "locationId": 10,
                                        "productId": 100,
                                        "unitType": "CASE",
                                        "newQty": 15,
                                        "reason": "棚卸差異"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inventoryId").value(1))
                    .andExpect(jsonPath("$.locationCode").value("A-01-01"))
                    .andExpect(jsonPath("$.productCode").value("P-001"))
                    .andExpect(jsonPath("$.productName").value("テスト商品"))
                    .andExpect(jsonPath("$.unitType").value("CASE"))
                    .andExpect(jsonPath("$.quantityBefore").value(20))
                    .andExpect(jsonPath("$.quantityAfter").value(15))
                    .andExpect(jsonPath("$.reason").value("棚卸差異"));
        }

        @Test
        @DisplayName("ロット番号・賞味期限を指定した在庫訂正")
        void correctInventory_withLotAndExpiry_returns200() throws Exception {
            InventoryCorrectionService.CorrectionResult result = new InventoryCorrectionService.CorrectionResult(
                    1L, "A-01-01", "P-001", "テスト商品", "PIECE",
                    10, 8, "破損");

            when(inventoryCorrectionService.correct(
                    eq(10L), eq(100L), eq("PIECE"),
                    eq("LOT-001"), eq(LocalDate.of(2026, 6, 30)),
                    eq(8), eq("破損")))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/inventory/correction")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "locationId": 10,
                                        "productId": 100,
                                        "unitType": "PIECE",
                                        "lotNumber": "LOT-001",
                                        "expiryDate": "2026-06-30",
                                        "newQty": 8,
                                        "reason": "破損"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantityBefore").value(10))
                    .andExpect(jsonPath("$.quantityAfter").value(8));
        }
    }

    // ========== getCorrectionHistory ==========

    @Nested
    @DisplayName("GET /api/v1/inventory/correction-history")
    class CorrectionHistoryTests {

        @Test
        @DisplayName("訂正履歴を取得し200を返す")
        void getCorrectionHistory_returns200() throws Exception {
            OffsetDateTime now = OffsetDateTime.parse("2026-03-20T10:00:00+09:00");
            List<InventoryCorrectionService.CorrectionHistoryRecord> records = List.of(
                    new InventoryCorrectionService.CorrectionHistoryRecord(
                            now, 5, 3, "棚卸差異", "山田太郎"),
                    new InventoryCorrectionService.CorrectionHistoryRecord(
                            now.minusDays(1), 10, 5, "入荷漏れ", "鈴木花子"));

            when(inventoryCorrectionService.getCorrectionHistory(1L, 10L, 100L, "CASE"))
                    .thenReturn(records);

            mockMvc.perform(get("/api/v1/inventory/correction-history")
                            .param("warehouseId", "1")
                            .param("locationId", "10")
                            .param("productId", "100")
                            .param("unitType", "CASE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].quantityBefore").value(5))
                    .andExpect(jsonPath("$[0].quantityAfter").value(3))
                    .andExpect(jsonPath("$[0].reason").value("棚卸差異"))
                    .andExpect(jsonPath("$[0].executedByName").value("山田太郎"))
                    .andExpect(jsonPath("$[1].quantityBefore").value(10))
                    .andExpect(jsonPath("$[1].quantityAfter").value(5))
                    .andExpect(jsonPath("$[1].reason").value("入荷漏れ"))
                    .andExpect(jsonPath("$[1].executedByName").value("鈴木花子"));
        }

        @Test
        @DisplayName("必須パラメータ欠落で400を返す")
        void getCorrectionHistory_missingParam_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/correction-history")
                            .param("warehouseId", "1")
                            .param("locationId", "10"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("訂正履歴が空の場合は空配列を返す")
        void getCorrectionHistory_empty_returns200() throws Exception {
            when(inventoryCorrectionService.getCorrectionHistory(1L, 10L, 100L, "CASE"))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/inventory/correction-history")
                            .param("warehouseId", "1")
                            .param("locationId", "10")
                            .param("productId", "100")
                            .param("unitType", "CASE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ========== listStocktakes ==========

    @Nested
    @DisplayName("GET /api/v1/inventory/stocktakes")
    class ListStocktakesTests {

        private StocktakeHeader createHeader(Long id, String status, Long confirmedBy) {
            StocktakeHeader h = StocktakeHeader.builder()
                    .stocktakeNumber("ST-2026-00001")
                    .warehouseId(1L)
                    .targetDescription("A棟 全エリア")
                    .stocktakeDate(LocalDate.of(2026, 3, 20))
                    .status(status)
                    .startedAt(OffsetDateTime.now())
                    .startedBy(10L)
                    .confirmedBy(confirmedBy)
                    .confirmedAt(confirmedBy != null ? OffsetDateTime.now() : null)
                    .build();
            setField(h, "id", id);
            return h;
        }

        @Test
        @DisplayName("棚卸一覧を返す（confirmedByあり）")
        void listStocktakes_withConfirmedBy_returns200() throws Exception {
            StocktakeHeader h = createHeader(1L, "CONFIRMED", 20L);

            when(stocktakeQueryService.search(eq(1L), isNull(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(h)));
            when(userService.getUserFullNameMap(Set.of(10L, 20L)))
                    .thenReturn(Map.of(10L, "開始者", 20L, "確定者"));

            Warehouse wh = new Warehouse();
            wh.setWarehouseName("メイン倉庫");
            when(warehouseService.findById(1L)).thenReturn(wh);

            when(stocktakeQueryService.countTotalLines(1L)).thenReturn(50L);
            when(stocktakeQueryService.countCountedLines(1L)).thenReturn(50L);

            mockMvc.perform(get("/api/v1/inventory/stocktakes").param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].stocktakeNumber").value("ST-2026-00001"))
                    .andExpect(jsonPath("$.content[0].warehouseName").value("メイン倉庫"))
                    .andExpect(jsonPath("$.content[0].startedByName").value("開始者"))
                    .andExpect(jsonPath("$.content[0].confirmedByName").value("確定者"))
                    .andExpect(jsonPath("$.content[0].totalLines").value(50))
                    .andExpect(jsonPath("$.content[0].countedLines").value(50));
        }

        @Test
        @DisplayName("棚卸一覧を返す（confirmedByなし）")
        void listStocktakes_withoutConfirmedBy_returns200() throws Exception {
            StocktakeHeader h = createHeader(1L, "STARTED", null);

            when(stocktakeQueryService.search(eq(1L), isNull(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(h)));
            when(userService.getUserFullNameMap(Set.of(10L)))
                    .thenReturn(Map.of(10L, "開始者"));

            Warehouse wh = new Warehouse();
            wh.setWarehouseName("メイン倉庫");
            when(warehouseService.findById(1L)).thenReturn(wh);

            when(stocktakeQueryService.countTotalLines(1L)).thenReturn(30L);
            when(stocktakeQueryService.countCountedLines(1L)).thenReturn(10L);

            mockMvc.perform(get("/api/v1/inventory/stocktakes").param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].confirmedByName").doesNotExist())
                    .andExpect(jsonPath("$.content[0].totalLines").value(30))
                    .andExpect(jsonPath("$.content[0].countedLines").value(10));
        }

        @Test
        @DisplayName("ステータスフィルタ指定で棚卸一覧を返す")
        void listStocktakes_withStatusFilter_returns200() throws Exception {
            when(stocktakeQueryService.search(eq(1L), eq("STARTED"), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(userService.getUserFullNameMap(any())).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/inventory/stocktakes")
                            .param("warehouseId", "1")
                            .param("status", "STARTED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("倉庫名取得に失敗した場合は空文字を返す")
        void listStocktakes_warehouseNotFound_returnsEmptyName() throws Exception {
            StocktakeHeader h = createHeader(1L, "STARTED", null);

            when(stocktakeQueryService.search(eq(1L), isNull(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(h)));
            when(userService.getUserFullNameMap(any()))
                    .thenReturn(Map.of(10L, "開始者"));
            when(warehouseService.findById(1L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫が見つかりません"));

            when(stocktakeQueryService.countTotalLines(1L)).thenReturn(10L);
            when(stocktakeQueryService.countCountedLines(1L)).thenReturn(0L);

            mockMvc.perform(get("/api/v1/inventory/stocktakes").param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].warehouseName").value(""));
        }

        @Test
        @DisplayName("日付フィルタ指定で棚卸一覧を返す")
        void listStocktakes_withDateFilter_returns200() throws Exception {
            when(stocktakeQueryService.search(eq(1L), isNull(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(userService.getUserFullNameMap(any())).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/inventory/stocktakes")
                            .param("warehouseId", "1")
                            .param("dateFrom", "2026-01-01")
                            .param("dateTo", "2026-03-31"))
                    .andExpect(status().isOk());
        }
    }

    // ========== getStocktake ==========

    @Nested
    @DisplayName("GET /api/v1/inventory/stocktakes/{id}")
    class GetStocktakeTests {

        private StocktakeHeader createHeader(Long id, String status, Long confirmedBy) {
            StocktakeHeader h = StocktakeHeader.builder()
                    .stocktakeNumber("ST-2026-00001")
                    .warehouseId(1L)
                    .targetDescription("A棟 全エリア")
                    .stocktakeDate(LocalDate.of(2026, 3, 20))
                    .status(status)
                    .startedAt(OffsetDateTime.now())
                    .startedBy(10L)
                    .confirmedBy(confirmedBy)
                    .confirmedAt(confirmedBy != null ? OffsetDateTime.now() : null)
                    .build();
            setField(h, "id", id);
            return h;
        }

        private StocktakeLine createLine(Long id, Long countedBy, boolean isCounted, Integer quantityDiff) {
            StocktakeLine l = StocktakeLine.builder()
                    .locationId(100L)
                    .locationCode("A-01-01")
                    .productId(200L)
                    .productCode("P-001")
                    .productName("テスト商品")
                    .unitType("CASE")
                    .quantityBefore(20)
                    .quantityCounted(isCounted ? 18 : null)
                    .quantityDiff(quantityDiff)
                    .isCounted(isCounted)
                    .countedAt(isCounted ? OffsetDateTime.now() : null)
                    .countedBy(countedBy)
                    .build();
            setField(l, "id", id);
            return l;
        }

        @Test
        @DisplayName("CONFIRMED状態の棚卸詳細を返す（quantityDiffあり）")
        void getStocktake_confirmed_returnsQuantityDiff() throws Exception {
            StocktakeHeader h = createHeader(1L, "CONFIRMED", 20L);

            StocktakeLine line = createLine(101L, 30L, true, -2);

            when(stocktakeQueryService.findById(1L)).thenReturn(h);
            when(stocktakeQueryService.searchLines(eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(line)));
            when(userService.getUserFullNameMap(Set.of(10L, 20L, 30L)))
                    .thenReturn(Map.of(10L, "開始者", 20L, "確定者", 30L, "計測者"));

            Warehouse wh = new Warehouse();
            wh.setWarehouseName("メイン倉庫");
            when(warehouseService.findById(1L)).thenReturn(wh);

            when(stocktakeQueryService.countTotalLines(1L)).thenReturn(1L);
            when(stocktakeQueryService.countCountedLines(1L)).thenReturn(1L);

            mockMvc.perform(get("/api/v1/inventory/stocktakes/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.stocktakeNumber").value("ST-2026-00001"))
                    .andExpect(jsonPath("$.warehouseName").value("メイン倉庫"))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.confirmedByName").value("確定者"))
                    .andExpect(jsonPath("$.totalLines").value(1))
                    .andExpect(jsonPath("$.countedLines").value(1))
                    .andExpect(jsonPath("$.lines.content", hasSize(1)))
                    .andExpect(jsonPath("$.lines.content[0].lineId").value(101))
                    .andExpect(jsonPath("$.lines.content[0].quantityDiff").value(-2))
                    .andExpect(jsonPath("$.lines.content[0].countedByName").value("計測者"));
        }

        @Test
        @DisplayName("STARTED状態の棚卸詳細を返す（quantityDiffはnull）")
        void getStocktake_started_quantityDiffNull() throws Exception {
            StocktakeHeader h = createHeader(1L, "STARTED", null);

            StocktakeLine line = createLine(101L, 30L, true, -2);

            when(stocktakeQueryService.findById(1L)).thenReturn(h);
            when(stocktakeQueryService.searchLines(eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(line)));
            when(userService.getUserFullNameMap(Set.of(10L, 30L)))
                    .thenReturn(Map.of(10L, "開始者", 30L, "計測者"));

            Warehouse wh = new Warehouse();
            wh.setWarehouseName("メイン倉庫");
            when(warehouseService.findById(1L)).thenReturn(wh);

            when(stocktakeQueryService.countTotalLines(1L)).thenReturn(1L);
            when(stocktakeQueryService.countCountedLines(1L)).thenReturn(1L);

            mockMvc.perform(get("/api/v1/inventory/stocktakes/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("STARTED"))
                    .andExpect(jsonPath("$.confirmedByName").doesNotExist())
                    .andExpect(jsonPath("$.lines.content[0].quantityDiff").doesNotExist());
        }

        @Test
        @DisplayName("countedByがnullの明細はcountedByNameがnull")
        void getStocktake_countedByNull_countedByNameNull() throws Exception {
            StocktakeHeader h = createHeader(1L, "STARTED", null);

            StocktakeLine line = createLine(101L, null, false, null);

            when(stocktakeQueryService.findById(1L)).thenReturn(h);
            when(stocktakeQueryService.searchLines(eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(line)));
            when(userService.getUserFullNameMap(Set.of(10L)))
                    .thenReturn(Map.of(10L, "開始者"));

            Warehouse wh = new Warehouse();
            wh.setWarehouseName("メイン倉庫");
            when(warehouseService.findById(1L)).thenReturn(wh);

            when(stocktakeQueryService.countTotalLines(1L)).thenReturn(1L);
            when(stocktakeQueryService.countCountedLines(1L)).thenReturn(0L);

            mockMvc.perform(get("/api/v1/inventory/stocktakes/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines.content[0].countedByName").doesNotExist())
                    .andExpect(jsonPath("$.lines.content[0].isCounted").value(false));
        }

        @Test
        @DisplayName("倉庫名取得に失敗した場合は空文字を返す")
        void getStocktake_warehouseNotFound_returnsEmptyName() throws Exception {
            StocktakeHeader h = createHeader(1L, "STARTED", null);

            when(stocktakeQueryService.findById(1L)).thenReturn(h);
            when(stocktakeQueryService.searchLines(eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(userService.getUserFullNameMap(any()))
                    .thenReturn(Map.of(10L, "開始者"));
            when(warehouseService.findById(1L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫が見つかりません"));

            when(stocktakeQueryService.countTotalLines(1L)).thenReturn(0L);
            when(stocktakeQueryService.countCountedLines(1L)).thenReturn(0L);

            mockMvc.perform(get("/api/v1/inventory/stocktakes/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warehouseName").value(""));
        }

        @Test
        @DisplayName("フィルタパラメータ指定で棚卸詳細を返す")
        void getStocktake_withFilters_returns200() throws Exception {
            StocktakeHeader h = createHeader(1L, "STARTED", null);

            when(stocktakeQueryService.findById(1L)).thenReturn(h);
            when(stocktakeQueryService.searchLines(eq(1L), eq(true), eq("A-01"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(userService.getUserFullNameMap(any()))
                    .thenReturn(Map.of(10L, "開始者"));

            Warehouse wh = new Warehouse();
            wh.setWarehouseName("メイン倉庫");
            when(warehouseService.findById(1L)).thenReturn(wh);

            when(stocktakeQueryService.countTotalLines(1L)).thenReturn(0L);
            when(stocktakeQueryService.countCountedLines(1L)).thenReturn(0L);

            mockMvc.perform(get("/api/v1/inventory/stocktakes/1")
                            .param("isCounted", "true")
                            .param("locationCodePrefix", "A-01"))
                    .andExpect(status().isOk());
        }
    }

    // ========== startStocktake ==========

    @Nested
    @DisplayName("POST /api/v1/inventory/stocktakes")
    class StartStocktakeTests {

        @Test
        @DisplayName("棚卸開始が成功し201を返す")
        void startStocktake_returns201() throws Exception {
            OffsetDateTime now = OffsetDateTime.now();
            StocktakeService.StartResult result = new StocktakeService.StartResult(
                    1L, "ST-2026-00001", "A棟 全エリア", "STARTED", 50, now);

            when(stocktakeService.startStocktake(
                    eq(1L), eq(2L), isNull(),
                    eq(LocalDate.of(2026, 3, 25)), eq("テスト棚卸")))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/inventory/stocktakes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "warehouseId": 1,
                                        "buildingId": 2,
                                        "stocktakeDate": "2026-03-25",
                                        "note": "テスト棚卸"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.stocktakeNumber").value("ST-2026-00001"))
                    .andExpect(jsonPath("$.targetDescription").value("A棟 全エリア"))
                    .andExpect(jsonPath("$.status").value("STARTED"))
                    .andExpect(jsonPath("$.totalLines").value(50));
        }

        @Test
        @DisplayName("エリアID指定の棚卸開始")
        void startStocktake_withAreaId_returns201() throws Exception {
            OffsetDateTime now = OffsetDateTime.now();
            StocktakeService.StartResult result = new StocktakeService.StartResult(
                    2L, "ST-2026-00002", "A棟 1Fエリア", "STARTED", 20, now);

            when(stocktakeService.startStocktake(
                    eq(1L), eq(2L), eq(3L),
                    eq(LocalDate.of(2026, 3, 25)), isNull()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/inventory/stocktakes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "warehouseId": 1,
                                        "buildingId": 2,
                                        "areaId": 3,
                                        "stocktakeDate": "2026-03-25"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(2))
                    .andExpect(jsonPath("$.totalLines").value(20));
        }
    }

    // ========== saveStocktakeLines ==========

    @Nested
    @DisplayName("PUT /api/v1/inventory/stocktakes/{id}/lines")
    class SaveStocktakeLinesTests {

        @Test
        @DisplayName("棚卸明細保存が成功し200を返す")
        void saveStocktakeLines_returns200() throws Exception {
            StocktakeService.InputResult result = new StocktakeService.InputResult(2, 50L, 25L);

            when(stocktakeService.saveStocktakeLines(eq(1L), anyList()))
                    .thenReturn(result);

            mockMvc.perform(put("/api/v1/inventory/stocktakes/1/lines")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "lines": [
                                            {"lineId": 101, "actualQty": 18},
                                            {"lineId": 102, "actualQty": 25}
                                        ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updatedCount").value(2))
                    .andExpect(jsonPath("$.totalLines").value(50))
                    .andExpect(jsonPath("$.countedLines").value(25));
        }
    }

    // ========== confirmStocktake ==========

    @Nested
    @DisplayName("POST /api/v1/inventory/stocktakes/{id}/confirm")
    class ConfirmStocktakeTests {

        @Test
        @DisplayName("棚卸確定が成功し200を返す")
        void confirmStocktake_returns200() throws Exception {
            OffsetDateTime now = OffsetDateTime.now();
            StocktakeService.ConfirmResult result = new StocktakeService.ConfirmResult(
                    1L, "ST-2026-00001", "CONFIRMED", 50, 5, now);

            when(stocktakeService.confirmStocktake(1L)).thenReturn(result);

            mockMvc.perform(post("/api/v1/inventory/stocktakes/1/confirm"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.stocktakeNumber").value("ST-2026-00001"))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.totalLines").value(50))
                    .andExpect(jsonPath("$.adjustedLines").value(5));
        }
    }

    // ========== parseSort ==========

    @Nested
    @DisplayName("parseSort - unknown sort property fallback")
    class ParseSortTests {

        @Test
        @DisplayName("不明なソートプロパティはデフォルトにフォールバックする")
        void parseSort_unknownProperty_fallsToDefault() throws Exception {
            when(inventoryQueryService.searchByLocation(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(inventoryQueryService.getLocationCodeMap(any())).thenReturn(Map.of());
            when(inventoryQueryService.getProductMap(any())).thenReturn(Map.of());

            // "unknownProp" is not in LOCATION_SORT_PROPERTIES, should fall back to "locationCode"
            mockMvc.perform(get("/api/v1/inventory")
                            .param("warehouseId", "1")
                            .param("sort", "unknownProp,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("カンマなしのソート指定でデフォルトASCになる")
        void parseSort_noDirection_defaultsToAsc() throws Exception {
            when(inventoryQueryService.searchByLocation(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(inventoryQueryService.getLocationCodeMap(any())).thenReturn(Map.of());
            when(inventoryQueryService.getProductMap(any())).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/inventory")
                            .param("warehouseId", "1")
                            .param("sort", "locationCode"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("棚卸一覧で不明なソートプロパティはデフォルトにフォールバックする")
        void parseSort_stocktakes_unknownProperty_fallsToDefault() throws Exception {
            when(stocktakeQueryService.search(eq(1L), isNull(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(userService.getUserFullNameMap(any())).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/inventory/stocktakes")
                            .param("warehouseId", "1")
                            .param("sort", "invalidField,desc"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("getLocationCapacity")
    class GetLocationCapacityTests {

        @Test
        @DisplayName("正常系: CASE の収容上限を取得")
        void getCapacity_case_success() throws Exception {
            when(inventoryMoveService.getLocationCapacity("CASE")).thenReturn(10);

            mockMvc.perform(get("/api/v1/inventory/location-capacity")
                            .param("unitType", "CASE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unitType").value("CASE"))
                    .andExpect(jsonPath("$.maxQuantity").value(10));
        }

        @Test
        @DisplayName("正常系: BALL の収容上限を取得")
        void getCapacity_ball_success() throws Exception {
            when(inventoryMoveService.getLocationCapacity("BALL")).thenReturn(6);

            mockMvc.perform(get("/api/v1/inventory/location-capacity")
                            .param("unitType", "BALL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unitType").value("BALL"))
                    .andExpect(jsonPath("$.maxQuantity").value(6));
        }

        @Test
        @DisplayName("正常系: PIECE の収容上限を取得")
        void getCapacity_piece_success() throws Exception {
            when(inventoryMoveService.getLocationCapacity("PIECE")).thenReturn(100);

            mockMvc.perform(get("/api/v1/inventory/location-capacity")
                            .param("unitType", "PIECE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unitType").value("PIECE"))
                    .andExpect(jsonPath("$.maxQuantity").value(100));
        }

        @Test
        @DisplayName("異常系: 不正な unitType でリクエスト")
        void getCapacity_invalidUnitType_returns422() throws Exception {
            when(inventoryMoveService.getLocationCapacity("INVALID"))
                    .thenThrow(new com.wms.shared.exception.BusinessRuleViolationException(
                            "VALIDATION_ERROR", "不正な荷姿: INVALID"));

            mockMvc.perform(get("/api/v1/inventory/location-capacity")
                            .param("unitType", "INVALID"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }
}
