package com.wms.master.service;

import com.wms.master.entity.Building;
import com.wms.master.repository.AreaRepository;
import com.wms.master.repository.BuildingRepository;
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
@DisplayName("BuildingService")
class BuildingServiceTest {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private AreaRepository areaRepository;

    @InjectMocks
    private BuildingService buildingService;

    @Nested
    @DisplayName("search")
    class Search {
        @Test
        @DisplayName("検索条件でページング結果を返す")
        void search_returnsPagedResult() {
            Building b = createBuilding(1L, 10L, "BLDG01", "棟A");
            Page<Building> page = new PageImpl<>(List.of(b));
            Pageable pageable = PageRequest.of(0, 20);
            when(buildingRepository.search(10L, "BLDG01", null, null, pageable)).thenReturn(page);

            Page<Building> result = buildingService.search(10L, "BLDG01", null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getBuildingCode()).isEqualTo("BLDG01");
        }

        @Test
        @DisplayName("warehouseIdフィルタなしで検索できる")
        void search_withoutWarehouseId_returnsResult() {
            Building b = createBuilding(1L, 10L, "BLDG01", "棟A");
            Page<Building> page = new PageImpl<>(List.of(b));
            Pageable pageable = PageRequest.of(0, 20);
            when(buildingRepository.search(null, null, null, true, pageable)).thenReturn(page);

            Page<Building> result = buildingService.search(null, null, null, true, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {
        @Test
        @DisplayName("存在するIDで棟を返す")
        void findById_exists_returnsBuilding() {
            Building b = createBuilding(1L, 10L, "BLDG01", "棟A");
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(b));

            Building result = buildingService.findById(1L);

            assertThat(result.getBuildingCode()).isEqualTo("BLDG01");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void findById_notExists_throwsException() {
            when(buildingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> buildingService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("棟");
        }
    }

    @Nested
    @DisplayName("findByIds")
    class FindByIds {
        @Test
        @DisplayName("複数IDでMapを返す")
        void findByIds_returnsMap() {
            Building b1 = createBuilding(1L, 10L, "BLDG01", "棟A");
            Building b2 = createBuilding(2L, 10L, "BLDG02", "棟B");
            when(buildingRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(b1, b2));

            Map<Long, Building> result = buildingService.findByIds(List.of(1L, 2L));

            assertThat(result).hasSize(2);
            assertThat(result.get(1L).getBuildingCode()).isEqualTo("BLDG01");
            assertThat(result.get(2L).getBuildingCode()).isEqualTo("BLDG02");
        }

        @Test
        @DisplayName("空リストで空Mapを返す")
        void findByIds_empty_returnsEmptyMap() {
            when(buildingRepository.findAllById(List.of())).thenReturn(List.of());

            Map<Long, Building> result = buildingService.findByIds(List.of());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("新規棟を登録できる")
        void create_success() {
            Building b = createBuilding(null, 10L, "BLDG01", "棟A");
            when(buildingRepository.existsByWarehouseIdAndBuildingCode(10L, "BLDG01")).thenReturn(false);
            when(buildingRepository.save(b)).thenReturn(b);

            Building result = buildingService.create(b);

            assertThat(result.getBuildingCode()).isEqualTo("BLDG01");
            verify(buildingRepository).save(b);
        }

        @Test
        @DisplayName("重複コードでDuplicateResourceExceptionをスロー")
        void create_duplicateCode_throwsException() {
            Building b = createBuilding(null, 10L, "BLDG01", "棟A");
            when(buildingRepository.existsByWarehouseIdAndBuildingCode(10L, "BLDG01")).thenReturn(true);

            assertThatThrownBy(() -> buildingService.create(b))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("BLDG01");
        }

        @Test
        @DisplayName("TOCTOU競合時にDataIntegrityViolationExceptionをDuplicateResourceExceptionに変換")
        void create_toctouRace_throwsDuplicateResourceException() {
            Building b = createBuilding(null, 10L, "BLDG01", "棟A");
            when(buildingRepository.existsByWarehouseIdAndBuildingCode(10L, "BLDG01")).thenReturn(false);
            when(buildingRepository.save(b)).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> buildingService.create(b))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("BLDG01");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {
        @Test
        @DisplayName("棟情報を更新できる")
        void update_success() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "旧棟名");
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(buildingRepository.save(any(Building.class))).thenAnswer(inv -> inv.getArgument(0));

            Building result = buildingService.update(1L, "新棟名", 0);

            assertThat(result.getBuildingName()).isEqualTo("新棟名");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void update_notFound_throwsException() {
            when(buildingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> buildingService.update(999L, "新棟名", 0))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("バージョン不一致で事前チェックによるOptimisticLockConflictExceptionをスロー")
        void update_versionMismatch_throwsException() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "棟A");
            // existing.version == 0, request version == 99
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> buildingService.update(1L, "棟A", 99))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void update_optimisticLockConflict_throwsException() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "棟A");
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(buildingRepository.save(any(Building.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Building.class.getName(), 1L));

            assertThatThrownBy(() -> buildingService.update(1L, "棟A", 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {
        @Test
        @DisplayName("棟を無効化できる")
        void toggleActive_deactivate_success() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "棟A");
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(areaRepository.countByBuildingIdAndIsActiveTrue(1L)).thenReturn(0L);
            when(buildingRepository.save(any(Building.class))).thenAnswer(inv -> inv.getArgument(0));

            Building result = buildingService.toggleActive(1L, false, 0);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("棟を有効化できる")
        void toggleActive_activate_success() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "棟A");
            existing.deactivate();
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(buildingRepository.save(any(Building.class))).thenAnswer(inv -> inv.getArgument(0));

            Building result = buildingService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("既に同じ状態の場合はUPDATEなし（冪等性）")
        void toggleActive_alreadySameState_noUpdate() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "棟A");
            // isActive=true で true を要求 → 子チェックをスキップして no-op
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));

            Building result = buildingService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
            verify(buildingRepository, never()).save(any());
        }

        @Test
        @DisplayName("配下に有効なエリアがある場合は無効化不可(422)")
        void toggleActive_hasActiveAreas_throwsBusinessRuleViolationException() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "棟A");
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(areaRepository.countByBuildingIdAndIsActiveTrue(1L)).thenReturn(3L);

