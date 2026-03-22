package com.wms.master.service;

import com.wms.master.entity.Partner;
import static com.wms.shared.util.LikeEscapeUtil.escape;
import com.wms.master.entity.PartnerType;
import com.wms.master.repository.PartnerRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partnerRepository;

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
        if (!isActive) {
            // TODO: 入荷予定テーブル実装後に処理中入荷予定の存在チェックを追加
            //       ステータス: PLANNED, CONFIRMED, INSPECTING → CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND (422)
            // TODO: 受注テーブル実装後に処理中受注の存在チェックを追加
            //       ステータス: PENDING, ALLOCATED, PICKING, INSPECTING → CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND (422)
        }
        if (partner.getIsActive().equals(isActive)) {
            log.info("Partner toggleActive no-op: id={}, isActive={}", id, isActive);
            return partner;
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
