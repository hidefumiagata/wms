package com.wms.master.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.generated.model.CreateWarehouseRequest;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateWarehouseRequest;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WarehouseController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WarehouseController")
class WarehouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String BASE_URL = "/api/v1/master/warehouses";

    // ========== listWarehouses ==========

    @Nested
    @DisplayName("GET /api/v1/master/warehouses")
    class ListTests {

        @Test
        @DisplayName("ページング形式で一覧を返す")
        void list_paged_returnsPageResponse() throws Exception {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            var page = new PageImpl<>(List.of(w), PageRequest.of(0, 20), 1);
            when(warehouseService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].warehouseCode").value("WARA"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("descソートで一覧を返す")
        void list_paged_descSort() throws Exception {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            var page = new PageImpl<>(List.of(w), PageRequest.of(0, 20), 1);
            when(warehouseService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "warehouseName,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("ソート方向なしでasc扱いになる")
        void list_paged_sortWithoutDirection() throws Exception {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            var page = new PageImpl<>(List.of(w), PageRequest.of(0, 20), 1);
            when(warehouseService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "warehouseCode"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("all=trueでWarehousePageResponse形式のリストを返す")
        void list_all_returnsWrappedList() throws Exception {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseService.findAllSimple(true)).thenReturn(List.of(w));

            mockMvc.perform(get(BASE_URL).param("all", "true").param("isActive", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].warehouseCode").value("WARA"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("許可されていないソートプロパティはデフォルトにフォールバック")
        void list_paged_invalidSortProperty_fallbackToDefault() throws Exception {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            var page = new PageImpl<>(List.of(w), PageRequest.of(0, 20), 1);
            when(warehouseService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "malicious_column,asc"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(warehouseService).search(any(), any(), any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort().getOrderFor("warehouseCode")).isNotNull();
        }

        @Test
        @DisplayName("ページサイズが上限100を超える場合は400を返す（Bean Validation @Max(100)）")
        void list_paged_sizeExceeding100_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("size", "101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("all=falseで空ページを返す")
        void list_paged_emptyResult() throws Exception {
            var page = new PageImpl<Warehouse>(List.of(), PageRequest.of(0, 20), 0);
            when(warehouseService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // ========== createWarehouse ==========

    @Nested
    @DisplayName("POST /api/v1/master/warehouses")
    class CreateTests {

        @Test
        @DisplayName("正常な登録リクエストで201を返す")
        void create_success_returns201() throws Exception {
            Warehouse created = createWarehouse(1L, "WARB", "大阪DC");
            when(warehouseService.create(any(Warehouse.class))).thenReturn(created);

            CreateWarehouseRequest request = new CreateWarehouseRequest()
                    .warehouseCode("WARB")
                    .warehouseName("大阪DC");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/master/warehouses/1"))
                    .andExpect(jsonPath("$.warehouseCode").value("WARB"))
                    .andExpect(jsonPath("$.warehouseName").value("大阪DC"));
        }

        @Test
        @DisplayName("重複コードで409を返す")
        void create_duplicateCode_returns409() throws Exception {
            when(warehouseService.create(any(Warehouse.class)))
                    .thenThrow(new DuplicateResourceException("DUPLICATE_CODE", "重複"));

            CreateWarehouseRequest request = new CreateWarehouseRequest()
                    .warehouseCode("WARA")
                    .warehouseName("東京DC");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void create_missingRequired_returns400() throws Exception {
            CreateWarehouseRequest request = new CreateWarehouseRequest();

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("倉庫コード形式不正で400を返す")
        void create_invalidCodePattern_returns400() throws Exception {
            CreateWarehouseRequest request = new CreateWarehouseRequest()
                    .warehouseCode("invalid")
                    .warehouseName("テスト倉庫");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========== getWarehouse ==========

    @Nested
    @DisplayName("GET /api/v1/master/warehouses/{id}")
    class GetTests {

        @Test
        @DisplayName("存在するIDで200を返す")
        void get_exists_returns200() throws Exception {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseService.findById(1L)).thenReturn(w);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warehouseCode").value("WARA"));
        }

        @Test
        @DisplayName("日時がnullの場合もレスポンスを返せる")
        void get_nullTimestamps_returns200() throws Exception {
            Warehouse w = createWarehouseWithoutTimestamps(2L, "WARB", "大阪DC");
            when(warehouseService.findById(2L)).thenReturn(w);

            mockMvc.perform(get(BASE_URL + "/2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warehouseCode").value("WARB"))
                    .andExpect(jsonPath("$.createdAt").doesNotExist());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void get_notFound_returns404() throws Exception {
            when(warehouseService.findById(999L))
                    .thenThrow(ResourceNotFoundException.of("WAREHOUSE_NOT_FOUND", "倉庫", 999L));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ========== updateWarehouse ==========

    @Nested
    @DisplayName("PUT /api/v1/master/warehouses/{id}")
    class UpdateTests {

        @Test
        @DisplayName("正常な更新で200を返す")
        void update_success_returns200() throws Exception {
            Warehouse updated = createWarehouse(1L, "WARA", "東京DC（新）");
            when(warehouseService.update(eq(1L), any(), any(), any(), any())).thenReturn(updated);

            UpdateWarehouseRequest request = new UpdateWarehouseRequest()
                    .warehouseName("東京DC（新）")
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warehouseName").value("東京DC（新）"));
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void update_conflict_returns409() throws Exception {
            when(warehouseService.update(eq(1L), any(), any(), any(), any()))
                    .thenThrow(new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT", "競合"));

            UpdateWarehouseRequest request = new UpdateWarehouseRequest()
                    .warehouseName("名前")
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    // ========== toggleWarehouseActive ==========

    @Nested
    @DisplayName("PATCH /api/v1/master/warehouses/{id}/deactivate")
    class ToggleTests {

        @Test
        @DisplayName("無効化で200を返す")
        void toggle_deactivate_returns200() throws Exception {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            w.deactivate();
            when(warehouseService.toggleActive(eq(1L), eq(false), eq(0))).thenReturn(w);

            ToggleActiveRequest request = new ToggleActiveRequest()
                    .isActive(false)
                    .version(0);

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        @Test
        @DisplayName("有効化で200を返す")
        void toggle_activate_returns200() throws Exception {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseService.toggleActive(eq(1L), eq(true), eq(0))).thenReturn(w);

            ToggleActiveRequest request = new ToggleActiveRequest()
                    .isActive(true)
                    .version(0);

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(true));
        }
    }

    // ========== checkWarehouseCodeExists ==========

    @Nested
    @DisplayName("GET /api/v1/master/warehouses/exists")
    class ExistsTests {

        @Test
        @DisplayName("存在するコードでexists=trueを返す")
        void exists_true() throws Exception {
            when(warehouseService.existsByCode("WARA")).thenReturn(true);

            mockMvc.perform(get(BASE_URL + "/exists").param("warehouseCode", "WARA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("存在しないコードでexists=falseを返す")
        void exists_false() throws Exception {
            when(warehouseService.existsByCode("XXXX")).thenReturn(false);

            mockMvc.perform(get(BASE_URL + "/exists").param("warehouseCode", "XXXX"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }

        @Test
        @DisplayName("warehouseCode未指定で400を返す")
        void exists_missingParam_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL + "/exists"))
                    .andExpect(status().isBadRequest());
        }
    }

    // --- Helper ---

    private static Warehouse createWarehouseWithoutTimestamps(Long id, String code, String name) {
        Warehouse w = new Warehouse();
        w.setWarehouseCode(code);
        w.setWarehouseName(name);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(w, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return w;
    }

    private static Warehouse createWarehouse(Long id, String code, String name) {
        Warehouse w = new Warehouse();
        w.setWarehouseCode(code);
        w.setWarehouseName(name);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(w, id);
                var createdAtField = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(w, OffsetDateTime.now());
                var updatedAtField = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAtField.setAccessible(true);
                updatedAtField.set(w, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return w;
    }
}
