package com.wms.master.service;

import com.wms.master.entity.Partner;
import static com.wms.shared.util.LikeEscapeUtil.escape;
import com.wms.master.repository.PartnerRepository;
import com.wms.master.service.spi.PartnerUsageChecker;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partnerRepository;
    // @Order(10) InboundPartnerUsageChecker → @Order(20) OutboundPartnerUsageChecker の順で
    // Spring DI が List を注入する (API-03 §4 の業務フロー契約)
    private final List<PartnerUsageChecker> partnerUsageCheckers;

    public Page<Partner> search(String partnerCode, String partnerName,
                                String partnerType, Boolean isActive, Pageable pageable) {
        return partnerRepository.search(escape(partnerCode), escape(partnerName), partnerType, isActive, pageable);
    }

    private static final int FIND_ALL_SIMPLE_LIMIT = 1000;

    public List<Partner> findAllSimple(Boolean isActive) {
        List<Partner> all = partnerRepository.findAllSimple(isActive);
        if (all.size() > FIND_ALL_SIMPLE_LIMIT) {
            log.warn("findAllSimple: 取引先件数が上限を超過しています (count={}, limit={})", all.size(), FIND_ALL_SIMPLE_LIMIT);
            return all.subList(0, FIND_ALL_SIMPLE_LIMIT);
        }
        return all;
    }

    public Partner findById(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "PARTNER_NOT_FOUND", "取引先", id));
    }

    @Transactional
    public Partner create(Partner partner) {
        if (partnerRepository.existsByPartnerCode(partner.getPartnerCode())) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "取引先コードが既に存在します: " + partner.getPartnerCode());
        }
        try {
            Partner created = partnerRepository.save(partner);
            log.info("Partner created: code={}, type={}", created.getPartnerCode(), created.getPartnerType());
            return created;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "取引先コードが既に存在します: " + partner.getPartnerCode());
        }
    }

    @Transactional
    public Partner update(UpdatePartnerCommand cmd) {
        Partner partner = findById(cmd.id());
        if (!partner.getVersion().equals(cmd.version())) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + cmd.id() + ")");
        }
        partner.setPartnerName(cmd.partnerName());
        partner.setPartnerNameKana(cmd.partnerNameKana());
        partner.setPartnerType(cmd.partnerType());
        partner.setAddress(cmd.address());
        partner.setPhone(cmd.phone());
        partner.setContactPerson(cmd.contactPerson());
        partner.setEmail(cmd.email());
        partner.setVersion(cmd.version());
        try {
            Partner saved = partnerRepository.save(partner);
            log.info("Partner updated: id={}, name={}", cmd.id(), cmd.partnerName());
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + cmd.id() + ")");
        }
    }

    @Transactional
    public Partner toggleActive(Long id, boolean isActive, Integer version) {
        Partner partner = findById(id);
        if (partner.getIsActive().equals(isActive)) {
            log.info("Partner toggleActive no-op: id={}, isActive={}", id, isActive);
            return partner;
        }
        if (!isActive) {
            // 無効化前の業務制約チェック (API-03 BR-001)
            // 各 PartnerUsageChecker (inbound/outbound 等) に「処理中伝票」があれば
            // BusinessRuleViolationException を 422 で返す
            for (PartnerUsageChecker checker : partnerUsageCheckers) {
                Optional<String> reason = checker.findBlockingReason(id);
                if (reason.isPresent()) {
                    log.warn("Partner deactivation blocked: id={}, code={}", id, reason.get());
                    throw new BusinessRuleViolationException(reason.get(),
                            "処理中の伝票が存在するため取引先を無効化できません (id=" + id + ")");
                }
            }
        }
        if (isActive) {
            partner.activate();
        } else {
            partner.deactivate();
        }
        partner.setVersion(version);
        try {
            Partner saved = partnerRepository.save(partner);
            log.info("Partner toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    public boolean existsByCode(String partnerCode) {
        return partnerRepository.existsByPartnerCode(partnerCode);
    }
}
