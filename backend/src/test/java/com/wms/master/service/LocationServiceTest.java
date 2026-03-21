package com.wms.master.service;

import com.wms.master.entity.Area;
import com.wms.master.entity.Location;
import com.wms.master.repository.AreaRepository;
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
@DisplayName("LocationService")
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private AreaRepository areaRepository;

    @InjectMocks
    private LocationService locationService;

    @Nested
    @DisplayName("search")
    class Search {
        @Test
        @DisplayName("codePrefixフィルタでページング結果を返す")
        void search_withCodePrefix_returnsPagedResult() {
            Location l = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            Page<Location> page = new PageImpl<>(List.of(l));
            Pageable pageable = PageRequest.of(0, 20);
            when(locationRepository.search(100L, null, "A-01", null, pageable)).thenReturn(page);

            Page<Location> result = locationService.search(100L, null, "A-01", null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getLocationCode()).isEqualTo("A-01-A-01-01-01");
        }

        @Test
        @DisplayName("areaIdフィルタでページング結果を返す")
        void search_withAreaId_returnsPagedResult() {
            Location l = createLocation(1L, 10L, 100L, "B-01-A-01-01-01");
            Page<Location> page = new PageImpl<>(List.of(l));
            Pageable pageable = PageRequest.of(0, 20);
            when(locationRepository.search(null, 10L, null, null, pageable)).thenReturn(page);

            Page<Location> result = locationService.search(null, 10L, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAreaId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("count")
    class Count {
        @Test
        @DisplayName("フィルタ条件で件数を返す")
        void count_returnsFilteredCount() {
            when(locationRepository.countFiltered(100L, 10L, true)).thenReturn(5L);

            long result = locationService.count(100L, 10L, true);

            assertThat(result).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {
        @Test
        @DisplayName("存在するIDでロケーションを返す")
        void findById_exists_returnsLocation() {
            Location l = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(l));

            Location result = locationService.findById(1L);

            assertThat(result.getLocationCode()).isEqualTo("A-01-A-01-01-01");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void findById_notExists_throwsException() {
            when(locationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ロケーション");
        }
    }

    @Nested
    @DisplayName("findByIds")
    class FindByIds {
        @Test
        @DisplayName("複数IDでMapを返す")
        void findByIds_returnsMap() {
            Location l1 = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            Location l2 = createLocation(2L, 10L, 100L, "A-01-A-01-01-02");
            when(locationRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(l1, l2));

            Map<Long, Location> result = locationService.findByIds(List.of(1L, 2L));

            assertThat(result).hasSize(2);
            assertThat(result.get(1L).getLocationCode()).isEqualTo("A-01-A-01-01-01");
            assertThat(result.get(2L).getLocationCode()).isEqualTo("A-01-A-01-01-02");
        }
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("STOCKエリアで正常にロケーションを登録できる（warehouseIdがエリアから設定される）")
        void create_stockArea_success() {
            Area area = createArea(10L, "AREA-01", "STOCK");
            Location location = new Location();
            location.setAreaId(10L);
            location.setLocationCode("A-01-A-01-01-01");

            when(areaRepository.findById(10L)).thenReturn(Optional.of(area));
            when(locationRepository.existsByWarehouseIdAndLocationCode(100L, "A-01-A-01-01-01")).thenReturn(false);
            when(locationRepository.save(location)).thenReturn(location);

            Location result = locationService.create(location);

            assertThat(result.getWarehouseId()).isEqualTo(100L);
            verify(areaRepository).findById(10L);
            // STOCKエリアは制限チェックをスキップ
            verify(locationRepository, never()).countByAreaId(any());
        }

        @Test
        @DisplayName("INBOUNDエリアで1件制限内なら正常に登録できる")
        void create_inboundArea_withinLimit_success() {
            Area area = createArea(10L, "AREA-IN", "INBOUND");
            Location location = new Location();
            location.setAreaId(10L);
            location.setLocationCode("INBOUND-01");

            when(areaRepository.findById(10L)).thenReturn(Optional.of(area));
            when(locationRepository.countByAreaId(10L)).thenReturn(0L);
            when(locationRepository.existsByWarehouseIdAndLocationCode(100L, "INBOUND-01")).thenReturn(false);
            when(locationRepository.save(location)).thenReturn(location);

            Location result = locationService.create(location);

            assertThat(result).isNotNull();
            verify(locationRepository).countByAreaId(10L);
        }

        @Test
        @DisplayName("OUTBOUNDエリアで1件制限を超えるとARIA_LOCATION_LIMIT_EXCEEDED(422)をスロー")
        void create_outboundArea_limitExceeded_throwsException() {
            Area area = createArea(10L, "AREA-OUT", "OUTBOUND");
            Location location = new Location();
            location.setAreaId(10L);
            location.setLocationCode("OUTBOUND-01");

            when(areaRepository.findById(10L)).thenReturn(Optional.of(area));
            when(locationRepository.countByAreaId(10L)).thenReturn(1L);

            assertThatThrownBy(() -> locationService.create(location))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ロケーションを1件のみ登録できます");
        }

        @Test
        @DisplayName("RETURNエリアで1件制限を超えるとARIA_LOCATION_LIMIT_EXCEEDED(422)をスロー")
        void create_returnArea_limitExceeded_throwsException() {
            Area area = createArea(10L, "AREA-RET", "RETURN");
            Location location = new Location();
            location.setAreaId(10L);
            location.setLocationCode("RETURN-01");

            when(areaRepository.findById(10L)).thenReturn(Optional.of(area));
            when(locationRepository.countByAreaId(10L)).thenReturn(1L);

            assertThatThrownBy(() -> locationService.create(location))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ロケーションを1件のみ登録できます");
        }

        @Test
        @DisplayName("STOCKエリアでは複数ロケーションを登録できる（制限チェックをスキップ）")
        void create_stockArea_multipleLocations_noLimitCheck() {
            Area area = createArea(10L, "AREA-STOCK", "STOCK");
            Location location = new Location();
            location.setAreaId(10L);
            location.setLocationCode("STOCK-99");

            when(areaRepository.findById(10L)).thenReturn(Optional.of(area));
            when(locationRepository.existsByWarehouseIdAndLocationCode(100L, "STOCK-99")).thenReturn(false);
            when(locationRepository.save(location)).thenReturn(location);

            locationService.create(location);

            verify(locationRepository, never()).countByAreaId(any());
        }

        @Test
        @DisplayName("存在しないエリアIDで404をスロー")
        void create_areaNotFound_throwsException() {
            Location location = new Location();
            location.setAreaId(999L);
            location.setLocationCode("X-99");

            when(areaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.create(location))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("エリア");
        }

        @Test
        @DisplayName("重複コードでDuplicateResourceExceptionをスロー")
        void create_duplicateCode_throwsException() {
            Area area = createArea(10L, "AREA-01", "STOCK");
            Location location = new Location();
            location.setAreaId(10L);
            location.setLocationCode("DUPLICATE-01");

            when(areaRepository.findById(10L)).thenReturn(Optional.of(area));
            when(locationRepository.existsByWarehouseIdAndLocationCode(100L, "DUPLICATE-01")).thenReturn(true);

            assertThatThrownBy(() -> locationService.create(location))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("DUPLICATE-01");
        }

        @Test
        @DisplayName("TOCTOU競合時にDataIntegrityViolationExceptionをDuplicateResourceExceptionに変換")
        void create_toctouRace_throwsDuplicateResourceException() {
            Area area = createArea(10L, "AREA-01", "STOCK");
            Location location = new Location();
            location.setAreaId(10L);
            location.setLocationCode("TOCTOU-01");

            when(areaRepository.findById(10L)).thenReturn(Optional.of(area));
            when(locationRepository.existsByWarehouseIdAndLocationCode(100L, "TOCTOU-01")).thenReturn(false);
            when(locationRepository.save(location)).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> locationService.create(location))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("TOCTOU-01");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {
        @Test
        @DisplayName("ロケーション情報を更新できる")
        void update_success() {
            Location existing = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            Location result = locationService.update(1L, "新しい棚名", 0);

            assertThat(result.getLocationName()).isEqualTo("新しい棚名");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void update_notFound_throwsException() {
            when(locationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.update(999L, "棚名", 0))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("バージョン不一致で事前チェックによるOptimisticLockConflictExceptionをスロー")
        void update_versionMismatch_throwsException() {
            Location existing = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            // existing.version == 0, request version == 99
            when(locationRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> locationService.update(1L, "棚名", 99))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void update_optimisticLockConflict_throwsException() {
            Location existing = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(locationRepository.save(any(Location.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Location.class.getName(), 1L));

            assertThatThrownBy(() -> locationService.update(1L, "棚名", 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {
        @Test
        @DisplayName("ロケーションを無効化できる")
        void toggleActive_deactivate_success() {
            Location existing = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            Location result = locationService.toggleActive(1L, false, 0);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("ロケーションを有効化できる")
        void toggleActive_activate_success() {
            Location existing = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            existing.deactivate();
            when(locationRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            Location result = locationService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("既に同じ状態の場合はUPDATEなし（冪等性）")
        void toggleActive_alreadySameState_noUpdate() {
            Location existing = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(existing));

            Location result = locationService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("バージョン不一致で事前チェックによるOptimisticLockConflictExceptionをスロー")
        void toggleActive_versionMismatch_throwsException() {
            Location existing = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            // existing.version == 0, request version == 99
            when(locationRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> locationService.toggleActive(1L, false, 99))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void toggleActive_optimisticLockConflict_throwsException() {
            Location existing = createLocation(1L, 10L, 100L, "A-01-A-01-01-01");
            when(locationRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(locationRepository.save(any(Location.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Location.class.getName(), 1L));

            assertThatThrownBy(() -> locationService.toggleActive(1L, false, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

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
}
