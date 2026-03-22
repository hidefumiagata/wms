package com.wms.master.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * エリアコントローラーの単体テスト。
 * セキュリティフィルターを無効化し、ビジネスロジックのマッピングを検証する。
 * 認可テストは AreaControllerAuthTest で行う。
 */
@WebMvcTest(AreaController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AreaController")
class AreaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AreaService areaService;

    @MockitoBean
    private BuildingService buildingService;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String BASE_URL = "/api/v1/master/areas";

    // --- Helpers ---

    private Area createArea(Long id, Long buildingId, Long warehouseId,
                             String code, String name, String areaType, String storageCondition) {
        Area a = new Area();
        a.setBuildingId(buildingId);
        a.setWarehouseId(warehouseId);
        a.setAreaCode(code);
        a.setAreaName(name);
        a.setAreaType(areaType);
        a.setStorageCondition(storageCondition);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(a, id);
                var createdAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAt.setAccessible(true);
                createdAt.set(a, OffsetDateTime.now());
                var updatedAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAt.setAccessible(true);
                updatedAt.set(a, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return a;
    }

    private Building createBuilding(Long id, Long warehouseId, String code) {
        Building b = new Building();
        b.setWarehouseId(warehouseId);
        b.setBuildingCode(code);
        b.setBuildingName("棟" + code);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(b, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return b;
    }

    private Warehouse createWarehouse(Long id, String code) {
        Warehouse w = new Warehouse();
        w.setWarehouseCode(code);
        w.setWarehouseName("倉庫" + code);
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

    // ===== GET /api/v1/master/areas =====

    @Nested
    @DisplayName("GET /areas（一覧）")
    class ListAreas {

        @Test
        @DisplayName("ページング形式でエリア一覧を返す")
        void listAreas_paged_returns200() throws Exception {
            Area a = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            Page<Area> page = new PageImpl<>(List.of(a));
            Building building = createBuilding(1L, 1L, "B01");
            Warehouse warehouse = createWarehouse(1L, "WH-001");

            when(areaService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(buildingService.findByIds(Set.of(1L))).thenReturn(Map.of(1L, building));
            when(warehouseService.findByIds(Set.of(1L))).thenReturn(Map.of(1L, warehouse));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].areaCode").value("A01"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("areaTypeフィルタ付きでページング検索できる")
        void listAreas_withAreaType_returns200() throws Exception {
            Area a = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            Page<Area> page = new PageImpl<>(List.of(a));
            Building building = createBuilding(1L, 1L, "B01");
            Warehouse warehouse = createWarehouse(1L, "WH-001");

            when(areaService.search(isNull(), isNull(), eq("STOCK"), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(buildingService.findByIds(any())).thenReturn(Map.of(1L, building));
            when(warehouseService.findByIds(any())).thenReturn(Map.of(1L, warehouse));

            mockMvc.perform(get(BASE_URL).param("areaType", "STOCK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].areaType").value("STOCK"));
        }

        @Test
        @DisplayName("storageConditionフィルタ付きでページング検索できる")
        void listAreas_withStorageCondition_returns200() throws Exception {
            Area a = createArea(1L, 1L, 1L, "A01", "冷蔵エリア", "STOCK", "REFRIGERATED");
            Page<Area> page = new PageImpl<>(List.of(a));
            Building building = createBuilding(1L, 1L, "B01");
            Warehouse warehouse = createWarehouse(1L, "WH-001");

            when(areaService.search(isNull(), isNull(), isNull(), eq("REFRIGERATED"), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(buildingService.findByIds(any())).thenReturn(Map.of(1L, building));
            when(warehouseService.findByIds(any())).thenReturn(Map.of(1L, warehouse));

            mockMvc.perform(get(BASE_URL).param("storageCondition", "REFRIGERATED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].storageCondition").value("REFRIGERATED"));
        }

        @Test
        @DisplayName("棟・倉庫マップにエントリが存在しない場合もnullフォールバックで200を返す")
        void listAreas_buildingAndWarehouseNotInMap_returns200() throws Exception {
            Area a = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            Page<Area> page = new PageImpl<>(List.of(a));

            when(areaService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(buildingService.findByIds(any())).thenReturn(Map.of()); // 空マップ
            when(warehouseService.findByIds(any())).thenReturn(Map.of()); // 空マップ

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].areaCode").value("A01"))
                    .andExpect(jsonPath("$.content[0].warehouseCode").doesNotExist())
                    .andExpect(jsonPath("$.content[0].buildingCode").doesNotExist());
        }

        @Test
        @DisplayName("pageが負の場合400を返す")
        void listAreas_negativePage_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("page", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sizeが0の場合400を返す")
        void listAreas_zeroSize_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("size", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sizeが100を超える場合は400を返す（Bean Validation @Max(100)）")
        void listAreas_oversizeSize_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("size", "101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("不正なsortプロパティは areaCode にフォールバックする")
        void listAreas_invalidSort_fallbackToAreaCode() throws Exception {
            Page<Area> page = new PageImpl<>(List.of());
            when(areaService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(buildingService.findByIds(any())).thenReturn(Map.of());
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "unknown,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sortにカンマがない場合は昇順になる")
        void listAreas_sortWithoutComma_returnsAsc() throws Exception {
            Page<Area> page = new PageImpl<>(List.of());
            when(areaService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(buildingService.findByIds(any())).thenReturn(Map.of());
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "areaCode"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sort=desc で降順ソートできる")
        void listAreas_sortDesc_returns200() throws Exception {
            Page<Area> page = new PageImpl<>(List.of());
            when(areaService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(buildingService.findByIds(any())).thenReturn(Map.of());
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "areaCode,desc"))
                    .andExpect(status().isOk());
        }
    }

    // ===== POST /api/v1/master/areas =====

    @Nested
    @DisplayName("POST /areas（登録）")
    class CreateArea {

        private static final String VALID_JSON = """
                {"buildingId":1,"areaCode":"A01","areaName":"テストエリア",
                "storageCondition":"AMBIENT","areaType":"STOCK"}
                """;

        @Test
        @DisplayName("正常登録で201+Locationヘッダーを返す")
        void createArea_success_returns201() throws Exception {
            Area created = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            Building building = createBuilding(1L, 1L, "B01");
            Warehouse warehouse = createWarehouse(1L, "WH-001");

            when(areaService.create(any(Area.class))).thenReturn(created);
            when(buildingService.findById(1L)).thenReturn(building);
            when(warehouseService.findById(1L)).thenReturn(warehouse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/master/areas/1"))
                    .andExpect(jsonPath("$.areaCode").value("A01"));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void createArea_missingRequired_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("重複コードで409を返す")
        void createArea_duplicateCode_returns409() throws Exception {
            when(areaService.create(any(Area.class)))
                    .thenThrow(new com.wms.shared.exception.DuplicateResourceException(
                            "DUPLICATE_CODE", "エリアコードが既に存在します: A01"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("棟が存在しない場合は404を返す")
        void createArea_buildingNotFound_returns404() throws Exception {
            when(areaService.create(any(Area.class)))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "BUILDING_NOT_FOUND", "棟", 1L));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== GET /api/v1/master/areas/{id} =====

    @Nested
    @DisplayName("GET /areas/{id}（詳細）")
    class GetArea {

        @Test
        @DisplayName("存在するIDで200を返す")
        void getArea_exists_returns200() throws Exception {
            Area a = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            Building building = createBuilding(1L, 1L, "B01");
            Warehouse warehouse = createWarehouse(1L, "WH-001");

            when(areaService.findById(1L)).thenReturn(a);
            when(buildingService.findById(1L)).thenReturn(building);
            when(warehouseService.findById(1L)).thenReturn(warehouse);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.areaCode").value("A01"));
        }

        @Test
        @DisplayName("createdAt/updatedAtがnullのエリアでも200を返す（toLocalDateTime nullブランチ）")
        void getArea_nullTimestamps_returns200() throws Exception {
            Area a = createArea(null, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            Building building = createBuilding(1L, 1L, "B01");
            Warehouse warehouse = createWarehouse(1L, "WH-001");

            when(areaService.findById(1L)).thenReturn(a);
            when(buildingService.findById(1L)).thenReturn(building);
            when(warehouseService.findById(1L)).thenReturn(warehouse);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void getArea_notExists_returns404() throws Exception {
            when(areaService.findById(999L))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "AREA_NOT_FOUND", "エリア", 999L));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== PUT /api/v1/master/areas/{id} =====

    @Nested
    @DisplayName("PUT /areas/{id}（更新）")
    class UpdateArea {

        private static final String VALID_JSON = """
                {"areaName":"テストエリア","storageCondition":"AMBIENT","version":0}
                """;

        @Test
        @DisplayName("正常更新で200とAreaUpdateResponseを返す")
        void updateArea_success_returns200() throws Exception {
            Area updated = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");

            when(areaService.update(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(updated);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.areaCode").value("A01"));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void updateArea_missingRequired_returns400() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void updateArea_notFound_returns404() throws Exception {
            when(areaService.update(anyLong(), anyString(), anyString(), anyInt()))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "AREA_NOT_FOUND", "エリア", 999L));

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void updateArea_optimisticLock_returns409() throws Exception {
            when(areaService.update(anyLong(), anyString(), anyString(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.OptimisticLockConflictException(
                            "OPTIMISTIC_LOCK_CONFLICT", "競合が発生しました"));

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // ===== PATCH /api/v1/master/areas/{id}/deactivate =====

    @Nested
    @DisplayName("PATCH /areas/{id}/deactivate（有効化/無効化）")
    class ToggleAreaActive {

        private static final String VALID_JSON = """
                {"isActive":false,"version":0}
                """;

        @Test
        @DisplayName("無効化で200を返す")
        void toggleAreaActive_deactivate_returns200() throws Exception {
            Area updated = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            updated.deactivate();

            when(areaService.toggleActive(anyLong(), anyBoolean(), anyInt())).thenReturn(updated);

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void toggleAreaActive_missingRequired_returns400() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void toggleAreaActive_notFound_returns404() throws Exception {
            when(areaService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "AREA_NOT_FOUND", "エリア", 999L));

            mockMvc.perform(patch(BASE_URL + "/999/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("配下ロケーションあり無効化不可で422を返す")
        void toggleAreaActive_hasLocations_returns422() throws Exception {
            when(areaService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.BusinessRuleViolationException(
                            "CANNOT_DEACTIVATE_HAS_CHILDREN",
                            "配下にロケーションが存在するため無効化できません (id=1)"));

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void toggleAreaActive_optimisticLock_returns409() throws Exception {
            when(areaService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.OptimisticLockConflictException(
                            "OPTIMISTIC_LOCK_CONFLICT", "競合が発生しました"));

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }
}
