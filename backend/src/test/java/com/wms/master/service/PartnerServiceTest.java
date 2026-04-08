package com.wms.master.service;

import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.repository.PartnerRepository;
import com.wms.master.service.spi.PartnerUsageChecker;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartnerService")
class PartnerServiceTest {

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private PartnerUsageChecker inboundChecker;

    @Mock
    private PartnerUsageChecker outboundChecker;

    private PartnerService partnerService;

    /**
     * NOTE (m-9): 本番コードでは {@code InboundPartnerUsageChecker} に {@code @Order(10)}、
     * {@code OutboundPartnerUsageChecker} に {@code @Order(20)} が付与されているため、
     * Spring DI 時の {@code List<PartnerUsageChecker>} 注入順は inbound → outbound で保証される。
     * 本テストでは同じ並び順 ({@code List.of(inboundChecker, outboundChecker)}) を明示的に
     * 組み立てて単体検証し、DI 順序は結合テスト
     * ({@code PartnerIntegrationTest#deactivate_bothActive_inboundTakesPrecedence_returns422})
     * で end-to-end 検証する。
     */
    @BeforeEach
    void setUpService() {
        partnerService = new PartnerService(partnerRepository, List.of(inboundChecker, outboundChecker));
    }

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
            assertThat(result.getContent().get(0).getPartnerType()).isEqualTo(PartnerType.SUPPLIER);
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

