package com.wms.inbound.service;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.repository.InboundSlipLineRepository;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.wms.shared.util.LikeEscapeUtil.escape;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InboundSlipService {

    private final InboundSlipRepository inboundSlipRepository;
    private final InboundSlipLineRepository inboundSlipLineRepository;
    private final WarehouseService warehouseService;
    private final UserRepository userRepository;

    public Page<InboundSlip> search(Long warehouseId, String slipNumber,
                                     List<String> statuses, LocalDate plannedDateFrom,
                                     LocalDate plannedDateTo, Long partnerId,
                                     Pageable pageable) {
        warehouseService.findById(warehouseId);

        String escapedSlipNumber = slipNumber != null ? escape(slipNumber) : null;

        log.debug("InboundSlip search: warehouseId={}, slipNumber={}, statuses={}", warehouseId, slipNumber, statuses);
        return inboundSlipRepository.search(
                warehouseId, escapedSlipNumber, statuses,
                plannedDateFrom, plannedDateTo, partnerId, pageable);
    }

    public InboundSlip findById(Long id) {
        return inboundSlipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INBOUND_SLIP_NOT_FOUND",
                        "入荷伝票が見つかりません (id=" + id + ")"));
    }

    public InboundSlip findByIdWithLines(Long id) {
        return inboundSlipRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INBOUND_SLIP_NOT_FOUND",
                        "入荷伝票が見つかりません (id=" + id + ")"));
    }

    public long countLinesBySlipId(Long slipId) {
        return inboundSlipLineRepository.countByInboundSlipId(slipId);
    }

    public String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(u -> u.getFullName())
                .orElse(null);
    }
}
