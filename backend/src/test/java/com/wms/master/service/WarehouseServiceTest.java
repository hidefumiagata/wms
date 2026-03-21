package com.wms.master.service;

import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseService")
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    @Nested
    @DisplayName("search")
    class Search {
        @Test
        @DisplayName("検索条件でページング結果を返す")
        void search_returnsPagedResult() {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            Page<Warehouse> page = new PageImpl<>(List.of(w));
            Pageable pageable = PageRequest.of(0, 20);
            when(warehouseRepository.search("WARA", null, null, pageable)).thenReturn(page);

            Page<Warehouse> result = warehouseService.search("WARA", null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getWarehouseCode()).isEqualTo("WARA");
        }
    }

    @Nested
    @DisplayName("findAllSimple")
    class FindAllSimple {
        @Test
        @DisplayName("isActiveフィルタで全件リストを返す")
        void findAllSimple_returnsFilteredList() {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseRepository.findAllSimple(true)).thenReturn(List.of(w));

            List<Warehouse> result = warehouseService.findAllSimple(true);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {
        @Test
        @DisplayName("存在するIDで倉庫を返す")
        void findById_exists_returnsWarehouse() {
            Warehouse w = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(w));

            Warehouse result = warehouseService.findById(1L);

            assertThat(result.getWarehouseCode()).isEqualTo("WARA");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void findById_notExists_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> warehouseService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("倉庫");
        }
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("新規倉庫を登録できる")
        void create_success() {
            Warehouse w = createWarehouse(null, "WARB", "大阪DC");
            when(warehouseRepository.existsByWarehouseCode("WARB")).thenReturn(false);
            when(warehouseRepository.save(w)).thenReturn(w);

            Warehouse result = warehouseService.create(w);

            assertThat(result.getWarehouseCode()).isEqualTo("WARB");
            verify(warehouseRepository).save(w);
        }

        @Test
        @DisplayName("重複コードでDuplicateResourceExceptionをスロー")
        void create_duplicateCode_throwsException() {
            Warehouse w = createWarehouse(null, "WARA", "東京DC");
            when(warehouseRepository.existsByWarehouseCode("WARA")).thenReturn(true);

            assertThatThrownBy(() -> warehouseService.create(w))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("WARA");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {
        @Test
        @DisplayName("倉庫情報を更新できる")
        void update_success() {
            Warehouse existing = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

            Warehouse result = warehouseService.update(1L, "東京DC（新）", "トウキョウディーシー", "新住所", 0);

            assertThat(result.getWarehouseName()).isEqualTo("東京DC（新）");
            assertThat(result.getWarehouseNameKana()).isEqualTo("トウキョウディーシー");
            assertThat(result.getAddress()).isEqualTo("新住所");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void update_notFound_throwsException() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> warehouseService.update(999L, "name", null, null, 0))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void update_optimisticLockConflict_throwsException() {
            Warehouse existing = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(warehouseRepository.save(any(Warehouse.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Warehouse.class.getName(), 1L));

            assertThatThrownBy(() -> warehouseService.update(1L, "名前", null, null, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {
        @Test
        @DisplayName("倉庫を無効化できる")
        void toggleActive_deactivate_success() {
            Warehouse existing = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

            Warehouse result = warehouseService.toggleActive(1L, false, 0);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("倉庫を有効化できる")
        void toggleActive_activate_success() {
            Warehouse existing = createWarehouse(1L, "WARA", "東京DC");
            existing.deactivate();
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

            Warehouse result = warehouseService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void toggleActive_optimisticLockConflict_throwsException() {
            Warehouse existing = createWarehouse(1L, "WARA", "東京DC");
            when(warehouseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(warehouseRepository.save(any(Warehouse.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Warehouse.class.getName(), 1L));

            assertThatThrownBy(() -> warehouseService.toggleActive(1L, false, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {
        @Test
        @DisplayName("存在するコードでtrueを返す")
        void existsByCode_exists_returnsTrue() {
            when(warehouseRepository.existsByWarehouseCode("WARA")).thenReturn(true);

            assertThat(warehouseService.existsByCode("WARA")).isTrue();
        }

        @Test
        @DisplayName("存在しないコードでfalseを返す")
        void existsByCode_notExists_returnsFalse() {
            when(warehouseRepository.existsByWarehouseCode("XXXX")).thenReturn(false);

            assertThat(warehouseService.existsByCode("XXXX")).isFalse();
        }
    }

    // --- Helper ---

    private Warehouse createWarehouse(Long id, String code, String name) {
        Warehouse w = new Warehouse();
        w.setWarehouseCode(code);
        w.setWarehouseName(name);
        if (id != null) {
            // BaseEntityのidはprotectedなのでリフレクションでセット
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
}
