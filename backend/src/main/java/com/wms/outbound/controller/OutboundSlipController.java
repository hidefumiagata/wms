package com.wms.outbound.controller;

import com.wms.generated.api.OutboundApi;
import com.wms.generated.model.*;
import com.wms.master.entity.Area;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
import com.wms.master.service.WarehouseService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.entity.PickingInstruction;
import com.wms.outbound.entity.PickingInstructionLine;
import com.wms.outbound.service.OutboundSlipService;
import com.wms.outbound.service.PickingService;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OutboundSlipController implements OutboundApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "plannedDate", "slipNumber", "status", "createdAt");

    private static final Set<String> PICKING_SORT_PROPERTIES = Set.of(
            "createdAt", "instructionNumber", "status");

    private final OutboundSlipService outboundSlipService;
    private final PickingService pickingService;
    private final WarehouseService warehouseService;
    private final AreaService areaService;
    private final UserRepository userRepository;

    // ==================== Outbound Slip APIs ====================

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

    // ==================== Picking APIs ====================

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<PickingInstructionPageResponse> listPickingInstructions(
            Long warehouseId, String instructionNumber, List<PickingInstructionStatus> status,
            LocalDate createdDateFrom, LocalDate createdDateTo,
            Integer page, Integer size, String sort) {

        List<String> statuses = status != null
                ? status.stream().map(PickingInstructionStatus::getValue).toList()
                : null;

        Sort sortObj = parseSort(sort, "createdAt", PICKING_SORT_PROPERTIES);
        Page<PickingInstruction> resultPage = pickingService.search(
                warehouseId, instructionNumber, statuses,
                createdDateFrom, createdDateTo,
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toPickingPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<PickingInstructionDetail> createPickingInstruction(
            CreatePickingInstructionRequest createPickingInstructionRequest) {
        PickingInstruction created = pickingService.createPickingInstruction(createPickingInstructionRequest);
        PickingInstruction withLines = pickingService.findByIdWithLines(created.getId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(toPickingDetail(withLines));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<PickingInstructionDetail> getPickingInstruction(Long id) {
        PickingInstruction instruction = pickingService.findByIdWithLines(id);
        return ResponseEntity.ok(toPickingDetail(instruction));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<PickingInstructionDetail> completePickingInstruction(
            Long id, CompletePickingRequest completePickingRequest) {
        PickingInstruction updated = pickingService.completePickingInstruction(id, completePickingRequest);
        PickingInstruction withLines = pickingService.findByIdWithLines(updated.getId());
        return ResponseEntity.ok(toPickingDetail(withLines));
    }

    // ==================== Inspect / Ship APIs ====================

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<OutboundSlipDetail> inspectOutboundSlip(
            Long id, InspectOutboundRequest inspectOutboundRequest) {
        OutboundSlip inspected = outboundSlipService.inspect(id, inspectOutboundRequest);
        OutboundSlip withLines = outboundSlipService.findByIdWithLines(inspected.getId());
        return ResponseEntity.ok(toDetail(withLines));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<OutboundSlipDetail> shipOutboundSlip(
            Long id, ShipOutboundRequest shipOutboundRequest) {
        OutboundSlip shipped = outboundSlipService.ship(id, shipOutboundRequest);
        OutboundSlip withLines = outboundSlipService.findByIdWithLines(shipped.getId());
        return ResponseEntity.ok(toDetail(withLines));
    }

    // ==================== Outbound Slip Converters ====================

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

    // ==================== Picking Converters ====================

    private PickingInstructionPageResponse toPickingPageResponse(Page<PickingInstruction> page) {
        // Collect warehouse and area IDs for batch lookup
        Set<Long> warehouseIds = page.getContent().stream()
                .map(PickingInstruction::getWarehouseId).collect(Collectors.toSet());
        Set<Long> areaIds = page.getContent().stream()
                .map(PickingInstruction::getAreaId).filter(a -> a != null).collect(Collectors.toSet());
        Set<Long> userIds = page.getContent().stream()
                .map(PickingInstruction::getCreatedBy).collect(Collectors.toSet());

        Map<Long, Warehouse> warehouses = warehouseService.findByIds(warehouseIds);
        Map<Long, Area> areas = areaIds.isEmpty() ? Map.of() : areaService.findByIds(areaIds);
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<PickingInstructionSummary> items = page.getContent().stream()
                .map(p -> toPickingSummary(p, warehouses, areas, users))
                .toList();

        return new PickingInstructionPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private PickingInstructionSummary toPickingSummary(PickingInstruction p,
                                                        Map<Long, Warehouse> warehouses,
                                                        Map<Long, Area> areas,
                                                        Map<Long, User> users) {
        Warehouse wh = warehouses.get(p.getWarehouseId());
        Area area = p.getAreaId() != null ? areas.get(p.getAreaId()) : null;
        User user = users.get(p.getCreatedBy());
        long lineCount = pickingService.countLinesByInstructionId(p.getId());

        return new PickingInstructionSummary()
                .id(p.getId())
                .instructionNumber(p.getInstructionNumber())
                .warehouseId(p.getWarehouseId())
                .warehouseName(wh != null ? wh.getWarehouseName() : null)
                .areaId(p.getAreaId())
                .areaName(area != null ? area.getAreaName() : null)
                .status(PickingInstructionStatus.fromValue(p.getStatus()))
                .lineCount((int) lineCount)
                .createdAt(p.getCreatedAt())
                .createdByName(user != null ? user.getFullName() : null);
    }

    private PickingInstructionDetail toPickingDetail(PickingInstruction p) {
        Warehouse wh = warehouseService.findById(p.getWarehouseId());
        Area area = p.getAreaId() != null ? areaService.findById(p.getAreaId()) : null;
        User user = userRepository.findById(p.getCreatedBy()).orElse(null);

        // 出荷伝票番号のマップを構築
        Set<Long> slipLineIds = p.getLines().stream()
                .map(PickingInstructionLine::getOutboundSlipLineId)
                .collect(Collectors.toSet());
        Map<Long, String> slipNumberByLineId = buildSlipNumberMap(slipLineIds);

        List<PickingInstructionLineDetail> lineDetails = p.getLines().stream()
                .map(l -> toPickingLineDetail(l, slipNumberByLineId))
                .toList();

        return new PickingInstructionDetail()
                .id(p.getId())
                .instructionNumber(p.getInstructionNumber())
                .warehouseId(p.getWarehouseId())
                .warehouseName(wh.getWarehouseName())
                .areaId(p.getAreaId())
                .areaName(area != null ? area.getAreaName() : null)
                .status(PickingInstructionStatus.fromValue(p.getStatus()))
                .lines(lineDetails)
                .createdAt(p.getCreatedAt())
                .createdBy(p.getCreatedBy())
                .createdByName(user != null ? user.getFullName() : null)
                .completedAt(p.getCompletedAt())
                .completedBy(p.getCompletedBy());
    }

    private PickingInstructionLineDetail toPickingLineDetail(PickingInstructionLine l,
                                                              Map<Long, String> slipNumberByLineId) {
        return new PickingInstructionLineDetail()
                .id(l.getId())
                .lineNo(l.getLineNo())
                .outboundSlipNumber(slipNumberByLineId.getOrDefault(l.getOutboundSlipLineId(), null))
                .outboundSlipLineId(l.getOutboundSlipLineId())
                .locationId(l.getLocationId())
                .locationCode(l.getLocationCode())
                .productCode(l.getProductCode())
                .productName(l.getProductName())
                .unitType(UnitType.fromValue(l.getUnitType()))
                .lotNumber(l.getLotNumber())
                .expiryDate(l.getExpiryDate())
                .qtyToPick(l.getQtyToPick())
                .qtyPicked(l.getQtyPicked() == 0 ? null : l.getQtyPicked())
                .lineStatus(PickingLineStatus.fromValue(l.getLineStatus()));
    }

    private Map<Long, String> buildSlipNumberMap(Set<Long> slipLineIds) {
        // For each outbound_slip_line_id, find the slip_number
        // This is done by querying outbound slips containing these line IDs
        return slipLineIds.stream()
                .collect(Collectors.toMap(
                        lineId -> lineId,
                        lineId -> {
                            try {
                                OutboundSlip slip = outboundSlipService.findBySlipLineId(lineId);
                                return slip.getSlipNumber();
                            } catch (Exception e) {
                                return "";
                            }
                        }));
    }

    // ==================== Common Utilities ====================

    private Sort parseSort(String sort, String defaultProperty, Set<String> allowedProperties) {
        String[] parts = sort.split(",");
        String property = allowedProperties.contains(parts[0])
                ? parts[0] : defaultProperty;
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
