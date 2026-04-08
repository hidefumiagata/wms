package com.wms.outbound.service;

import com.wms.master.service.spi.PartnerUsageChecker;
import com.wms.outbound.repository.OutboundSlipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 出荷モジュール側の {@link PartnerUsageChecker} 実装。
 *
 * <p>処理中の出荷伝票 (受注) が紐付いている取引先は無効化できないことを判定する
 * (API-03 BR-001)。</p>
 *
 * <p><b>ステータス対応:</b> 設計書 (API-03) では「処理中受注」を
 * {@code PENDING}/{@code ALLOCATED}/{@code PICKING}/{@code INSPECTING} と記載しているが、
 * 実装/DB制約 ({@code outbound_slips.status}) には {@code PENDING}/{@code PICKING} が
 * 存在しない。本実装では {@code SHIPPED}/{@code CANCELLED} を除く全ステータス
 * (= {@code ORDERED}, {@code PARTIAL_ALLOCATED}, {@code ALLOCATED},
 * {@code PICKING_COMPLETED}, {@code INSPECTING}) を「処理中」として扱う。</p>
 */
@Component
@RequiredArgsConstructor
public class OutboundPartnerUsageChecker implements PartnerUsageChecker {

    /**
     * 「処理中」とみなす出荷伝票ステータス (SHIPPED / CANCELLED 以外)。
     */
    static final Set<String> ACTIVE_STATUSES = Set.of(
            "ORDERED", "PARTIAL_ALLOCATED", "ALLOCATED", "PICKING_COMPLETED", "INSPECTING");

    private static final String ERROR_CODE = "CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND";

    private final OutboundSlipRepository outboundSlipRepository;

    @Override
    public Optional<String> findBlockingReason(Long partnerId) {
        if (outboundSlipRepository.existsByPartnerIdAndStatusIn(partnerId, ACTIVE_STATUSES)) {
            return Optional.of(ERROR_CODE);
        }
        return Optional.empty();
    }
}