            assertThatThrownBy(() -> buildingService.toggleActive(1L, false, 0))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("エリア");
        }

        @Test
        @DisplayName("バージョン不一致で事前チェックによるOptimisticLockConflictExceptionをスロー")
        void toggleActive_versionMismatch_throwsException() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "棟A");
            // existing.version == 0, request version == 99
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> buildingService.toggleActive(1L, false, 99))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void toggleActive_optimisticLockConflict_throwsException() {
            Building existing = createBuilding(1L, 10L, "BLDG01", "棟A");
            when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(areaRepository.countByBuildingIdAndIsActiveTrue(1L)).thenReturn(0L);
            when(buildingRepository.save(any(Building.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Building.class.getName(), 1L));

            assertThatThrownBy(() -> buildingService.toggleActive(1L, false, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("existsByWarehouseIdAndCode")
    class ExistsByWarehouseIdAndCode {
        @Test
        @DisplayName("存在するコードでtrueを返す")
        void existsByWarehouseIdAndCode_exists_returnsTrue() {
            when(buildingRepository.existsByWarehouseIdAndBuildingCode(10L, "BLDG01")).thenReturn(true);

            assertThat(buildingService.existsByWarehouseIdAndCode(10L, "BLDG01")).isTrue();
        }

        @Test
        @DisplayName("存在しないコードでfalseを返す")
        void existsByWarehouseIdAndCode_notExists_returnsFalse() {
            when(buildingRepository.existsByWarehouseIdAndBuildingCode(10L, "XXXX")).thenReturn(false);

            assertThat(buildingService.existsByWarehouseIdAndCode(10L, "XXXX")).isFalse();
        }
    }

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
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return b;
    }
}
