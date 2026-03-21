package com.wms.master.service;

import com.wms.master.entity.Partner;
import com.wms.master.repository.PartnerRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
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
@DisplayName("PartnerService")
class PartnerServiceTest {

    @Mock
    private PartnerRepository partnerRepository;

    @InjectMocks
    private PartnerService partnerService;

    @Nested
    @DisplayName("search")
    class Search {
        @Test
        @DisplayName("検索条件でページング結果を返す")
        void search_returnsPagedResult() {
            Partner p = createPartner(1L, "SUP-001", "テスト仕入先", "SUPPLIER");
            Page<Partner> page = new PageImpl<>(List.of(p));
            Pageable pageable = PageRequest.of(0, 20);
            when(partnerRepository.search("SUP", null, null, null, pageable)).thenReturn(page);

            Page<Partner> result = partnerService.search("SUP", null, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getPartnerCode()).isEqualTo("SUP-001");
        }

        @Test
        @DisplayName("partnerTypeフィルタを渡して検索できる")
        void search_withPartnerType_returnsFiltered() {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            Page<Partner> page = new PageImpl<>(List.of(p));
            Pageable pageable = PageRequest.of(0, 20);
            when(partnerRepository.search(null, null, "SUPPLIER", null, pageable)).thenReturn(page);

            Page<Partner> result = partnerService.search(null, null, "SUPPLIER", null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getPartnerType()).isEqualTo("SUPPLIER");
        }
    }

    @Nested
    @DisplayName("findAllSimple")
    class FindAllSimple {
        @Test
        @DisplayName("isActiveフィルタで全件リストを返す")
        void findAllSimple_returnsFilteredList() {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.findAllSimple(true)).thenReturn(List.of(p));

            List<Partner> result = partnerService.findAllSimple(true);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPartnerCode()).isEqualTo("SUP-001");
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {
        @Test
        @DisplayName("存在するIDで取引先を返す")
        void findById_exists_returnsPartner() {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(p));

            Partner result = partnerService.findById(1L);

            assertThat(result.getPartnerCode()).isEqualTo("SUP-001");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void findById_notExists_throwsException() {
            when(partnerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> partnerService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("取引先");
        }
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("新規取引先を登録できる")
        void create_success() {
            Partner p = createPartner(null, "SUP-002", "仕入先B", "SUPPLIER");
            when(partnerRepository.existsByPartnerCode("SUP-002")).thenReturn(false);
            when(partnerRepository.save(p)).thenReturn(p);

            Partner result = partnerService.create(p);

            assertThat(result.getPartnerCode()).isEqualTo("SUP-002");
            verify(partnerRepository).save(p);
        }

        @Test
        @DisplayName("重複コードでDuplicateResourceExceptionをスロー")
        void create_duplicateCode_throwsException() {
            Partner p = createPartner(null, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.existsByPartnerCode("SUP-001")).thenReturn(true);

            assertThatThrownBy(() -> partnerService.create(p))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("SUP-001");
        }

        @Test
        @DisplayName("TOCTOU競合時にDataIntegrityViolationExceptionをDuplicateResourceExceptionに変換")
        void create_toctouRace_throwsDuplicateResourceException() {
            Partner p = createPartner(null, "SUP-002", "仕入先B", "SUPPLIER");
            when(partnerRepository.existsByPartnerCode("SUP-002")).thenReturn(false);
            when(partnerRepository.save(p)).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> partnerService.create(p))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("SUP-002");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {
        @Test
        @DisplayName("取引先情報を更新できる")
        void update_success() {
            Partner existing = createPartner(1L, "SUP-001", "旧名称", "SUPPLIER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(partnerRepository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

            Partner result = partnerService.update(1L, "新名称", "シンメイショウ", "BOTH",
                    "東京都", "03-1234-5678", "担当太郎", "test@example.com", 0);

            assertThat(result.getPartnerName()).isEqualTo("新名称");
            assertThat(result.getPartnerNameKana()).isEqualTo("シンメイショウ");
            assertThat(result.getPartnerType()).isEqualTo("BOTH");
            assertThat(result.getPhone()).isEqualTo("03-1234-5678");
            assertThat(result.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("PUTは全置換のためオプション項目をnullにクリアできる")
        void update_clearsOptionalFields() {
            Partner existing = createPartner(1L, "SUP-001", "旧名称", "SUPPLIER");
            existing.setAddress("旧住所");
            existing.setPhone("03-0000-0000");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(partnerRepository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

            Partner result = partnerService.update(1L, "新名称", null, "SUPPLIER",
                    null, null, null, null, 0);

            assertThat(result.getAddress()).isNull();
            assertThat(result.getPhone()).isNull();
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void update_notFound_throwsException() {
            when(partnerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> partnerService.update(999L, "name", null, "SUPPLIER",
                    null, null, null, null, 0))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void update_optimisticLockConflict_throwsException() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(partnerRepository.save(any(Partner.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Partner.class.getName(), 1L));

            assertThatThrownBy(() -> partnerService.update(1L, "名前", null, "SUPPLIER",
                    null, null, null, null, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {
        @Test
        @DisplayName("取引先を無効化できる")
        void toggleActive_deactivate_success() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(partnerRepository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

            Partner result = partnerService.toggleActive(1L, false, 0);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("取引先を有効化できる")
        void toggleActive_activate_success() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            existing.deactivate();
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(partnerRepository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

            Partner result = partnerService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("既に同じ状態の場合はUPDATEなし（冪等性）")
        void toggleActive_alreadySameState_noUpdate() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            // isActive は true（デフォルト）
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));

            Partner result = partnerService.toggleActive(1L, true, 0);

            assertThat(result.getIsActive()).isTrue();
            verify(partnerRepository, never()).save(any());
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void toggleActive_optimisticLockConflict_throwsException() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(partnerRepository.save(any(Partner.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Partner.class.getName(), 1L));

            assertThatThrownBy(() -> partnerService.toggleActive(1L, false, 0))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {
        @Test
        @DisplayName("存在するコードでtrueを返す")
        void existsByCode_exists_returnsTrue() {
            when(partnerRepository.existsByPartnerCode("SUP-001")).thenReturn(true);

            assertThat(partnerService.existsByCode("SUP-001")).isTrue();
        }

        @Test
        @DisplayName("存在しないコードでfalseを返す")
        void existsByCode_notExists_returnsFalse() {
            when(partnerRepository.existsByPartnerCode("XXXX")).thenReturn(false);

            assertThat(partnerService.existsByCode("XXXX")).isFalse();
        }
    }

    // --- Helper ---

    private Partner createPartner(Long id, String code, String name, String type) {
        Partner p = new Partner();
        p.setPartnerCode(code);
        p.setPartnerName(name);
        p.setPartnerType(type);
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
