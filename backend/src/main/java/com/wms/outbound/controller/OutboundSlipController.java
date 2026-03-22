package com.wms.outbound.controller;

import com.wms.generated.api.OutboundApi;
import com.wms.generated.model.*;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.service.OutboundSlipService;
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
public class OutboundSlipController implements OutboundApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "plannedDate", "slipNumber", "status", "createdAt");

    private final OutboundSlipService outboundSlipService;

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<OutboundSlipPageResponse> listOutboundSlips(
            Long warehouseId, String slipNumber,
            LocalDate plannedDateFrom, LocalDate plannedDateTo,
            Long partnerId, List<OutboundSlipStatus> status,
            Integer page, Integer size, String sort) {

        List<String> statuses = status != null
                ? status.stream().map(OutboundSlipStatus::getValue).toList()
                : null;

        Sort sortObj = parseSort(sort, "plannedDate", ALLOWED_SORT_PROPERTIES);
        Page<OutboundSlip> resultPage = outboundSlipService.search(
                warehouseId, slipNumber, statuses,
                plannedDateFrom, plannedDateTo, partnerId,
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toPageResponse(resultPage));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<OutboundSlipDetail> getOutboundSlip(Long id) {
        OutboundSlip slip = outboundSlipService.findByIdWithLines(id);
        return ResponseEntity.ok(toDetail(slip));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<OutboundSlipDetail> createOutboundSlip(
            CreateOutboundSlipRequest createOutboundSlipRequest) {
        OutboundSlip created = outboundSlipService.create(createOutboundSlipRequest);
        OutboundSlip withLines = outboundSlipService.findByIdWithLines(created.getId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(toDetail(withLines));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<Void> deleteOutboundSlip(Long id) {
        outboundSlipService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<OutboundCancelResponse> cancelOutboundSlip(
            Long id, CancelOutboundRequest cancelOutboundRequest) {
        OutboundSlip cancelled = outboundSlipService.cancel(id, cancelOutboundRequest);
        return ResponseEntity.ok(toCancelResponse(cancelled));
    }

    // --- Stub implementations for picking/inspect/ship (to be implemented in subsequent PRs) ---

    @Override
    public ResponseEntity<PickingInstructionPageResponse> listPickingInstructions(
            Long warehouseId, String instructionNumber, List<PickingInstructionStatus> status,
            LocalDate createdDateFrom, LocalDate createdDateTo,
            Integer page, Integer size, String sort) {
        throw new UnsupportedOperationException("ピッキング指示一覧は後続PRで実装予定");
    }

    @Override
    public ResponseEntity<PickingInstructionDetail> createPickingInstruction(
            CreatePickingInstructionRequest createPickingInstructionRequest) {
        throw new UnsupportedOperationException("ピッキング指示作成は後続PRで実装予定");
    }

    @Override
    public ResponseEntity<PickingInstructionDetail> getPickingInstruction(Long id) {
        throw new UnsupportedOperationException("ピッキング指示詳細は後続PRで実装予定");
    }

    @Override
    public ResponseEntity<PickingInstructionDetail> completePickingInstruction(
            Long id, CompletePickingRequest completePickingRequest) {
        throw new UnsupportedOperationException("ピッキング完了は後続PRで実装予定");
    }

    @Override
    public ResponseEntity<OutboundSlipDetail> inspectOutboundSlip(
            Long id, InspectOutboundRequest inspectOutboundRequest) {
        throw new UnsupportedOperationException("出荷検品は後続PRで実装予定");
    }

    @Override
    public ResponseEntity<OutboundSlipDetail> shipOutboundSlip(
            Long id, ShipOutboundRequest shipOutboundRequest) {
        throw new UnsupportedOperationException("出荷完了は後続PRで実装予定");
    }

    // --- Converters ---

    private OutboundSlipPageResponse toPageResponse(Page<OutboundSlip> page) {
        List<OutboundSlipSummary> items = page.getContent().stream()
                .map(this::toSummary)
                .toList();
        return new OutboundSlipPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private OutboundSlipSummary toSummary(OutboundSlip s) {
        long lineCount = outboundSlipService.countLinesBySlipId(s.getId());
        return new OutboundSlipSummary()
                .id(s.getId())
                .slipNumber(s.getSlipNumber())
                .slipType(OutboundSlipType.fromValue(s.getSlipType()))
                .partnerName(s.getPartnerName())
                .plannedDate(s.getPlannedDate())
                .status(OutboundSlipStatus.fromValue(s.getStatus()))
                .lineCount((int) lineCount)
                .createdAt(s.getCreatedAt());
    }

    private OutboundSlipDetail toDetail(OutboundSlip s) {
        List<OutboundSlipLineDetail> lineDetails = s.getLines().stream()
                .map(this::toLineDetail)
                .toList();

        return new OutboundSlipDetail()
                .id(s.getId())
                .slipNumber(s.getSlipNumber())
                .slipType(OutboundSlipType.fromValue(s.getSlipType()))
                .transferSlipNumber(s.getTransferSlipNumber())
                .warehouseId(s.getWarehouseId())
                .warehouseCode(s.getWarehouseCode())
                .warehouseName(s.getWarehouseName())
                .partnerId(s.getPartnerId())
                .partnerCode(s.getPartnerCode())
                .partnerName(s.getPartnerName())
                .plannedDate(s.getPlannedDate())
                .carrier(s.getCarrier())
                .trackingNumber(s.getTrackingNumber())
                .status(OutboundSlipStatus.fromValue(s.getStatus()))
                .note(s.getNote())
                .shippedAt(s.getShippedAt())
                .shippedBy(s.getShippedBy())
                .cancelledAt(s.getCancelledAt())
                .cancelledBy(s.getCancelledBy())
                .createdAt(s.getCreatedAt())
                .createdBy(s.getCreatedBy())
                .updatedAt(s.getUpdatedAt())
                .updatedBy(s.getUpdatedBy())
                .lines(lineDetails);
    }

    private OutboundSlipLineDetail toLineDetail(OutboundSlipLine l) {
        return new OutboundSlipLineDetail()
                .id(l.getId())
                .lineNo(l.getLineNo())
                .productId(l.getProductId())
                .productCode(l.getProductCode())
                .productName(l.getProductName())
                .unitType(UnitType.fromValue(l.getUnitType()))
                .orderedQty(l.getOrderedQty())
                .inspectedQty(l.getInspectedQty())
                .shippedQty(l.getShippedQty())
                .lineStatus(OutboundLineStatus.fromValue(l.getLineStatus()));
    }

    private OutboundCancelResponse toCancelResponse(OutboundSlip s) {
        return new OutboundCancelResponse()
                .id(s.getId())
                .slipNumber(s.getSlipNumber())
                .status(OutboundSlipStatus.fromValue(s.getStatus()))
                .cancelledAt(s.getCancelledAt())
                .cancelledBy(s.getCancelledBy());
    }

    private Sort parseSort(String sort, String defaultProperty, Set<String> allowedProperties) {
        String[] parts = sort.split(",");
        String property = allowedProperties.contains(parts[0])
                ? parts[0] : defaultProperty;
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
