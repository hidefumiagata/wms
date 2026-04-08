package com.wms.master.service.spi;

import java.util.Optional;

/**
 * 取引先の利用状況チェック SPI。
 *
 * <p>取引先を無効化する際、他モジュール (入荷/出荷など) 側で
 * 「処理中」状態の伝票が紐付いていないかを判定する。</p>
 *
 * <p>master モジュールはこのインターフェースのみを所有し、各業務モジュール
 * (inbound/outbound) 側で実装を提供することで、master → 業務モジュール
 * の依存（循環依存）を回避する (RULE-SVC-002)。</p>
 */
public interface PartnerUsageChecker {

    /**
     * 指定 partner が無効化不可な使用状態にある場合、対応する errorCode を返す。
     * 使用中でなければ {@link Optional#empty()}。
     *
     * @param partnerId 対象 partner の ID
     * @return 業務エラーコード (例: {@code CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND})、
     *         使用中でない場合は empty
     */
    Optional<String> findBlockingReason(Long partnerId);
}
