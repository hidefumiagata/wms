package com.wms.inbound.service;

import com.wms.inbound.repository.InboundSlipRepository;
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
@DisplayName("InboundPartnerUsageChecker")
class InboundPartnerUsageCheckerTest {

    @Mock
    private InboundSlipRepository inboundSlipRepository;

    @InjectMocks
    private InboundPartnerUsageChecker checker;

    @Test
    @DisplayName("処理中入荷伝票が存在する場合 errorCode を返す")
    void findBlockingReason_hasActiveSlip_returnsErrorCode() {
        when(inboundSlipRepository.existsByPartnerIdAndStatusIn(eq(1L), any())).thenReturn(true);

        Optional<String> result = checker.findBlockingReason(1L);

        assertThat(result).contains("CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND");
    }

    @Test
    @DisplayName("処理中入荷伝票が存在しない場合 empty を返す")
    void findBlockingReason_noActiveSlip_returnsEmpty() {
        when(inboundSlipRepository.existsByPartnerIdAndStatusIn(eq(2L), any())).thenReturn(false);

        Optional<String> result = checker.findBlockingReason(2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("チェック対象ステータスは PLANNED/CONFIRMED/INSPECTING の3種")
    void findBlockingReason_passesExpectedStatuses() {
        when(inboundSlipRepository.existsByPartnerIdAndStatusIn(eq(3L), any())).thenReturn(false);

        checker.findBlockingReason(3L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(inboundSlipRepository).existsByPartnerIdAndStatusIn(eq(3L), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(
                Set.of("PLANNED", "CONFIRMED", "INSPECTING"));
    }
}
