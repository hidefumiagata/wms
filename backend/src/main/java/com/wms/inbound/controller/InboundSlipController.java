package com.wms.inbound.controller;

import com.wms.generated.api.InboundApi;
import com.wms.generated.model.*;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.inbound.service.InboundSlipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@Slf4j
public class InboundSlipController implements InboundApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "plannedDate", "slipNumber", "status", "createdAt");

    private final InboundSlipService inboundSlipService;

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<InboundSlipSummaryPageResponse> listInboundSlips(
            Long warehouseId, String slipNumber, List<InboundSlipStatus> status,
            LocalDate plannedDateFrom, LocalDate plannedDateTo, Long partnerId,
            Integer page, Integer size, String sort) {

        List<String> statuses = status != null
                ? status.stream().map(InboundSlipStatus::getValue).toList()
                : null;

        Sort sortObj = parseSort(sort, "plannedDate");
        Page<InboundSlip> resultPage = inboundSlipService.search(
                warehouseId, slipNumber, statuses,
                plannedDateFrom, plannedDateTo, partnerId,
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toSummaryPageResponse(resultPage));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<InboundSlipDetail> getInboundSlip(Long id) {
        InboundSlip slip = inboundSlipService.findByIdWithLines(id);
        return ResponseEntity.ok(toDetail(slip));
    }

    // --- 後続Issueで実装 ---

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> createInboundSlip(
            CreateInboundSlipRequest createInboundSlipRequest) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<Void> deleteInboundSlip(Long id) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> confirmInboundSlip(Long id) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> cancelInboundSlip(Long id) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> inspectInboundSlip(
            Long id, InspectInboundRequest inspectInboundRequest) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> storeInboundSlip(
            Long id, StoreInboundRequest storeInboundRequest) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<InboundResultPageResponse> listInboundResults(
            Long warehouseId, LocalDate storedDateFrom, LocalDate storedDateTo,
            Long partnerId, String slipNumber, String productCode,
            Integer page, Integer size, String sort) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // --- Converters ---

    private InboundSlipSummaryPageResponse toSummaryPageResponse(Page<InboundSlip> page) {
        List<InboundSlipSummary> items = page.getContent().stream()
                .map(this::toSummary)
                .toList();
        return new InboundSlipSummaryPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private InboundSlipSummary toSummary(InboundSlip s) {
        long lineCount = inboundSlipService.countLinesBySlipId(s.getId());
        return new InboundSlipSummary()
                .id(s.getId())
                .slipNumber(s.getSlipNumber())
                .slipType(InboundSlipType.fromValue(s.getSlipType()))
                .warehouseCode(s.getWarehouseCode())
                .partnerCode(s.getPartnerCode())
                .partnerName(s.getPartnerName())
                .plannedDate(s.getPlannedDate())
                .status(InboundSlipStatus.fromValue(s.getStatus()))
                .lineCount((int) lineCount)
                .createdAt(s.getCreatedAt());
    }

    private InboundSlipDetail toDetail(InboundSlip s) {
        String createdByName = inboundSlipService.resolveUserName(s.getCreatedBy());

        List<InboundSlipLineDetail> lineDetails = s.getLines().stream()
                .map(this::toLineDetail)
                .toList();

        return new InboundSlipDetail()
                .id(s.getId())
                .slipNumber(s.getSlipNumber())
                .slipType(InboundSlipType.fromValue(s.getSlipType()))
                .transferSlipNumber(s.getTransferSlipNumber())
                .warehouseId(s.getWarehouseId())
                .warehouseCode(s.getWarehouseCode())
                .warehouseName(s.getWarehouseName())
                .partnerId(s.getPartnerId())
                .partnerCode(s.getPartnerCode())
                .partnerName(s.getPartnerName())
                .plannedDate(s.getPlannedDate())
                .status(InboundSlipStatus.fromValue(s.getStatus()))
                .note(s.getNote())
                .cancelledAt(s.getCancelledAt())
                .cancelledBy(s.getCancelledBy())
                .createdAt(s.getCreatedAt())
                .createdBy(s.getCreatedBy())
                .createdByName(createdByName)
                .updatedAt(s.getUpdatedAt())
                .updatedBy(s.getUpdatedBy())
                .lines(lineDetails);
    }

    private InboundSlipLineDetail toLineDetail(InboundSlipLine l) {
        Integer diffQty = l.getInspectedQty() != null
                ? l.getInspectedQty() - l.getPlannedQty()
                : null;

        return new InboundSlipLineDetail()
                .id(l.getId())
                .lineNo(l.getLineNo())
                .productId(l.getProductId())
                .productCode(l.getProductCode())
                .productName(l.getProductName())
                .unitType(UnitType.fromValue(l.getUnitType()))
                .plannedQty(l.getPlannedQty())
                .inspectedQty(l.getInspectedQty())
                .diffQty(diffQty)
                .lotNumber(l.getLotNumber())
                .expiryDate(l.getExpiryDate())
                .putawayLocationId(l.getPutawayLocationId())
                .putawayLocationCode(l.getPutawayLocationCode())
                .lineStatus(InboundLineStatus.fromValue(l.getLineStatus()))
                .inspectedAt(l.getInspectedAt())
                .inspectedBy(l.getInspectedBy())
                .storedAt(l.getStoredAt())
                .storedBy(l.getStoredBy());
    }

    private Sort parseSort(String sort, String defaultProperty) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, defaultProperty);
        }
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : defaultProperty;
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
