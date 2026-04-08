package com.wms.outbound.service;

import com.wms.outbound.repository.OutboundSlipRepository;
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
@DisplayName("OutboundPartnerUsageChecker")
class OutboundPartnerUsageCheckerTest {

    @Mock
    private OutboundSlipRepository outboundSlipRepository;

    @InjectMocks
    private OutboundPartnerUsageChecker checker;

    @Test
    @DisplayName("処理中受注伝票が存在する場合 errorCode を返す")
    void findBlockingReason_hasActiveSlip_returnsErrorCode() {
        // m-1 / m-8: ステータス集合は本番定数 ACTIVE_STATUSES を基準に argThat で厳密検証する
        when(outboundSlipRepository.existsByPartnerIdAndStatusIn(
                eq(1L),
                argThat(s -> OutboundPartnerUsageChecker.ACTIVE_STATUSES.equals(s))))
                .thenReturn(true);

        Optional<String> result = checker.findBlockingReason(1L);

        assertThat(result).contains("CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND");
    }

    @Test
    @DisplayName("処理中受注伝票が存在しない場合 empty を返す")
    void findBlockingReason_noActiveSlip_returnsEmpty() {
        when(outboundSlipRepository.existsByPartnerIdAndStatusIn(
                eq(2L),
                argThat(s -> OutboundPartnerUsageChecker.ACTIVE_STATUSES.equals(s))))
                .thenReturn(false);

        Optional<String> result = checker.findBlockingReason(2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("チェック対象ステータスは ACTIVE_STATUSES と一致する (ORDERED/PARTIAL_ALLOCATED/ALLOCATED/PICKING_COMPLETED/INSPECTING)")
    void findBlockingReason_passesExpectedStatuses() {
        // m-1: 生文字列を複製せず本番定数を参照。m-8: argThat で集合を厳密検証
        // NOTE: 設計書上の "PENDING" / "PICKING" は実装/DB制約に存在しないため、
        //       それぞれ ORDERED / PICKING_COMPLETED が対応する
        when(outboundSlipRepository.existsByPartnerIdAndStatusIn(
                eq(3L),
                argThat(s -> OutboundPartnerUsageChecker.ACTIVE_STATUSES.equals(s))))
                .thenReturn(false);

        Optional<String> result = checker.findBlockingReason(3L);

        assertThat(result).isEmpty();
    }
}
