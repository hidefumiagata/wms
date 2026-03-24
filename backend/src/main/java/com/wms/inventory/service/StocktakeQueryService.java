package com.wms.inventory.service;

import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.inventory.repository.StocktakeLineRepository;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StocktakeQueryService {

    private final StocktakeHeaderRepository stocktakeHeaderRepository;
    private final StocktakeLineRepository stocktakeLineRepository;
    private final WarehouseService warehouseService;

    public Page<StocktakeHeader> search(Long warehouseId, String status,
                                         LocalDate dateFrom, LocalDate dateTo,
                                         Pageable pageable) {
        warehouseService.findById(warehouseId);

        OffsetDateTime from = dateFrom != null
                ? dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime to = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) : null;

        log.debug("Stocktake search: warehouseId={}, status={}, dateFrom={}, dateTo={}",
                warehouseId, status, dateFrom, dateTo);

        return stocktakeHeaderRepository.search(warehouseId, status, from, to, pageable);
    }

    public StocktakeHeader findById(Long id) {
        return stocktakeHeaderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "STOCKTAKE_NOT_FOUND", "棚卸が見つかりません (id=" + id + ")"));
    }

    public StocktakeHeader findByIdWithLines(Long id) {
        return stocktakeHeaderRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "STOCKTAKE_NOT_FOUND", "棚卸が見つかりません (id=" + id + ")"));
    }

    public long countTotalLines(Long headerId) {
        return stocktakeLineRepository.countByHeaderId(headerId);
    }

    public long countCountedLines(Long headerId) {
        return stocktakeLineRepository.countCountedByHeaderId(headerId);
    }

    public Page<com.wms.inventory.entity.StocktakeLine> searchLines(Long headerId, Boolean isCounted,
                                                                      String locationCodePrefix, Pageable pageable) {
        return stocktakeLineRepository.searchByHeader(headerId, isCounted, locationCodePrefix, pageable);
    }
}
