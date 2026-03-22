package com.wms.allocation.controller;

import com.wms.allocation.entity.UnpackInstruction;
import com.wms.allocation.service.AllocationService;
import com.wms.allocation.service.AllocationService.*;
import com.wms.generated.api.AllocationApi;
import com.wms.generated.model.*;
import com.wms.outbound.entity.OutboundSlip;
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
public class AllocationController implements AllocationApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "plannedDate", "slipNumber", "status", "createdAt");

    private final AllocationService allocationService;

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<AllocationOrderPageResponse> getAllocationOrders(
            Long warehouseId, List<String> status,
            LocalDate shippingDateFrom, LocalDate shippingDateTo,
            String partnerName,
            Integer page, Integer size, String sort) {

        Sort sortObj = parseSort(sort, "plannedDate", ALLOWED_SORT_PROPERTIES);
        Page<OutboundSlip> resultPage = allocationService.searchOrders(
                warehouseId, status,
                shippingDateFrom, shippingDateTo,
                partnerName,
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toAllocationOrderPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<AllocatedOrderPageResponse> getAllocatedOrders(
            Integer page, Integer size, String sort) {

        Sort sortObj = parseSort(sort, "plannedDate", ALLOWED_SORT_PROPERTIES);
        Page<OutboundSlip> resultPage = allocationService.searchAllocatedOrders(
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toAllocatedOrderPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<UnpackInstructionPageResponse> getUnpackInstructions(
            Long outboundSlipId, String status, Integer page, Integer size) {

        Page<UnpackInstruction> resultPage = allocationService.searchUnpackInstructions(
                outboundSlipId, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return ResponseEntity.ok(toUnpackInstructionPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<AllocationExecutionResult> executeAllocation(
            ExecuteAllocationRequest executeAllocationRequest) {

        AllocationResult result = allocationService.executeAllocation(executeAllocationRequest);
        return ResponseEntity.ok(toExecutionResult(result));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<AllocationReleaseResult> releaseAllocation(
            ReleaseAllocationRequest releaseAllocationRequest) {

        AllocationReleaseInfo result = allocationService.releaseAllocation(releaseAllocationRequest);
        return ResponseEntity.ok(toReleaseResult(result));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<UnpackCompletionResult> completeUnpackInstruction(Long id) {
        UnpackCompletionInfo result = allocationService.completeUnpackInstruction(id);
        return ResponseEntity.ok(toUnpackCompletionResult(result));
    }

    // --- Converters ---

    private AllocationOrderPageResponse toAllocationOrderPageResponse(Page<OutboundSlip> page) {
        List<AllocationOrderSummary> items = page.getContent().stream()
                .map(this::toAllocationOrderSummary)
                .toList();
        return new AllocationOrderPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private AllocationOrderSummary toAllocationOrderSummary(OutboundSlip s) {
        long lineCount = allocationService.countLinesBySlipId(s.getId());
        return new AllocationOrderSummary()
                .id(s.getId())
                .slipNumber(s.getSlipNumber())
                .plannedDate(s.getPlannedDate())
                .partnerName(s.getPartnerName())
                .lineCount((int) lineCount)
                .status(AllocationOrderSummary.StatusEnum.fromValue(s.getStatus()));
    }

    private AllocatedOrderPageResponse toAllocatedOrderPageResponse(Page<OutboundSlip> page) {
        List<AllocatedOrderSummary> items = page.getContent().stream()
                .map(this::toAllocatedOrderSummary)
                .toList();
        return new AllocatedOrderPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private AllocatedOrderSummary toAllocatedOrderSummary(OutboundSlip s) {
        long lineCount = allocationService.countLinesBySlipId(s.getId());
        long allocatedLineCount = allocationService.countAllocatedLinesBySlipId(s.getId());
        return new AllocatedOrderSummary()
                .id(s.getId())
                .slipNumber(s.getSlipNumber())
                .plannedDate(s.getPlannedDate())
                .partnerName(s.getPartnerName())
                .lineCount((int) lineCount)
                .allocatedLineCount((int) allocatedLineCount)
                .status(AllocatedOrderSummary.StatusEnum.fromValue(s.getStatus()));
    }

    private UnpackInstructionPageResponse toUnpackInstructionPageResponse(Page<UnpackInstruction> page) {
        List<UnpackInstructionSummary> items = page.getContent().stream()
                .map(this::toUnpackInstructionSummary)
                .toList();
        return new UnpackInstructionPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private UnpackInstructionSummary toUnpackInstructionSummary(UnpackInstruction u) {
        return new UnpackInstructionSummary()
                .id(u.getId())
                .outboundSlipId(u.getOutboundSlipId())
                .fromUnitType(u.getFromUnitType())
                .toUnitType(u.getToUnitType())
                .quantity(u.getFromQty())
                .status(UnpackInstructionSummary.StatusEnum.fromValue(u.getStatus()))
                .createdAt(u.getCreatedAt());
    }

    private AllocationExecutionResult toExecutionResult(AllocationResult result) {
        List<AllocatedSlipDetail> allocatedSlips = result.allocatedSlips().stream()
                .map(s -> {
                    List<AllocatedLineDetail> lines = s.allocatedLines().stream()
                            .map(l -> new AllocatedLineDetail()
                                    .lineNo(l.lineNo())
                                    .productCode(l.productCode())
                                    .productName(l.productName())
                                    .orderedQty(l.orderedQty())
                                    .allocatedQty(l.allocatedQty()))
                            .toList();
                    return new AllocatedSlipDetail()
                            .outboundSlipId(s.outboundSlipId())
                            .slipNumber(s.slipNumber())
                            .status(AllocatedSlipDetail.StatusEnum.fromValue(s.status()))
                            .allocatedLines(lines);
                })
                .toList();

        List<UnpackInstructionItem> unpackItems = result.unpackInstructions().stream()
                .map(u -> new UnpackInstructionItem()
                        .id(u.id())
                        .productCode(u.productCode())
                        .productName(u.productName())
                        .fromUnitType(u.fromUnitType())
                        .toUnitType(u.toUnitType())
                        .quantity(u.quantity())
                        .status(UnpackInstructionItem.StatusEnum.INSTRUCTED))
                .toList();

        List<UnallocatedLineDetail> unallocatedLines = result.unallocatedLines().stream()
                .map(u -> new UnallocatedLineDetail()
                        .outboundSlipId(u.outboundSlipId())
                        .slipNumber(u.slipNumber())
                        .lineNo(u.lineNo())
                        .productCode(u.productCode())
                        .productName(u.productName())
                        .shortageQty(u.shortageQty()))
                .toList();

        return new AllocationExecutionResult()
                .allocatedCount(result.allocatedCount())
                .allocatedSlips(allocatedSlips)
                .unpackInstructions(unpackItems)
                .unallocatedLines(unallocatedLines);
    }

    private AllocationReleaseResult toReleaseResult(AllocationReleaseInfo result) {
        List<ReleasedSlipDetail> releasedSlips = result.releasedSlips().stream()
                .map(s -> new ReleasedSlipDetail()
                        .outboundSlipId(s.outboundSlipId())
                        .slipNumber(s.slipNumber())
                        .previousStatus(s.previousStatus())
                        .newStatus(ReleasedSlipDetail.NewStatusEnum.ORDERED))
                .toList();

        return new AllocationReleaseResult()
                .releasedCount(result.releasedCount())
                .releasedSlips(releasedSlips);
    }

    private UnpackCompletionResult toUnpackCompletionResult(UnpackCompletionInfo info) {
        List<com.wms.generated.model.InventoryMovement> movements = info.movements().stream()
                .map(m -> new com.wms.generated.model.InventoryMovement()
                        .movementType(com.wms.generated.model.InventoryMovement.MovementTypeEnum.fromValue(m.movementType()))
                        .productCode(m.productCode())
                        .unitType(m.unitType())
                        .quantity(m.quantity())
                        .locationCode(m.locationCode()))
                .toList();

        return new UnpackCompletionResult()
                .id(info.id())
                .status(UnpackCompletionResult.StatusEnum.fromValue(info.status()))
                .completedAt(info.completedAt())
                .inventoryMovements(movements);
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
