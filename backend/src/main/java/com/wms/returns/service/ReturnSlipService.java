package com.wms.returns.service;

import com.wms.generated.model.CreateReturnRequest;
import com.wms.generated.model.ReturnReason;
import com.wms.generated.model.ReturnType;
import com.wms.inventory.service.InventoryService;
import com.wms.master.entity.Location;
import com.wms.master.entity.Partner;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.LocationService;
import com.wms.master.service.PartnerService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.returns.entity.ReturnSlip;
import com.wms.returns.repository.ReturnSlipRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import com.wms.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReturnSlipService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReturnSlipRepository returnSlipRepository;
    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final PartnerService partnerService;
    private final ProductService productService;
    private final LocationService locationService;
    private final BusinessDateProvider businessDateProvider;
    private final UserService userService;

    public Page<ReturnSlip> search(Long warehouseId, String returnType,
                                    LocalDate returnDateFrom, LocalDate returnDateTo,
                                    String returnReason,
                                    Long productId, Long partnerId,
                                    Pageable pageable) {
        warehouseService.findById(warehouseId);

        if (returnDateFrom != null && returnDateTo != null && returnDateFrom.isAfter(returnDateTo)) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "返品日Fromは返品日To以前を指定してください");
        }

        return returnSlipRepository.search(
                warehouseId, returnType, returnDateFrom, returnDateTo,
                productId, partnerId, returnReason, pageable);
    }

    public String resolveUserName(Long userId) {
        return userService.getUserFullName(userId);
    }

    @Transactional
    public ReturnSlip create(CreateReturnRequest request) {
        LocalDate businessDate = businessDateProvider.today();

        // Validate: returnType=INVENTORY requires locationId
        if (request.getReturnType() == ReturnType.INVENTORY && request.getLocationId() == null) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "在庫返品の場合、ロケーションIDは必須です");
        }

        // Validate: returnReason=OTHER requires returnReasonNote
        if (request.getReturnReason() == ReturnReason.OTHER
                && (request.getReturnReasonNote() == null || request.getReturnReasonNote().isBlank())) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "返品理由が「その他」の場合、備考は必須です");
        }

        // Validate product
        Product product = productService.findById(request.getProductId());
        if (!Boolean.TRUE.equals(product.getIsActive())) {
            log.info("ReturnSlip create product inactive: productId={}", product.getId());
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "無効な商品が指定されています");
        }

        // Validate partner (optional)
        Partner partner = null;
        if (request.getPartnerId() != null) {
            partner = partnerService.findById(request.getPartnerId());
        }

        // Get warehouse from JWT context (warehouseCode in WmsUserDetails)
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        Warehouse warehouse = warehouseService.findByCode(userDetails.getWarehouseCode());

        // Location + inventory checks for INVENTORY returns
        Location location = null;
        if (request.getReturnType() == ReturnType.INVENTORY) {
            location = locationService.findById(request.getLocationId());

            if (!location.getWarehouseId().equals(warehouse.getId())) {
                log.info("ReturnSlip create location warehouse mismatch: locationId={}, warehouseId={}",
                        location.getId(), warehouse.getId());
                throw new BusinessRuleViolationException("RETURN_LOCATION_WAREHOUSE_MISMATCH",
                        "指定のロケーションはこの倉庫に属していません");
            }

            if (Boolean.TRUE.equals(location.getIsStocktakingLocked())) {
                log.info("ReturnSlip create location stocktake locked: locationId={}", location.getId());
                throw new BusinessRuleViolationException("RETURN_STOCKTAKE_LOCKED",
                        "棚卸ロック中のロケーションからは返品できません");
            }
        }

        // Generate slip number
        String typePrefix = switch (request.getReturnType()) {
            case INBOUND -> "I";
            case INVENTORY -> "S";
            case OUTBOUND -> "O";
        };
        String dateStr = businessDate.format(DATE_FORMAT);
        String slipNumber = generateSlipNumber(typePrefix, dateStr);

        // Build entity
        ReturnSlip slip = ReturnSlip.builder()
                .returnNumber(slipNumber)
                .returnType(request.getReturnType().getValue())
                .status("COMPLETED")
                .warehouseId(warehouse.getId())
                .warehouseCode(warehouse.getWarehouseCode())
                .warehouseName(warehouse.getWarehouseName())
                .partnerId(partner != null ? partner.getId() : null)
                .partnerCode(partner != null ? partner.getPartnerCode() : null)
                .partnerName(partner != null ? partner.getPartnerName() : null)
                .productId(product.getId())
                .productCode(product.getProductCode())
                .productName(product.getProductName())
                .quantity(request.getQuantity())
                .unitType(request.getUnitType().getValue())
                .locationId(location != null ? location.getId() : null)
                .locationCode(location != null ? location.getLocationCode() : null)
                .returnReason(request.getReturnReason().getValue())
                .returnReasonNote(request.getReturnReasonNote())
                .relatedSlipNumber(request.getRelatedSlipNumber())
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .returnDate(businessDate)
                .build();

        // Deduct inventory for INVENTORY returns (before save to get referenceId after)
        // Save first to get the slip ID, then deduct
        ReturnSlip saved;
        try {
            saved = returnSlipRepository.save(slip);
        } catch (DataIntegrityViolationException e) {
            log.warn("Return slip number collision, retrying: {}", slipNumber);
            String retrySlipNumber = generateSlipNumber(typePrefix, dateStr);
            slip = slip.toBuilder().returnNumber(retrySlipNumber).build();
            saved = returnSlipRepository.save(slip);
        }

        if (request.getReturnType() == ReturnType.INVENTORY) {
            OffsetDateTime now = OffsetDateTime.now();
            inventoryService.deductReturnStock(new InventoryService.DeductReturnCommand(
                    warehouse.getId(),
                    location.getId(), location.getLocationCode(),
                    product.getId(), product.getProductCode(), product.getProductName(),
                    request.getUnitType().getValue(),
                    request.getQuantity(), saved.getId(),
                    userDetails.getUserId(), now));
        }

        log.info("ReturnSlip created: slipNumber={}, returnType={}, warehouseId={}",
                saved.getReturnNumber(), saved.getReturnType(), saved.getWarehouseId());
        return saved;
    }

    private String generateSlipNumber(String typePrefix, String dateStr) {
        int maxSeq = returnSlipRepository.findMaxSequenceByDate(dateStr);
        return String.format("RTN-%s-%s-%04d", typePrefix, dateStr, maxSeq + 1);
    }
}
