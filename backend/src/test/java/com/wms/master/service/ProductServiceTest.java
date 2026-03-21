package com.wms.master.service;

import com.wms.master.entity.Product;
import com.wms.master.repository.ProductRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Nested
    @DisplayName("search")
    class Search {
        @Test
        @DisplayName("検索条件でページング結果を返す")
        void search_returnsPagedResult() {
            Product p = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            Page<Product> page = new PageImpl<>(List.of(p));
            Pageable pageable = PageRequest.of(0, 20);
            when(productRepository.search("P-001", null, null, null, null, pageable)).thenReturn(page);

            Page<Product> result = productService.search("P-001", null, null, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getProductCode()).isEqualTo("P-001");
        }

        @Test
        @DisplayName("storageConditionフィルタを渡して検索できる")
        void search_withStorageCondition_returnsFiltered() {
            Product p = createProduct(1L, "P-001", "冷蔵商品A", "REFRIGERATED");
            Page<Product> page = new PageImpl<>(List.of(p));
            Pageable pageable = PageRequest.of(0, 20);
            when(productRepository.search(null, null, "REFRIGERATED", null, null, pageable)).thenReturn(page);

            Page<Product> result = productService.search(null, null, "REFRIGERATED", null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStorageCondition()).isEqualTo("REFRIGERATED");
        }

        @Test
        @DisplayName("shipmentStopFlagフィルタを渡して検索できる")
        void search_withShipmentStopFlag_returnsFiltered() {
            Product p = createProduct(1L, "P-002", "出荷停止商品", "AMBIENT");
            p.setShipmentStopFlag(true);
            Page<Product> page = new PageImpl<>(List.of(p));
            Pageable pageable = PageRequest.of(0, 20);
            when(productRepository.search(null, null, null, null, true, pageable)).thenReturn(page);

            Page<Product> result = productService.search(null, null, null, null, true, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getShipmentStopFlag()).isTrue();
        }
    }

    @Nested
    @DisplayName("findAllSimple")
    class FindAllSimple {
        @Test
        @DisplayName("isActiveフィルタで全件リストを返す")
        void findAllSimple_returnsFilteredList() {
            Product p = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            when(productRepository.findAllSimple(true)).thenReturn(List.of(p));

            List<Product> result = productService.findAllSimple(true);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductCode()).isEqualTo("P-001");
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {
        @Test
        @DisplayName("存在するIDで商品を返す")
        void findById_exists_returnsProduct() {
            Product p = createProduct(1L, "P-001", "テスト商品A", "AMBIENT");
            when(productRepository.findById(1L)).thenReturn(Optional.of(p));

            Product result = productService.findById(1L);

            assertThat(result.getProductCode()).isEqualTo("P-001");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void findById_notExists_throwsException() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("商品");
        }
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("新規商品を登録できる")
        void create_success() {
            Product p = createProduct(null, "P-002", "テスト商品B", "AMBIENT");
            when(productRepository.existsByProductCode("P-002")).thenReturn(false);
            when(productRepository.save(p)).thenReturn(p);

            Product result = productService.create(p);

            assertThat(result.getProductCode()).isEqualTo("P-002");
            verify(productRepository).save(p);
        }

        @Test
        @DisplayName("重複コードでDuplicateResourceExceptionをスロー")
        void create_duplicateCode_throwsException() {
            Product p = createProduct(null, "P-001", "テスト商品A", "AMBIENT");
            when(productRepository.existsByProductCode("P-001")).thenReturn(true);

            assertThatThrownBy(() -> productService.create(p))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("P-001");
        }

        @Test
        @DisplayName("TOCTOU競合時にDataIntegrityViolationExceptionをDuplicateResourceExceptionに変換")
        void create_toctouRace_throwsDuplicateResourceException() {
            Product p = createProduct(null, "P-002", "テスト商品B", "AMBIENT");
            when(productRepository.existsByProductCode("P-002")).thenReturn(false);
            when(productRepository.save(p)).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> productService.create(p))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("P-002");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {
        @Test
        @DisplayName("商品情報を更新できる")
        void update_success() {
            Product existing = createProduct(1L, "P-001", "旧商品名", "AMBIENT");
            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product result = productService.update(1L, "新商品名", "シンショウヒンメイ",
                    12, 6, "4901234567890", "REFRIGERATED",
                    false, true, false, false, true, 0);

            assertThat(result.getProductName()).isEqualTo("新商品名");
            assertThat(result.getProductNameKana()).isEqualTo("シンショウヒンメイ");
            assertThat(result.getCaseQuantity()).isEqualTo(12);
            assertThat(result.getBallQuantity()).isEqualTo(6);
            assertThat(result.getBarcode()).isEqualTo("4901234567890");
            assertThat(result.getStorageCondition()).isEqualTo("REFRIGERATED");
            assertThat(result.getLotManageFlag()).isTrue();
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("PUTは全置換のためオプション項目をnullにクリアできる")
        void update_clearsOptionalFields() {
            Product existing = createProduct(1L, "P-001", "旧商品名", "AMBIENT");
            existing.setProductNameKana("カナ");
            existing.setBarcode("4901111111111");
            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product result = productService.update(1L, "新商品名", null,
                    6, 10, null, "AMBIENT",
                    false, false, false, false, true, 0);

            assertThat(result.getProductNameKana()).isNull();
            assertThat(result.getBarcode()).isNull();
        }

        @Test
        @DisplayName("isActive=falseで無効化できる")
        void update_deactivates() {
            Product existing = createProduct(1L, "P-001", "商品A", "AMBIENT");
            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product result = productService.update(1L, "商品A", null,
                    6, 10, null, "AMBIENT",
                    false, false, false, false, false, 0);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void update_notFound_throwsException() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(999L, "商品名", null,
                    6, 10, null, "AMBIENT",
                    false, false, false, false, true, 0))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void update_optimisticLockConflict_throwsException() {
            Product existing = createProduct(1L, "P-001", "商品A", "AMBIENT");
            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Product.class.getName(), 1L));

            assertThatThrownBy(() -> productService.update(1L, "商品A", null,
                    6, 10, null, "AMBIENT",
                    false, false, false, false, true, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {
        @Test
        @DisplayName("商品を無効化できる")
        void toggleActive_deactivate_success() {
            Product existing = createProduct(1L, "P-001", "商品A", "AMBIENT");
            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product result = productService.toggleActive(1L, false, 0);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("商品を有効化できる")
        void toggleActive_activate_success() {
            Product existing = createProduct(1L, "P-001", "商品A", "AMBIENT");
            existing.deactivate();
            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product result = productService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("既に同じ状態の場合はUPDATEなし（冪等性）")
        void toggleActive_alreadySameState_noUpdate() {
            Product existing = createProduct(1L, "P-001", "商品A", "AMBIENT");
            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

            Product result = productService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void toggleActive_optimisticLockConflict_throwsException() {
            Product existing = createProduct(1L, "P-001", "商品A", "AMBIENT");
            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Product.class.getName(), 1L));

            assertThatThrownBy(() -> productService.toggleActive(1L, false, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {
        @Test
        @DisplayName("存在するコードでtrueを返す")
        void existsByCode_exists_returnsTrue() {
            when(productRepository.existsByProductCode("P-001")).thenReturn(true);

            assertThat(productService.existsByCode("P-001")).isTrue();
        }

        @Test
        @DisplayName("存在しないコードでfalseを返す")
        void existsByCode_notExists_returnsFalse() {
            when(productRepository.existsByProductCode("XXXX")).thenReturn(false);

            assertThat(productService.existsByCode("XXXX")).isFalse();
        }
    }

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
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return p;
    }
}
