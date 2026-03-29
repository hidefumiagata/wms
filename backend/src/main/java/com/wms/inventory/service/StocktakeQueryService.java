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

import com.wms.shared.util.LikeEscapeUtil;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StocktakeQueryService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final StocktakeHeaderRepository stocktakeHeaderRepository;
    private final StocktakeLineRepository stocktakeLineRepository;
    private final WarehouseService warehouseService;

    public Page<StocktakeHeader> search(Long warehouseId, String status,
                                         LocalDate dateFrom, LocalDate dateTo,
                                         String stocktakeNumber, Long buildingId,
                                         Pageable pageable) {
        warehouseService.findById(warehouseId);

        OffsetDateTime from = dateFrom != null
                ? dateFrom.atStartOfDay(JST).toOffsetDateTime() : null;
        OffsetDateTime to = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay(JST).toOffsetDateTime() : null;

        String escapedNumber = LikeEscapeUtil.escape(stocktakeNumber);

        log.debug("Stocktake search: warehouseId={}, status={}, dateFrom={}, dateTo={}, stocktakeNumber={}, buildingId={}",
                warehouseId, status, dateFrom, dateTo, stocktakeNumber, buildingId);

        return stocktakeHeaderRepository.search(warehouseId, status, from, to,
                escapedNumber, buildingId, pageable);
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

    public Map<Long, Long> countTotalLinesByHeaderIds(Set<Long> headerIds) {
        if (headerIds.isEmpty()) {
            return Map.of();
        }
        return stocktakeLineRepository.countTotalLinesByHeaderIds(headerIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }

    public Map<Long, Long> countCountedLinesByHeaderIds(Set<Long> headerIds) {
        if (headerIds.isEmpty()) {
            return Map.of();
        }
        return stocktakeLineRepository.countCountedLinesByHeaderIds(headerIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }

    public Page<com.wms.inventory.entity.StocktakeLine> searchLines(Long headerId, Boolean isCounted,
                                                                      String locationCodePrefix, Pageable pageable) {
        return stocktakeLineRepository.searchByHeader(headerId, isCounted, locationCodePrefix, pageable);
    }
}
