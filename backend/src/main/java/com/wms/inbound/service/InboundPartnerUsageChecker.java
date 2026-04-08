package com.wms.inbound.service;

import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.master.service.spi.PartnerUsageChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 入荷モジュール側の {@link PartnerUsageChecker} 実装。
 *
 * <p>処理中の入荷伝票 (PLANNED / CONFIRMED / INSPECTING) が紐付いている取引先は
 * 無効化できないことを判定する (API-03 BR-001)。</p>
 *
 * <p><b>実行順序:</b> API-03 4章の業務フロー図では「入荷チェック → 受注チェック」の
 * 順序が契約として規定されているため、{@code @Order(10)} を付与して
 * {@link com.wms.outbound.service.OutboundPartnerUsageChecker} ({@code @Order(20)})
 * より先に評価されることを Spring DI レベルで保証する。</p>
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class InboundPartnerUsageChecker implements PartnerUsageChecker {

    /**
     * 「処理中」とみなす入荷伝票ステータス。
     * PARTIAL_STORED / STORED / CANCELLED は完了系のため対象外。
     */
    static final Set<String> ACTIVE_STATUSES = Set.of("PLANNED", "CONFIRMED", "INSPECTING");

    private static final String ERROR_CODE = "CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND";

    private final InboundSlipRepository inboundSlipRepository;

    @Override
    public Optional<String> findBlockingReason(Long partnerId) {
        if (inboundSlipRepository.existsByPartnerIdAndStatusIn(partnerId, ACTIVE_STATUSES)) {
            return Optional.of(ERROR_CODE);
        }
        return Optional.empty();
    }
}
