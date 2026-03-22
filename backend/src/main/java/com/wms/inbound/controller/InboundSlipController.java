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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@Slf4j
public class InboundSlipController implements InboundApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "plannedDate", "slipNumber", "status", "createdAt");

    private static final Set<String> ALLOWED_RESULT_SORT_PROPERTIES = Set.of(
            "storedAt", "inboundSlip.slipNumber", "productCode");

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

        Sort sortObj = parseSort(sort, "plannedDate", ALLOWED_SORT_PROPERTIES);
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

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> createInboundSlip(
            CreateInboundSlipRequest createInboundSlipRequest) {
        InboundSlip created = inboundSlipService.create(createInboundSlipRequest);
        // Re-fetch with lines to ensure audit fields (createdAt etc.) are populated after commit
        InboundSlip withLines = inboundSlipService.findByIdWithLines(created.getId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(toDetail(withLines));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<Void> deleteInboundSlip(Long id) {
        inboundSlipService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> confirmInboundSlip(Long id) {
        InboundSlip confirmed = inboundSlipService.confirm(id);
        return ResponseEntity.ok(toDetail(confirmed));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> cancelInboundSlip(Long id) {
        InboundSlip cancelled = inboundSlipService.cancel(id);
        return ResponseEntity.ok(toDetail(cancelled));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> inspectInboundSlip(
            Long id, InspectInboundRequest inspectInboundRequest) {
        InboundSlip inspected = inboundSlipService.inspect(id, inspectInboundRequest);
        return ResponseEntity.ok(toDetail(inspected));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<InboundSlipDetail> storeInboundSlip(
            Long id, StoreInboundRequest storeInboundRequest) {
        InboundSlip stored = inboundSlipService.store(id, storeInboundRequest);
        return ResponseEntity.ok(toDetail(stored));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<InboundResultPageResponse> listInboundResults(
            Long warehouseId, LocalDate storedDateFrom, LocalDate storedDateTo,
            Long partnerId, String slipNumber, String productCode,
            Integer page, Integer size, String sort) {

        Sort sortObj = parseSort(sort, "storedAt", ALLOWED_RESULT_SORT_PROPERTIES);
        Page<InboundSlipLine> resultPage = inboundSlipService.findResults(
                warehouseId, storedDateFrom, storedDateTo, partnerId,
                slipNumber, productCode,
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toResultPageResponse(resultPage));
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

    private InboundResultPageResponse toResultPageResponse(Page<InboundSlipLine> page) {
        List<InboundResultItem> items = page.getContent().stream()
                .map(this::toResultItem)
                .toList();
        return new InboundResultPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private InboundResultItem toResultItem(InboundSlipLine line) {
        InboundSlip slip = line.getInboundSlip();
        Integer diffQty = line.getInspectedQty() != null
                ? line.getInspectedQty() - line.getPlannedQty()
                : null;
        String storedByName = inboundSlipService.resolveUserName(line.getStoredBy());

        return new InboundResultItem()
                .slipId(slip.getId())
                .slipNumber(slip.getSlipNumber())
                .slipType(InboundSlipType.fromValue(slip.getSlipType()))
                .partnerCode(slip.getPartnerCode())
                .partnerName(slip.getPartnerName())
                .lineId(line.getId())
                .lineNo(line.getLineNo())
                .productCode(line.getProductCode())
                .productName(line.getProductName())
                .unitType(UnitType.fromValue(line.getUnitType()))
                .lotNumber(line.getLotNumber())
                .expiryDate(line.getExpiryDate())
                .plannedQty(line.getPlannedQty())
                .inspectedQty(line.getInspectedQty())
                .diffQty(diffQty)
                .locationCode(line.getPutawayLocationCode())
                .storedAt(line.getStoredAt())
                .storedByName(storedByName);
    }

    // sort parameter always has a default value from OpenAPI generated interface
    private Sort parseSort(String sort, String defaultProperty, Set<String> allowedProperties) {
        String[] parts = sort.split(",");
        String property = allowedProperties.contains(parts[0])
                ? parts[0] : defaultProperty;
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