        @Test
        @DisplayName("上限(1000件)を超える場合は1000件に切り詰められる")
        void findAllSimple_exceedsLimit_truncatedTo1000() {
            List<Partner> largeList = java.util.stream.IntStream.rangeClosed(1, 1001)
                    .mapToObj(i -> createPartner((long) i, "P-" + i, "取引先" + i, "SUPPLIER"))
                    .toList();
            when(partnerRepository.findAllSimple(null)).thenReturn(largeList);

            List<Partner> result = partnerService.findAllSimple(null);

            assertThat(result).hasSize(1000);
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

            Partner result = partnerService.update(new UpdatePartnerCommand(1L, "新名称", "シンメイショウ", PartnerType.BOTH,
                    "東京都", "03-1234-5678", "担当太郎", "test@example.com", 0));

            assertThat(result.getPartnerName()).isEqualTo("新名称");
            assertThat(result.getPartnerNameKana()).isEqualTo("シンメイショウ");
            assertThat(result.getPartnerType()).isEqualTo(PartnerType.BOTH);
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

            Partner result = partnerService.update(new UpdatePartnerCommand(1L, "新名称", null, PartnerType.SUPPLIER,
                    null, null, null, null, 0));

            assertThat(result.getAddress()).isNull();
            assertThat(result.getPhone()).isNull();
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void update_notFound_throwsException() {
            when(partnerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> partnerService.update(new UpdatePartnerCommand(999L, "name", null, PartnerType.SUPPLIER,
                    null, null, null, null, 0)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("バージョン不一致でOptimisticLockConflictExceptionをスロー")
        void update_versionMismatch_throwsException() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            existing.setVersion(5);
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> partnerService.update(new UpdatePartnerCommand(1L, "名前", null, PartnerType.SUPPLIER,
                    null, null, null, null, 3)))
                    .isInstanceOf(OptimisticLockConflictException.class)
                    .hasMessageContaining("他のユーザーによる更新が先行しました")
                    // OWASP A09: 内部 id をクライアント向け例外メッセージに露出しない
                    .hasMessageNotContaining("id=");
        }

        @Test
        @DisplayName("楽観的ロック競合でOptimisticLockConflictExceptionをスロー")
        void update_optimisticLockConflict_throwsException() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(partnerRepository.save(any(Partner.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Partner.class.getName(), 1L));

            assertThatThrownBy(() -> partnerService.update(new UpdatePartnerCommand(1L, "名前", null, PartnerType.SUPPLIER,
                    null, null, null, null, 0)))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {
        @Test
        @DisplayName("取引先を無効化できる (両チェッカーOK, inbound→outbound の順で呼ばれる)")
        void toggleActive_deactivate_success() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(inboundChecker.findBlockingReason(1L)).thenReturn(Optional.empty());
            when(outboundChecker.findBlockingReason(1L)).thenReturn(Optional.empty());
            when(partnerRepository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

            Partner result = partnerService.toggleActive(1L, false, 0);

            assertThat(result.getIsActive()).isFalse();

            // M-5: inbound → outbound の順で呼び出されることを検証
            InOrder inOrder = inOrder(inboundChecker, outboundChecker);
            inOrder.verify(inboundChecker).findBlockingReason(1L);
            inOrder.verify(outboundChecker).findBlockingReason(1L);
        }

        @Test
        @DisplayName("[BR-001] 両チェッカーNGの場合はinboundエラーが優先される (outboundは呼ばれない)")
        void toggleActive_bothBlocking_inboundTakesPrecedence() {
            Partner existing = createPartner(1L, "BTH-001", "兼用取引先", "BOTH");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(inboundChecker.findBlockingReason(1L))
                    .thenReturn(Optional.of("CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND"));

            assertThatThrownBy(() -> partnerService.toggleActive(1L, false, 0))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND")
                    // C-R2-3: 入荷要因のメッセージは「入荷予定」を明示する
                    .hasMessageContaining("処理中の入荷予定")
                    // SEC-R2-Min-1 (OWASP A09): BR 違反メッセージにも内部 id を含めない
                    .hasMessageNotContaining("id=");

            // inboundで即ブロックされ、outbound checker は評価されない
            verify(outboundChecker, never()).findBlockingReason(any());
            verify(partnerRepository, never()).save(any());
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

        // m-6: 処理中入荷のみがあるケースは toggleActive_bothBlocking_inboundTakesPrecedence
        //      (inbound が必ず outbound に優先して評価される) に集約したため削除

        @Test
        @DisplayName("[BR-001] 処理中受注伝票がある場合はBusinessRuleViolationException(CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND)")
        void toggleActive_hasActiveOutbound_throwsBusinessRuleViolation() {
            Partner existing = createPartner(1L, "CUS-001", "出荷先A", "CUSTOMER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(inboundChecker.findBlockingReason(1L)).thenReturn(Optional.empty());
            when(outboundChecker.findBlockingReason(1L))
                    .thenReturn(Optional.of("CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND"));

            assertThatThrownBy(() -> partnerService.toggleActive(1L, false, 0))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND")
                    // C-R2-3: 受注要因のメッセージは「受注」を明示する
                    .hasMessageContaining("処理中の受注")
                    // SEC-R2-Min-1 (OWASP A09): BR 違反メッセージにも内部 id を含めない
                    .hasMessageNotContaining("id=");

            verify(partnerRepository, never()).save(any());
        }

        @Test
        @DisplayName("有効化(isActive=true)の場合はチェッカーは呼ばれない")
        void toggleActive_activate_skipsCheckers() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            existing.deactivate();
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(partnerRepository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

            partnerService.toggleActive(1L, true, 0);

            verifyNoInteractions(inboundChecker);
            verifyNoInteractions(outboundChecker);
        }

        @Test
        @DisplayName("既に無効状態に対して再度無効化(冪等no-op)の場合はチェッカーもsaveも呼ばれない")
        void toggleActive_alreadyInactive_idempotentNoOp_skipsCheckers() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            existing.deactivate();
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));

            Partner result = partnerService.toggleActive(1L, false, 0);

            assertThat(result.getIsActive()).isFalse();
            verifyNoInteractions(inboundChecker);
            verifyNoInteractions(outboundChecker);
            verify(partnerRepository, never()).save(any());
        }

        @Test
        @DisplayName("未知のエラーコードでもデフォルトメッセージで BusinessRuleViolationException")
        void toggleActive_unknownBlockingReason_throwsBusinessRuleViolationWithDefaultMessage() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(inboundChecker.findBlockingReason(1L)).thenReturn(Optional.of("UNKNOWN_REASON"));

            assertThatThrownBy(() -> partnerService.toggleActive(1L, false, 0))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "UNKNOWN_REASON")
                    .hasMessageContaining("処理中の伝票")
                    // SEC-R2-Min-1 (OWASP A09): BR 違反メッセージにも内部 id を含めない
                    .hasMessageNotContaining("id=");

            verify(partnerRepository, never()).save(any());
        }

        @Test
        @DisplayName("[#453] no-op (既無効化) でも stale version は 409 を返す")
        void toggleActive_noOpWithStaleVersion_throwsOptimisticLockConflict() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            existing.deactivate();
            existing.setVersion(5);
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> partnerService.toggleActive(1L, false, 3))
                    .isInstanceOf(OptimisticLockConflictException.class)
                    .hasMessageContaining("他のユーザーによる更新が先行しました")
                    // OWASP A09: 内部 id をクライアント向け例外メッセージに露出しない
                    .hasMessageNotContaining("id=");

            verifyNoInteractions(inboundChecker);
            verifyNoInteractions(outboundChecker);
            verify(partnerRepository, never()).save(any());
        }

        @Test
        @DisplayName("[#453] BR 違反かつ stale version の場合は 409 (OL) が 422 (BR) より優先される")
        void toggleActive_brViolationAndStaleVersion_optimisticLockTakesPrecedence() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            existing.setVersion(5);
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> partnerService.toggleActive(1L, false, 3))
                    .isInstanceOf(OptimisticLockConflictException.class);

            // version 先行チェックで止まるため BR チェッカーは呼ばれない
            verifyNoInteractions(inboundChecker);
            verifyNoInteractions(outboundChecker);
            verify(partnerRepository, never()).save(any());
        }

        @Test
        @DisplayName("[#453] version 不一致 (有効化リクエスト) でも 409 を返す")
        void toggleActive_activateWithStaleVersion_throwsOptimisticLockConflict() {
            Partner existing = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            existing.deactivate();
            existing.setVersion(2);
            when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> partnerService.toggleActive(1L, true, 1))
                    .isInstanceOf(OptimisticLockConflictException.class);

            verify(partnerRepository, never()).save(any());
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
        p.setPartnerType(PartnerType.valueOf(type));
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
