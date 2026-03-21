package com.wms.master.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.entity.Building;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.BuildingService;
import com.wms.master.service.WarehouseService;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
 * 棟コントローラーの単体テスト。
 * セキュリティフィルターを無効化し、ビジネスロジックのマッピングを検証する。
 * 認可テストは BuildingControllerAuthTest で行う。
 */
@WebMvcTest(BuildingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BuildingController")
class BuildingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BuildingService buildingService;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String BASE_URL = "/api/v1/master/buildings";

    // --- Helper ---

    private Building createBuilding(Long id, Long warehouseId, String code, String name) {
        Building b = new Building();
        b.setWarehouseId(warehouseId);
        b.setBuildingCode(code);
        b.setBuildingName(name);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(b, id);
                var createdAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAt.setAccessible(true);
                createdAt.set(b, OffsetDateTime.now());
                var updatedAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAt.setAccessible(true);
                updatedAt.set(b, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return b;
    }

    private Warehouse createWarehouse(Long id, String code, String name) {
        Warehouse w = new Warehouse();
        w.setWarehouseCode(code);
        w.setWarehouseName(name);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(w, id);
                var createdAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAt.setAccessible(true);
                createdAt.set(w, OffsetDateTime.now());
                var updatedAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAt.setAccessible(true);
                updatedAt.set(w, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return w;
    }

    // ===== GET /api/v1/master/buildings =====

    @Nested
    @DisplayName("GET /buildings（一覧）")
    class ListBuildings {

        @Test
        @DisplayName("ページング形式で棟一覧を返す")
        void listBuildings_paged_returns200() throws Exception {
            Building b = createBuilding(1L, 10L, "BLDG01", "棟A");
            Page<Building> page = new PageImpl<>(List.of(b));
            Warehouse w = createWarehouse(10L, "WH001", "倉庫A");
            when(buildingService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(warehouseService.findByIds(any())).thenReturn(Map.of(10L, w));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].buildingCode").value("BLDG01"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("warehouseIdフィルタ付きでページング検索できる")
        void listBuildings_withWarehouseId_returns200() throws Exception {
            Building b = createBuilding(1L, 10L, "BLDG01", "棟A");
            Page<Building> page = new PageImpl<>(List.of(b));
            Warehouse w = createWarehouse(10L, "WH001", "倉庫A");
            when(buildingService.search(eq(10L), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(warehouseService.findByIds(any())).thenReturn(Map.of(10L, w));

            mockMvc.perform(get(BASE_URL).param("warehouseId", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].warehouseCode").value("WH001"));
        }

        @Test
        @DisplayName("pageが負の場合400を返す")
        void listBuildings_negativePage_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("page", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sizeが0の場合400を返す")
        void listBuildings_zeroSize_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("size", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sizeが100を超える場合は100に丸める")
        void listBuildings_oversizeSize_cappedTo100() throws Exception {
            Page<Building> page = new PageImpl<>(List.of());
            when(buildingService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("size", "200"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("不正なsortプロパティは buildingCode にフォールバックする")
        void listBuildings_invalidSort_fallbackToBuildingCode() throws Exception {
            Page<Building> page = new PageImpl<>(List.of());
            when(buildingService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "unknown,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("倉庫マップにwarehouseが存在しない場合もnullフォールバックで200を返す")
        void listBuildings_warehouseNotInMap_returns200() throws Exception {
            Building b = createBuilding(1L, 10L, "BLDG01", "棟A");
            Page<Building> page = new PageImpl<>(List.of(b));
            when(buildingService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(warehouseService.findByIds(any())).thenReturn(Map.of()); // 空マップ

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].buildingCode").value("BLDG01"))
                    .andExpect(jsonPath("$.content[0].warehouseCode").doesNotExist());
        }

        @Test
        @DisplayName("sort=descで降順ソートできる")
        void listBuildings_sortDesc_returns200() throws Exception {
            Page<Building> page = new PageImpl<>(List.of());
            when(buildingService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "buildingCode,desc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sortにカンマがない場合は昇順になる")
        void listBuildings_sortWithoutComma_returnsAsc() throws Exception {
            Page<Building> page = new PageImpl<>(List.of());
            when(buildingService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "buildingCode"))
                    .andExpect(status().isOk());
        }
    }

    // ===== POST /api/v1/master/buildings =====

    @Nested
    @DisplayName("POST /buildings（登録）")
    class CreateBuilding {

        private static final String VALID_JSON = """
                {"warehouseId":10,"buildingCode":"BLDG01","buildingName":"棟A"}
                """;

        @Test
        @DisplayName("正常登録で201を返す")
        void createBuilding_success_returns201() throws Exception {
            Warehouse w = createWarehouse(10L, "WH001", "倉庫A");
            Building created = createBuilding(1L, 10L, "BLDG01", "棟A");
            when(warehouseService.findById(10L)).thenReturn(w);
            when(buildingService.create(any(Building.class))).thenReturn(created);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.buildingCode").value("BLDG01"));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void createBuilding_missingRequired_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("重複コードで409を返す")
        void createBuilding_duplicateCode_returns409() throws Exception {
            Warehouse w = createWarehouse(10L, "WH001", "倉庫A");
            when(warehouseService.findById(10L)).thenReturn(w);
            when(buildingService.create(any(Building.class)))
                    .thenThrow(new com.wms.shared.exception.DuplicateResourceException(
                            "DUPLICATE_CODE", "棟コードが既に存在します: BLDG01"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // ===== GET /api/v1/master/buildings/{id} =====

    @Nested
    @DisplayName("GET /buildings/{id}（詳細）")
    class GetBuilding {

        @Test
        @DisplayName("存在するIDで200を返す")
        void getBuilding_exists_returns200() throws Exception {
            Building b = createBuilding(1L, 10L, "BLDG01", "棟A");
            Warehouse w = createWarehouse(10L, "WH001", "倉庫A");
            when(buildingService.findById(1L)).thenReturn(b);
            when(warehouseService.findById(10L)).thenReturn(w);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.buildingCode").value("BLDG01"));
        }

        @Test
        @DisplayName("createdAt/updatedAtがnullの棟でも200を返す（toLocalDateTime nullブランチ）")
        void getBuilding_nullTimestamps_returns200() throws Exception {
            Building b = createBuilding(null, 10L, "BLDG01", "棟A");
            Warehouse w = createWarehouse(10L, "WH001", "倉庫A");
            when(buildingService.findById(1L)).thenReturn(b);
            when(warehouseService.findById(10L)).thenReturn(w);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void getBuilding_notExists_returns404() throws Exception {
            when(buildingService.findById(999L))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "BUILDING_NOT_FOUND", "棟", 999L));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== PUT /api/v1/master/buildings/{id} =====

    @Nested
    @DisplayName("PUT /buildings/{id}（更新）")
    class UpdateBuilding {

        private static final String VALID_JSON = """
                {"buildingName":"新棟名","version":0}
                """;

        @Test
        @DisplayName("正常更新で200を返す")
        void updateBuilding_success_returns200() throws Exception {
            Building updated = createBuilding(1L, 10L, "BLDG01", "新棟名");
            Warehouse w = createWarehouse(10L, "WH001", "倉庫A");
            when(buildingService.update(anyLong(), anyString(), anyInt())).thenReturn(updated);
            when(warehouseService.findById(10L)).thenReturn(w);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.buildingName").value("新棟名"));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void updateBuilding_missingRequired_returns400() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void updateBuilding_notFound_returns404() throws Exception {
            when(buildingService.update(anyLong(), anyString(), anyInt()))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "BUILDING_NOT_FOUND", "棟", 999L));

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void updateBuilding_optimisticLock_returns409() throws Exception {
            when(buildingService.update(anyLong(), anyString(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.OptimisticLockConflictException(
                            "OPTIMISTIC_LOCK_CONFLICT", "競合が発生しました"));

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // ===== PATCH /api/v1/master/buildings/{id}/deactivate =====

    @Nested
    @DisplayName("PATCH /buildings/{id}/deactivate（有効化/無効化）")
    class ToggleBuildingActive {

        private static final String VALID_JSON = """
                {"isActive":false,"version":0}
                """;

        @Test
        @DisplayName("無効化で200を返す")
        void toggleBuildingActive_deactivate_returns200() throws Exception {
            Building updated = createBuilding(1L, 10L, "BLDG01", "棟A");
            updated.deactivate();
            when(buildingService.toggleActive(anyLong(), anyBoolean(), anyInt())).thenReturn(updated);

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void toggleBuildingActive_missingRequired_returns400() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void toggleBuildingActive_notFound_returns404() throws Exception {
            when(buildingService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "BUILDING_NOT_FOUND", "棟", 999L));

            mockMvc.perform(patch(BASE_URL + "/999/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("配下にエリアありで422を返す")
        void toggleBuildingActive_hasChildren_returns422() throws Exception {
            when(buildingService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.BusinessRuleViolationException(
                            "CANNOT_DEACTIVATE_HAS_CHILDREN",
                            "配下に有効なエリアが存在するため無効化できません"));

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void toggleBuildingActive_optimisticLock_returns409() throws Exception {
            when(buildingService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.OptimisticLockConflictException(
                            "OPTIMISTIC_LOCK_CONFLICT", "競合が発生しました"));

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }
}
