package com.wms.inbound.service;

import com.wms.inbound.repository.InboundSlipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InboundPartnerUsageChecker")
class InboundPartnerUsageCheckerTest {

    @Mock
    private InboundSlipRepository inboundSlipRepository;

    @InjectMocks
    private InboundPartnerUsageChecker checker;

    @Test
    @DisplayName("処理中入荷伝票が存在する場合 errorCode を返す")
    void findBlockingReason_hasActiveSlip_returnsErrorCode() {
        // m-1 / m-8: ステータス集合は本番定数 ACTIVE_STATUSES を基準に argThat で厳密検証する
        when(inboundSlipRepository.existsByPartnerIdAndStatusIn(
                eq(1L),
                argThat(s -> InboundPartnerUsageChecker.ACTIVE_STATUSES.equals(s))))
                .thenReturn(true);

        Optional<String> result = checker.findBlockingReason(1L);

        assertThat(result).contains("CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND");
    }

    @Test
    @DisplayName("処理中入荷伝票が存在しない場合 empty を返す (ステータス集合は ACTIVE_STATUSES と一致)")
    void findBlockingReason_noActiveSlip_returnsEmpty() {
        // m-1 / m-8 / C-R2-5: argThat で集合を厳密検証することで、ステータス集合の一致確認
        // を兼ねる。重複していた passesExpectedStatuses テストは削除済み。
        when(inboundSlipRepository.existsByPartnerIdAndStatusIn(
                eq(2L),
                argThat(s -> InboundPartnerUsageChecker.ACTIVE_STATUSES.equals(s))))
                .thenReturn(false);

        Optional<String> result = checker.findBlockingReason(2L);

        assertThat(result).isEmpty();
    }
}
