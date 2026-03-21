package com.wms.master.service;

import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.repository.AreaRepository;
import com.wms.master.repository.BuildingRepository;
import com.wms.master.repository.LocationRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AreaService")
class AreaServiceTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private AreaService areaService;

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("areaTypeフィルタを渡して検索できる")
        void search_withAreaType_returnsFiltered() {
            Area a = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            Page<Area> page = new PageImpl<>(List.of(a));
            Pageable pageable = PageRequest.of(0, 20);
            when(areaRepository.search(null, null, "STOCK", null, null, pageable)).thenReturn(page);

            Page<Area> result = areaService.search(null, null, "STOCK", null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAreaType()).isEqualTo("STOCK");
        }

        @Test
        @DisplayName("storageConditionフィルタを渡して検索できる")
        void search_withStorageCondition_returnsFiltered() {
            Area a = createArea(1L, 1L, 1L, "A01", "冷蔵エリア", "STOCK", "REFRIGERATED");
            Page<Area> page = new PageImpl<>(List.of(a));
            Pageable pageable = PageRequest.of(0, 20);
            when(areaRepository.search(null, null, null, "REFRIGERATED", null, pageable)).thenReturn(page);

            Page<Area> result = areaService.search(null, null, null, "REFRIGERATED", null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStorageCondition()).isEqualTo("REFRIGERATED");
        }

        @Test
        @DisplayName("全フィルタなしでページング結果を返す")
        void search_noFilter_returnsPagedResult() {
            Area a = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            Page<Area> page = new PageImpl<>(List.of(a));
            Pageable pageable = PageRequest.of(0, 20);
            when(areaRepository.search(null, null, null, null, null, pageable)).thenReturn(page);

            Page<Area> result = areaService.search(null, null, null, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("存在するIDでエリアを返す")
        void findById_exists_returnsArea() {
            Area a = createArea(1L, 1L, 1L, "A01", "テストエリア", "STOCK", "AMBIENT");
            when(areaRepository.findById(1L)).thenReturn(Optional.of(a));

            Area result = areaService.findById(1L);

            assertThat(result.getAreaCode()).isEqualTo("A01");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void findById_notExists_throwsException() {
            when(areaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> areaService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("エリア");
        }
    }

    @Nested
    @DisplayName("findByIds")
    class FindByIds {

        @Test
        @DisplayName("複数IDでMapを返す")
        void findByIds_returnsMap() {
            Area a1 = createArea(1L, 1L, 1L, "A01", "エリア1", "STOCK", "AMBIENT");
            Area a2 = createArea(2L, 1L, 1L, "A02", "エリア2", "RECEIVING", "REFRIGERATED");
            when(areaRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(a1, a2));

            Map<Long, Area> result = areaService.findByIds(List.of(1L, 2L));

            assertThat(result).hasSize(2);
            assertThat(result.get(1L).getAreaCode()).isEqualTo("A01");
            assertThat(result.get(2L).getAreaCode()).isEqualTo("A02");
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("棟から warehouseId が自動設定されてエリアを登録できる")
        void create_success_warehouseIdFromBuilding() {
            Building building = createBuilding(10L, 5L, "B01");
            Area area = createArea(null, null, 10L, "A01", "テストエリア", "STOCK", "AMBIENT");

            when(buildingRepository.findById(10L)).thenReturn(Optional.of(building));
            when(areaRepository.existsByBuildingIdAndAreaCode(10L, "A01")).thenReturn(false);
            when(areaRepository.save(area)).thenReturn(area);

            Area result = areaService.create(area);

            verify(buildingRepository).findById(10L);
            assertThat(area.getWarehouseId()).isEqualTo(5L);
            assertThat(result.getAreaCode()).isEqualTo("A01");
        }

        @Test
        @DisplayName("棟が存在しない場合はResourceNotFoundExceptionをスロー")
        void create_buildingNotFound_throwsException() {
            Area area = createArea(null, null, 99L, "A01", "テストエリア", "STOCK", "AMBIENT");
            when(buildingRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> areaService.create(area))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("棟");

            verify(areaRepository, never()).save(any());
        }

        @Test
        @DisplayName("重複コードでDuplicateResourceExceptionをスロー")
        void create_duplicateCode_throwsException() {
            Building building = createBuilding(10L, 5L, "B01");
            Area area = createArea(null, null, 10L, "A01", "テストエリア", "STOCK", "AMBIENT");

            when(buildingRepository.findById(10L)).thenReturn(Optional.of(building));
            when(areaRepository.existsByBuildingIdAndAreaCode(10L, "A01")).thenReturn(true);

            assertThatThrownBy(() -> areaService.create(area))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("A01");

            verify(areaRepository, never()).save(any());
        }

        @Test
        @DisplayName("TOCTOU競合時にDataIntegrityViolationExceptionをDuplicateResourceExceptionに変換")
        void create_toctouRace_throwsDuplicateResourceException() {
            Building building = createBuilding(10L, 5L, "B01");
            Area area = createArea(null, null, 10L, "A01", "テストエリア", "STOCK", "AMBIENT");

            when(buildingRepository.findById(10L)).thenReturn(Optional.of(building));
            when(areaRepository.existsByBuildingIdAndAreaCode(10L, "A01")).thenReturn(false);
            when(areaRepository.save(area)).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> areaService.create(area))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("A01");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("エリア情報を更新できる")
        void update_success() {
            Area existing = createArea(1L, 1L, 1L, "A01", "旧エリア名", "STOCK", "AMBIENT");
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(areaRepository.save(any(Area.class))).thenAnswer(inv -> inv.getArgument(0));

            Area result = areaService.update(1L, "新エリア名", "REFRIGERATED", 0);

            assertThat(result.getAreaName()).isEqualTo("新エリア名");
            assertThat(result.getStorageCondition()).isEqualTo("REFRIGERATED");
        }

        @Test
        @DisplayName("バージョン不一致で事前チェックによるOptimisticLockConflictExceptionをスロー")
        void update_versionMismatch_throwsException() {
            Area existing = createArea(1L, 1L, 1L, "A01", "エリア", "STOCK", "AMBIENT");
            // existing.version == 0, request version == 99
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> areaService.update(1L, "新エリア名", "AMBIENT", 99))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void update_optimisticLockConflict_throwsException() {
            Area existing = createArea(1L, 1L, 1L, "A01", "エリア", "STOCK", "AMBIENT");
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(areaRepository.save(any(Area.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Area.class.getName(), 1L));

            assertThatThrownBy(() -> areaService.update(1L, "新エリア名", "AMBIENT", 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void update_notFound_throwsException() {
            when(areaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> areaService.update(999L, "エリア名", "AMBIENT", 0))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {

        @Test
        @DisplayName("エリアを無効化できる")
        void toggleActive_deactivate_success() {
            Area existing = createArea(1L, 1L, 1L, "A01", "エリア", "STOCK", "AMBIENT");
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(locationRepository.countByAreaIdAndIsActiveTrue(1L)).thenReturn(0L);
            when(areaRepository.save(any(Area.class))).thenAnswer(inv -> inv.getArgument(0));

            Area result = areaService.toggleActive(1L, false, 0);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("エリアを有効化できる")
        void toggleActive_activate_success() {
            Area existing = createArea(1L, 1L, 1L, "A01", "エリア", "STOCK", "AMBIENT");
            existing.deactivate();
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(areaRepository.save(any(Area.class))).thenAnswer(inv -> inv.getArgument(0));

            Area result = areaService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("既に同じ状態の場合はUPDATEなし（冪等性）")
        void toggleActive_alreadySameState_noUpdate() {
            Area existing = createArea(1L, 1L, 1L, "A01", "エリア", "STOCK", "AMBIENT");
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));
            // isActive=true なのでロケーション存在チェックはスキップされる

            Area result = areaService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
            verify(areaRepository, never()).save(any());
        }

        @Test
        @DisplayName("配下ロケーションあり無効化不可でBusinessRuleViolationExceptionをスロー")
        void toggleActive_hasLocations_throwsException() {
            Area existing = createArea(1L, 1L, 1L, "A01", "エリア", "STOCK", "AMBIENT");
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(locationRepository.countByAreaIdAndIsActiveTrue(1L)).thenReturn(3L);

            assertThatThrownBy(() -> areaService.toggleActive(1L, false, 0))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ロケーション");

            verify(areaRepository, never()).save(any());
        }

        @Test
        @DisplayName("バージョン不一致で事前チェックによるOptimisticLockConflictExceptionをスロー")
        void toggleActive_versionMismatch_throwsException() {
            Area existing = createArea(1L, 1L, 1L, "A01", "エリア", "STOCK", "AMBIENT");
            // existing.version == 0, request version == 99
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> areaService.toggleActive(1L, false, 99))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void toggleActive_optimisticLockConflict_throwsException() {
            Area existing = createArea(1L, 1L, 1L, "A01", "エリア", "STOCK", "AMBIENT");
            when(areaRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(locationRepository.countByAreaIdAndIsActiveTrue(1L)).thenReturn(0L);
            when(areaRepository.save(any(Area.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Area.class.getName(), 1L));

            assertThatThrownBy(() -> areaService.toggleActive(1L, false, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    // --- Helpers ---

    private Area createArea(Long id, Long warehouseId, Long buildingId,
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
}
