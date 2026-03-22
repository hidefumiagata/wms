package com.wms.inbound.service;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InboundSlipService {

    private final InboundSlipRepository inboundSlipRepository;
    private final WarehouseService warehouseService;

    public Page<InboundSlip> search(Long warehouseId, String slipNumber,
                                     List<String> statuses, LocalDate plannedDateFrom,
                                     LocalDate plannedDateTo, Long partnerId,
                                     Pageable pageable) {
        // 倉庫存在チェック
        warehouseService.findById(warehouseId);

        return inboundSlipRepository.search(
                warehouseId, slipNumber, statuses,
                plannedDateFrom, plannedDateTo, partnerId, pageable);
    }

    public InboundSlip findById(Long id) {
        return inboundSlipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INBOUND_SLIP_NOT_FOUND",
                        "入荷伝票が見つかりません (id=" + id + ")"));
    }
}
