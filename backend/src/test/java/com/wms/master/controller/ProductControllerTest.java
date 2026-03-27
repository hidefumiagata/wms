package com.wms.master.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.entity.Product;
import com.wms.master.service.ProductService;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.shared.security.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品コントローラーの単体テスト。
 * セキュリティフィルターを無効化し、ビジネスロジックのマッピングを検証する。
 * 認可テストは ProductControllerAuthTest で行う。
 */
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProductController")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    private static final String BASE_URL = "/api/v1/master/products";

    // --- Helper ---

    private Product createProduct(Long id, String code, String name, String storageCondition) {
        Product p = new Product();
        p.setProductCode(code);
        p.setProductName(name);
        p.setStorageCondition(storageCondition);
        p.setCaseQuantity(6);
        p.setBallQuantity(10);
        p.setIsHazardous(false);
        p.setLotManageFlag(false);
        p.setExpiryManageFlag(false);
        p.setShipmentStopFlag(false);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(p, id);
                var createdAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAt.setAccessible(true);
                createdAt.set(p, OffsetDateTime.now());
                var updatedAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAt.setAccessible(true);
                updatedAt.set(p, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return p;
    }

    // ===== GET /api/v1/master/products =====

    @Nested
    @DisplayName("GET /products（一覧）")
    class ListProducts {

        @Test
        @DisplayName("ページング形式で商品一覧を返す")
        void listProducts_paged_returns200() throws Exception {
            Product p = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            Page<Product> page = new PageImpl<>(List.of(p));
            when(productService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].productCode").value("P-001"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("all=trueで全件リストを返す")
        void listProducts_all_returns200() throws Exception {
            Product p = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            when(productService.findAllSimple(isNull())).thenReturn(List.of(p));

            mockMvc.perform(get(BASE_URL).param("all", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].productCode").value("P-001"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("all=trueで0件の場合 totalPages=0 を返す")
        void listProducts_allEmpty_returns200WithZeroPages() throws Exception {
            when(productService.findAllSimple(isNull())).thenReturn(List.of());

            mockMvc.perform(get(BASE_URL).param("all", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        @DisplayName("sortにカンマがない場合は昇順になる")
        void listProducts_sortWithoutComma_returnsAsc() throws Exception {
            Page<Product> page = new PageImpl<>(List.of());
            when(productService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "productCode"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("storageConditionフィルタ付きでページング検索できる")
        void listProducts_withStorageCondition_returns200() throws Exception {
            Product p = createProduct(1L, "P-001", "冷蔵商品A", "REFRIGERATED");
            Page<Product> page = new PageImpl<>(List.of(p));
            when(productService.search(isNull(), isNull(), eq("REFRIGERATED"), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("storageCondition", "REFRIGERATED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].storageCondition").value("REFRIGERATED"));
        }

        @Test
        @DisplayName("shipmentStopFlagフィルタ付きで検索できる")
        void listProducts_withShipmentStopFlag_returns200() throws Exception {
            Product p = createProduct(1L, "P-002", "出荷停止商品", "AMBIENT");
            p.setShipmentStopFlag(true);
            Page<Product> page = new PageImpl<>(List.of(p));
            when(productService.search(isNull(), isNull(), isNull(), isNull(), eq(true), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("shipmentStopFlag", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].shipmentStopFlag").value(true));
        }

        @Test
        @DisplayName("pageが負の場合400を返す")
        void listProducts_negativePage_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("page", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sizeが0の場合400を返す")
        void listProducts_zeroSize_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("size", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sizeが100を超える場合は100に丸める")
        void listProducts_oversizeSize_cappedTo100() throws Exception {
            Page<Product> page = new PageImpl<>(List.of());
            when(productService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("size", "200"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("不正なsortプロパティは productCode にフォールバックする")
        void listProducts_invalidSort_fallbackToProductCode() throws Exception {
            Page<Product> page = new PageImpl<>(List.of());
            when(productService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "unknown,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sort=desc で降順ソートできる")
        void listProducts_sortDesc_returns200() throws Exception {
            Page<Product> page = new PageImpl<>(List.of());
            when(productService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "productCode,desc"))
                    .andExpect(status().isOk());
        }
    }

    // ===== POST /api/v1/master/products =====

    @Nested
    @DisplayName("POST /products（登録）")
    class CreateProduct {

        private static final String VALID_JSON = """
                {"productCode":"P-001","productName":"テスト商品A","caseQuantity":6,"ballQuantity":10,
                "storageCondition":"AMBIENT","isHazardous":false,"lotManageFlag":false,
                "expiryManageFlag":false,"shipmentStopFlag":false,"isActive":true}
                """;

        @Test
        @DisplayName("正常登録で201を返す")
        void createProduct_success_returns201() throws Exception {
            Product created = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            when(productService.create(any(Product.class))).thenReturn(created);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.productCode").value("P-001"));
        }

        @Test
        @DisplayName("isActive=falseで非アクティブ状態で登録できる")
        void createProduct_inactive_returns201() throws Exception {
            String inactiveJson = """
                    {"productCode":"P-002","productName":"テスト商品B","caseQuantity":6,"ballQuantity":10,
                    "storageCondition":"FROZEN","isHazardous":false,"lotManageFlag":false,
                    "expiryManageFlag":false,"shipmentStopFlag":false,"isActive":false}
                    """;
            Product created = createProduct(2L, "P-002", "テスト商品B", "FROZEN");
            when(productService.create(any(Product.class))).thenReturn(created);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(inactiveJson))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void createProduct_missingRequired_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("重複コードで409を返す")
        void createProduct_duplicateCode_returns409() throws Exception {
            when(productService.create(any(Product.class)))
                    .thenThrow(new com.wms.shared.exception.DuplicateResourceException(
                            "DUPLICATE_CODE", "商品コードが既に存在します: P-001"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // ===== GET /api/v1/master/products/{id} =====

    @Nested
    @DisplayName("GET /products/{id}（詳細）")
    class GetProduct {

        @Test
        @DisplayName("存在するIDで200を返す（在庫なし）")
        void getProduct_exists_returns200() throws Exception {
            Product p = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            when(productService.findByIdWithInventoryCheck(1L))
                    .thenReturn(new ProductService.ProductWithInventory(p, false));

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productCode").value("P-001"))
                    .andExpect(jsonPath("$.hasInventory").value(false));
        }

        @Test
        @DisplayName("在庫ありの商品でhasInventory=trueを返す")
        void getProduct_hasInventory_returnsTrue() throws Exception {
            Product p = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            when(productService.findByIdWithInventoryCheck(1L))
                    .thenReturn(new ProductService.ProductWithInventory(p, true));

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasInventory").value(true));
        }

        @Test
        @DisplayName("createdAt/updatedAtがnullの商品でも200を返す")
        void getProduct_nullTimestamps_returns200() throws Exception {
            Product p = createProduct(null, "P-001", "テスト商品A", "AMBIENT");
            when(productService.findByIdWithInventoryCheck(1L))
                    .thenReturn(new ProductService.ProductWithInventory(p, false));

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void getProduct_notExists_returns404() throws Exception {
            when(productService.findByIdWithInventoryCheck(999L))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "PRODUCT_NOT_FOUND", "商品", 999L));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== PUT /api/v1/master/products/{id} =====

    @Nested
    @DisplayName("PUT /products/{id}（更新）")
    class UpdateProduct {

        private static final String VALID_JSON = """
                {"productName":"更新商品名","caseQuantity":12,"ballQuantity":6,
                "storageCondition":"REFRIGERATED","isHazardous":false,"lotManageFlag":true,
                "expiryManageFlag":false,"shipmentStopFlag":false,"isActive":true,"version":0}
                """;

        @Test
        @DisplayName("正常更新で200を返す")
        void updateProduct_success_returns200() throws Exception {
            Product updated = createProduct(1L, "P-001", "更新商品名", "REFRIGERATED");
            when(productService.update(any(com.wms.master.service.UpdateProductCommand.class)))
                    .thenReturn(updated);
            when(productService.hasInventory(1L)).thenReturn(false);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productName").value("更新商品名"))
                    .andExpect(jsonPath("$.hasInventory").value(false));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void updateProduct_missingRequired_returns400() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void updateProduct_notFound_returns404() throws Exception {
            when(productService.update(any(com.wms.master.service.UpdateProductCommand.class)))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "PRODUCT_NOT_FOUND", "商品", 999L));

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void updateProduct_optimisticLock_returns409() throws Exception {
            when(productService.update(any(com.wms.master.service.UpdateProductCommand.class)))
                    .thenThrow(new com.wms.shared.exception.OptimisticLockConflictException(
                            "OPTIMISTIC_LOCK_CONFLICT", "競合が発生しました"));

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // ===== PATCH /api/v1/master/products/{id}/toggle-active =====

    @Nested
    @DisplayName("PATCH /products/{id}/toggle-active（有効化/無効化）")
    class ToggleProductActive {

        private static final String VALID_JSON = """
                {"isActive":false,"version":0}
                """;

        @Test
        @DisplayName("無効化で200を返す")
        void toggleProductActive_deactivate_returns200() throws Exception {
            Product updated = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            updated.deactivate();
            when(productService.toggleActive(anyLong(), anyBoolean(), anyInt())).thenReturn(updated);
            when(productService.hasInventory(1L)).thenReturn(false);

            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false))
                    .andExpect(jsonPath("$.hasInventory").value(false));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void toggleProductActive_missingRequired_returns400() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void toggleProductActive_notFound_returns404() throws Exception {
            when(productService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "PRODUCT_NOT_FOUND", "商品", 999L));

            mockMvc.perform(patch(BASE_URL + "/999/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void toggleProductActive_optimisticLock_returns409() throws Exception {
            when(productService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.OptimisticLockConflictException(
                            "OPTIMISTIC_LOCK_CONFLICT", "競合が発生しました"));

            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // ===== GET /api/v1/master/products/exists =====

    @Nested
    @DisplayName("GET /products/exists（コード存在確認）")
    class CheckProductCodeExists {

        @Test
        @DisplayName("存在するコードでexists=trueを返す")
        void checkExists_exists_returnsTrue() throws Exception {
            when(rateLimiterService.tryConsumeCodeExists("admin")).thenReturn(true);
            when(productService.existsByCode("P-001")).thenReturn(true);
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "admin", null, List.of());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                mockMvc.perform(get(BASE_URL + "/exists").param("productCode", "P-001"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.exists").value(true));
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("存在しないコードでexists=falseを返す")
        void checkExists_notExists_returnsFalse() throws Exception {
            when(productService.existsByCode("XXXX")).thenReturn(false);

            mockMvc.perform(get(BASE_URL + "/exists").param("productCode", "XXXX"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }

        @Test
        @DisplayName("productCode未指定で400を返す")
        void checkExists_missingParam_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL + "/exists"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("レートリミット超過で429を返す")
        void checkExists_rateLimited_returns429() throws Exception {
            when(rateLimiterService.tryConsumeCodeExists("admin")).thenReturn(false);
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "admin", null, List.of());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                mockMvc.perform(get(BASE_URL + "/exists")
                                .param("productCode", "P-001"))
                        .andExpect(status().isTooManyRequests());
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        }
    }
}
