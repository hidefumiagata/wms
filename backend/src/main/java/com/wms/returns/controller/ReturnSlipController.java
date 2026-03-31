package com.wms.returns.controller;

import com.wms.generated.api.ReturnsApi;
import com.wms.generated.model.CreateReturnRequest;
import com.wms.generated.model.ReturnReason;
import com.wms.generated.model.ReturnSlipDetail;
import com.wms.generated.model.ReturnSlipPageResponse;
import com.wms.generated.model.ReturnSlipStatus;
import com.wms.generated.model.ReturnSlipSummary;
import com.wms.generated.model.ReturnType;
import com.wms.returns.entity.ReturnSlip;
import com.wms.returns.service.ReturnSlipService;
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
public class ReturnSlipController implements ReturnsApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "returnDate", "returnNumber", "returnType", "createdAt");

    private final ReturnSlipService returnSlipService;

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<ReturnSlipDetail> createReturn(
            CreateReturnRequest createReturnRequest) {
        ReturnSlip created = returnSlipService.create(createReturnRequest);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(toDetail(created));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<ReturnSlipPageResponse> getReturns(
            Long warehouseId, ReturnType returnType,
            LocalDate returnDateFrom, LocalDate returnDateTo,
            Long productId, Long partnerId, ReturnReason returnReason,
            Integer page, Integer size, String sort) {

        String returnTypeStr = returnType != null ? returnType.getValue() : null;
        String returnReasonStr = returnReason != null ? returnReason.getValue() : null;

        Sort sortObj = parseSort(sort);
        Page<ReturnSlip> resultPage = returnSlipService.search(
                warehouseId, returnTypeStr,
                returnDateFrom, returnDateTo,
                returnReasonStr,
                productId, partnerId,
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toPageResponse(resultPage));
    }

    private ReturnSlipDetail toDetail(ReturnSlip s) {
        String createdByName = returnSlipService.resolveUserName(s.getCreatedBy());

        return new ReturnSlipDetail()
                .id(s.getId())
                .slipNumber(s.getReturnNumber())
                .returnType(ReturnType.fromValue(s.getReturnType()))
                .warehouseId(s.getWarehouseId())
                .warehouseCode(s.getWarehouseCode())
                .warehouseName(s.getWarehouseName())
                .productId(s.getProductId())
                .productCode(s.getProductCode())
                .productName(s.getProductName())
                .quantity(s.getQuantity())
                .unitType(s.getUnitType())
                .locationId(s.getLocationId())
                .locationCode(s.getLocationCode())
                .partnerId(s.getPartnerId())
                .partnerCode(s.getPartnerCode())
                .partnerName(s.getPartnerName())
                .returnReason(ReturnReason.fromValue(s.getReturnReason()))
                .returnReasonNote(s.getReturnReasonNote())
                .relatedSlipNumber(s.getRelatedSlipNumber())
                .lotNumber(s.getLotNumber())
                .expiryDate(s.getExpiryDate())
                .returnDate(s.getReturnDate())
                .status(ReturnSlipStatus.fromValue(s.getStatus()))
                .createdAt(s.getCreatedAt())
                .createdBy(s.getCreatedBy())
                .createdByName(createdByName);
    }

    private ReturnSlipSummary toSummary(ReturnSlip s) {
        String createdByName = returnSlipService.resolveUserName(s.getCreatedBy());

        return new ReturnSlipSummary()
                .id(s.getId())
                .slipNumber(s.getReturnNumber())
                .returnType(ReturnType.fromValue(s.getReturnType()))
                .warehouseCode(s.getWarehouseCode())
                .productCode(s.getProductCode())
                .productName(s.getProductName())
                .quantity(s.getQuantity())
                .unitType(s.getUnitType())
                .locationCode(s.getLocationCode())
                .partnerCode(s.getPartnerCode())
                .partnerName(s.getPartnerName())
                .returnReason(ReturnReason.fromValue(s.getReturnReason()))
                .returnReasonNote(s.getReturnReasonNote())
                .relatedSlipNumber(s.getRelatedSlipNumber())
                .returnDate(s.getReturnDate())
                .status(ReturnSlipStatus.fromValue(s.getStatus()))
                .createdAt(s.getCreatedAt())
                .createdByName(createdByName);
    }

    private ReturnSlipPageResponse toPageResponse(Page<ReturnSlip> page) {
        List<ReturnSlipSummary> items = page.getContent().stream()
                .map(this::toSummary)
                .toList();
        return new ReturnSlipPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "returnDate";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
