package com.wms.outbound.service;

import com.wms.outbound.repository.OutboundSlipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
        when(outboundSlipRepository.existsByPartnerIdAndStatusIn(eq(1L), any())).thenReturn(true);

        Optional<String> result = checker.findBlockingReason(1L);

        assertThat(result).contains("CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND");
    }

    @Test
    @DisplayName("処理中受注伝票が存在しない場合 empty を返す")
    void findBlockingReason_noActiveSlip_returnsEmpty() {
        when(outboundSlipRepository.existsByPartnerIdAndStatusIn(eq(2L), any())).thenReturn(false);

        Optional<String> result = checker.findBlockingReason(2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("チェック対象ステータスは ORDERED/PARTIAL_ALLOCATED/ALLOCATED/PICKING_COMPLETED/INSPECTING の5種")
    void findBlockingReason_passesExpectedStatuses() {
        when(outboundSlipRepository.existsByPartnerIdAndStatusIn(eq(3L), any())).thenReturn(false);

        checker.findBlockingReason(3L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(outboundSlipRepository).existsByPartnerIdAndStatusIn(eq(3L), captor.capture());
        // NOTE: 設計書上の "PENDING" / "PICKING" は実装/DB制約に存在しないため、
        //       それぞれ ORDERED / PICKING_COMPLETED が対応する
        assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(
                Set.of("ORDERED", "PARTIAL_ALLOCATED", "ALLOCATED", "PICKING_COMPLETED", "INSPECTING"));
    }
}
