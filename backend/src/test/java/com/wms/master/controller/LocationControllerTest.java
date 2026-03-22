package com.wms.master.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.entity.Location;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
import com.wms.master.service.BuildingService;
import com.wms.master.service.LocationService;
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
 * ロケーションコントローラーの単体テスト。
 * セキュリティフィルターを無効化し、ビジネスロジックのマッピングを検証する。
 * 認可テストは LocationControllerAuthTest で行う。
 */
@WebMvcTest(LocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("LocationController")
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LocationService locationService;

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

    private static final String BASE_URL = "/api/v1/master/locations";

    // --- Helpers ---

    private Location createLocation(Long id, Long areaId, Long warehouseId, String code) {
        Location l = new Location();
        l.setAreaId(areaId);
        l.setWarehouseId(warehouseId);
        l.setLocationCode(code);
        l.setIsStocktakingLocked(false);
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(l, id);
                var createdAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAt.setAccessible(true);
                createdAt.set(l, OffsetDateTime.now());
                var updatedAt = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAt.setAccessible(true);
                updatedAt.set(l, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return l;
    }

    private Area createArea(Long id, String code, String areaType) {
        Area a = new Area();
        a.setAreaCode(code);
        a.setAreaType(areaType);
        a.setWarehouseId(100L);
        a.setBuildingId(1L);
        a.setAreaName("テストエリア");
        a.setStorageCondition("AMBIENT");
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(a, id);
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
        w.setWarehouseName("テスト倉庫");
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

    // ===== GET /api/v1/master/locations =====

    @Nested
    @DisplayName("GET /locations（一覧）")
    class ListLocations {

        @Test
        @DisplayName("ページング形式でロケーション一覧を返す")
        void listLocations_paged_returns200() throws Exception {
            Location l = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            Page<Location> page = new PageImpl<>(List.of(l));
            Area area = createArea(10L, "AREA-01", "STOCK");
            Warehouse warehouse = createWarehouse(100L, "WH-001");

            when(locationService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(areaService.findByIds(any())).thenReturn(Map.of(10L, area));
            when(warehouseService.findByIds(any())).thenReturn(Map.of(100L, warehouse));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].locationCode").value("A-01-A-01-01-01"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("warehouseId/areaId/codePrefixフィルタ付きで検索できる")
        void listLocations_withFilters_returns200() throws Exception {
            Location l = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            Page<Location> page = new PageImpl<>(List.of(l));
            Area area = createArea(10L, "AREA-01", "STOCK");
            Warehouse warehouse = createWarehouse(100L, "WH-001");

            when(locationService.search(eq(100L), eq(10L), eq("A-01"), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(areaService.findByIds(any())).thenReturn(Map.of(10L, area));
            when(warehouseService.findByIds(any())).thenReturn(Map.of(100L, warehouse));

            mockMvc.perform(get(BASE_URL)
                            .param("warehouseId", "100")
                            .param("areaId", "10")
                            .param("codePrefix", "A-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].locationCode").value("A-01-A-01-01-01"));
        }

        @Test
        @DisplayName("エリア・倉庫マップにエントリが存在しない場合もnullフォールバックで200を返す")
        void listLocations_areaAndWarehouseNotInMap_returns200() throws Exception {
            Location l = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            Page<Location> page = new PageImpl<>(List.of(l));

            when(locationService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(areaService.findByIds(any())).thenReturn(Map.of()); // 空マップ
            when(warehouseService.findByIds(any())).thenReturn(Map.of()); // 空マップ

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].locationCode").value("A-01-A-01-01-01"))
                    .andExpect(jsonPath("$.content[0].warehouseCode").doesNotExist())
                    .andExpect(jsonPath("$.content[0].areaCode").doesNotExist());
        }

        @Test
        @DisplayName("pageが負の場合400を返す")
        void listLocations_negativePage_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("page", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sizeが0の場合400を返す")
        void listLocations_zeroSize_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("size", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sizeが100を超える場合は400を返す（Bean Validation @Max(100)）")
        void listLocations_oversizeSize_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("size", "101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("不正なsortプロパティはlocationCodeにフォールバックする")
        void listLocations_invalidSort_fallbackToLocationCode() throws Exception {
            Page<Location> page = new PageImpl<>(List.of());
            when(locationService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(areaService.findByIds(any())).thenReturn(Map.of());
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "unknown,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sortにカンマがない場合は昇順になる")
        void listLocations_sortWithoutComma_returnsAsc() throws Exception {
            Page<Location> page = new PageImpl<>(List.of());
            when(locationService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(areaService.findByIds(any())).thenReturn(Map.of());
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "locationCode"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sort=descで降順ソートできる")
        void listLocations_sortDesc_returns200() throws Exception {
            Page<Location> page = new PageImpl<>(List.of());
            when(locationService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);
            when(areaService.findByIds(any())).thenReturn(Map.of());
            when(warehouseService.findByIds(any())).thenReturn(Map.of());

            mockMvc.perform(get(BASE_URL).param("sort", "locationCode,desc"))
                    .andExpect(status().isOk());
        }
    }

    // ===== GET /api/v1/master/locations/count =====

    @Nested
    @DisplayName("GET /locations/count（件数）")
    class CountLocations {

        @Test
        @DisplayName("isActive省略時はデフォルトtrueで件数を返す")
        void countLocations_defaultIsActiveTrue_returns200() throws Exception {
            when(locationService.count(isNull(), isNull(), isNull(), eq(true))).thenReturn(42L);

            mockMvc.perform(get(BASE_URL + "/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(42));
        }

        @Test
        @DisplayName("isActive=falseを明示的に指定して件数を返す")
        void countLocations_isActiveFalse_returns200() throws Exception {
            when(locationService.count(isNull(), isNull(), isNull(), eq(false))).thenReturn(5L);

            mockMvc.perform(get(BASE_URL + "/count").param("isActive", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));
        }

        @Test
        @DisplayName("buildingIdフィルタで件数を返す")
        void countLocations_withBuildingId_returns200() throws Exception {
            when(locationService.count(isNull(), eq(1L), isNull(), eq(true))).thenReturn(10L);

            mockMvc.perform(get(BASE_URL + "/count").param("buildingId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(10));
        }
    }

    // ===== POST /api/v1/master/locations =====

    @Nested
    @DisplayName("POST /locations（登録）")
    class CreateLocation {

        private static final String VALID_JSON = """
                {"areaId":1,"locationCode":"A-01-A-01-01-01","locationName":"棚A"}
                """;

        @Test
        @DisplayName("正常登録で201を返す")
        void createLocation_success_returns201() throws Exception {
            Location created = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            created.setLocationName("棚A");
            Area area = createArea(10L, "AREA-01", "STOCK");
            Warehouse warehouse = createWarehouse(100L, "WH-001");

            when(locationService.create(any(Location.class))).thenReturn(created);
            when(areaService.findById(10L)).thenReturn(area);
            when(warehouseService.findById(100L)).thenReturn(warehouse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.locationCode").value("A-01-A-01-01-01"));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void createLocation_missingRequired_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("重複コードで409を返す")
        void createLocation_duplicateCode_returns409() throws Exception {
            when(locationService.create(any(Location.class)))
                    .thenThrow(new com.wms.shared.exception.DuplicateResourceException(
                            "DUPLICATE_CODE", "ロケーションコードが既に存在します: A-01-A-01-01-01"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("エリア1件制限超過で422を返す")
        void createLocation_areaLimitExceeded_returns422() throws Exception {
            when(locationService.create(any(Location.class)))
                    .thenThrow(new com.wms.shared.exception.BusinessRuleViolationException(
                            "AREA_LOCATION_LIMIT_EXCEEDED", "INBOUNDエリアにはロケーションを1件のみ登録できます"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ===== GET /api/v1/master/locations/{id} =====

    @Nested
    @DisplayName("GET /locations/{id}（詳細）")
    class GetLocation {

        @Test
        @DisplayName("存在するIDで200を返す")
        void getLocation_exists_returns200() throws Exception {
            Location l = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            Area area = createArea(10L, "AREA-01", "STOCK");
            Building building = createBuilding(1L, 100L, "B01");
            Warehouse warehouse = createWarehouse(100L, "WH-001");

            when(locationService.findById(1L)).thenReturn(l);
            when(areaService.findById(10L)).thenReturn(area);
            when(buildingService.findById(1L)).thenReturn(building);
            when(warehouseService.findById(100L)).thenReturn(warehouse);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locationCode").value("A-01-A-01-01-01"));
        }

        @Test
        @DisplayName("createdAt/updatedAtがnullのロケーションでも200を返す")
        void getLocation_nullTimestamps_returns200() throws Exception {
            Location l = createLocation(null, 10L, 100L, "A-01-A-01-01-01");
            Area area = createArea(10L, "AREA-01", "STOCK");
            Building building = createBuilding(1L, 100L, "B01");
            Warehouse warehouse = createWarehouse(100L, "WH-001");

            when(locationService.findById(1L)).thenReturn(l);
            when(areaService.findById(10L)).thenReturn(area);
            when(buildingService.findById(1L)).thenReturn(building);
            when(warehouseService.findById(100L)).thenReturn(warehouse);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("locationNameがnullの場合も200を返す（optional field）")
        void getLocation_nullLocationName_returns200() throws Exception {
            Location l = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            // locationName は設定しない（null）
            Area area = createArea(10L, "AREA-01", "STOCK");
            Building building = createBuilding(1L, 100L, "B01");
            Warehouse warehouse = createWarehouse(100L, "WH-001");

            when(locationService.findById(1L)).thenReturn(l);
            when(areaService.findById(10L)).thenReturn(area);
            when(buildingService.findById(1L)).thenReturn(building);
            when(warehouseService.findById(100L)).thenReturn(warehouse);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locationName").doesNotExist());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void getLocation_notExists_returns404() throws Exception {
            when(locationService.findById(999L))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "LOCATION_NOT_FOUND", "ロケーション", 999L));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== PUT /api/v1/master/locations/{id} =====

    @Nested
    @DisplayName("PUT /locations/{id}（更新）")
    class UpdateLocation {

        private static final String VALID_JSON = """
                {"locationName":"棚A","version":0}
                """;

        @Test
        @DisplayName("正常更新で200を返す（LocationUpdateResponse）")
        void updateLocation_success_returns200() throws Exception {
            Location updated = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            updated.setLocationName("棚A");
            when(locationService.update(anyLong(), anyString(), anyInt())).thenReturn(updated);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locationCode").value("A-01-A-01-01-01"));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void updateLocation_missingRequired_returns400() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void updateLocation_notFound_returns404() throws Exception {
            when(locationService.update(anyLong(), anyString(), anyInt()))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "LOCATION_NOT_FOUND", "ロケーション", 999L));

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void updateLocation_optimisticLock_returns409() throws Exception {
            when(locationService.update(anyLong(), anyString(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.OptimisticLockConflictException(
                            "OPTIMISTIC_LOCK_CONFLICT", "競合が発生しました"));

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // ===== PATCH /api/v1/master/locations/{id}/toggle-active =====

    @Nested
    @DisplayName("PATCH /locations/{id}/toggle-active（有効化/無効化）")
    class ToggleLocationActive {

        private static final String VALID_JSON = """
                {"isActive":false,"version":0}
                """;

        @Test
        @DisplayName("無効化で200を返す")
        void toggleLocationActive_deactivate_returns200() throws Exception {
            Location updated = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            updated.deactivate();
            when(locationService.toggleActive(anyLong(), anyBoolean(), anyInt())).thenReturn(updated);

            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void toggleLocationActive_missingRequired_returns400() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void toggleLocationActive_notFound_returns404() throws Exception {
            when(locationService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(com.wms.shared.exception.ResourceNotFoundException.of(
                            "LOCATION_NOT_FOUND", "ロケーション", 999L));

            mockMvc.perform(patch(BASE_URL + "/999/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void toggleLocationActive_optimisticLock_returns409() throws Exception {
            when(locationService.toggleActive(anyLong(), anyBoolean(), anyInt()))
                    .thenThrow(new com.wms.shared.exception.OptimisticLockConflictException(
                            "OPTIMISTIC_LOCK_CONFLICT", "競合が発生しました"));

            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict());
        }
    }
}
