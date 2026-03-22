package com.wms.inbound.service;

import com.wms.generated.model.CreateInboundLineRequest;
import com.wms.generated.model.CreateInboundSlipRequest;
import com.wms.generated.model.InboundSlipType;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.inbound.repository.InboundSlipLineRepository;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.PartnerService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.generated.model.InboundLineStatus;
import com.wms.generated.model.InboundSlipStatus;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import com.wms.system.repository.UserRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.wms.shared.util.LikeEscapeUtil.escape;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InboundSlipService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InboundSlipRepository inboundSlipRepository;
    private final InboundSlipLineRepository inboundSlipLineRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final WarehouseService warehouseService;
    private final PartnerService partnerService;
    private final ProductService productService;
    private final BusinessDateProvider businessDateProvider;
    private final UserRepository userRepository;

    public Page<InboundSlip> search(Long warehouseId, String slipNumber,
                                     List<String> statuses, LocalDate plannedDateFrom,
                                     LocalDate plannedDateTo, Long partnerId,
                                     Pageable pageable) {
        warehouseService.findById(warehouseId);

        String escapedSlipNumber = slipNumber != null ? escape(slipNumber) : null;

        log.debug("InboundSlip search: warehouseId={}, slipNumber={}, statuses={}", warehouseId, slipNumber, statuses);
        return inboundSlipRepository.search(
                warehouseId, escapedSlipNumber, statuses,
                plannedDateFrom, plannedDateTo, partnerId, pageable);
    }

    public InboundSlip findById(Long id) {
        return inboundSlipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INBOUND_SLIP_NOT_FOUND",
                        "入荷伝票が見つかりません (id=" + id + ")"));
    }

    public InboundSlip findByIdWithLines(Long id) {
        return inboundSlipRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INBOUND_SLIP_NOT_FOUND",
                        "入荷伝票が見つかりません (id=" + id + ")"));
    }

    public long countLinesBySlipId(Long slipId) {
        return inboundSlipLineRepository.countByInboundSlipId(slipId);
    }

    public String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(u -> u.getFullName())
                .orElse(null);
    }

    @Transactional
    public InboundSlip create(CreateInboundSlipRequest request) {
        LocalDate today = businessDateProvider.today();

        // Validate planned date
        if (request.getPlannedDate().isBefore(today)) {
            throw new BusinessRuleViolationException("PLANNED_DATE_TOO_EARLY",
                    "入荷予定日は営業日以降を指定してください");
        }

        // Validate lines - check for duplicate products (cheap check before DB calls)
        Set<Long> productIds = new HashSet<>();
        for (CreateInboundLineRequest line : request.getLines()) {
            if (!productIds.add(line.getProductId())) {
                throw new DuplicateResourceException("DUPLICATE_PRODUCT_IN_LINES",
                        "同一伝票内に同じ商品が複数指定されています (productId=" + line.getProductId() + ")");
            }
        }

        // Validate warehouse
        Warehouse warehouse = warehouseService.findById(request.getWarehouseId());

        // Validate partner (required for NORMAL, not for WAREHOUSE_TRANSFER)
        Partner partner = null;
        if (request.getSlipType() == InboundSlipType.NORMAL) {
            if (request.getPartnerId() == null) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "通常入荷の場合、仕入先IDは必須です");
            }
            partner = partnerService.findById(request.getPartnerId());
            if (partner.getPartnerType() != PartnerType.SUPPLIER
                    && partner.getPartnerType() != PartnerType.BOTH) {
                throw new BusinessRuleViolationException("INBOUND_PARTNER_NOT_SUPPLIER",
                        "仕入先の取引先種別がSUPPLIERまたはBOTHではありません (partnerType=" + partner.getPartnerType() + ")");
            }
        } else if (request.getPartnerId() != null) {
            partner = partnerService.findById(request.getPartnerId());
        }

        // Validate each line's product
        List<ProductLineInfo> lineInfos = request.getLines().stream().map(lineReq -> {
            Product product = productService.findById(lineReq.getProductId());

            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "無効な商品が指定されています (productId=" + product.getId() + ")");
            }

            if (Boolean.TRUE.equals(product.getLotManageFlag())
                    && (lineReq.getLotNumber() == null || lineReq.getLotNumber().isBlank())) {
                throw new BusinessRuleViolationException("LOT_NUMBER_REQUIRED",
                        "ロット管理対象商品にはロット番号が必須です (productId=" + product.getId() + ")");
            }

            if (Boolean.TRUE.equals(product.getExpiryManageFlag()) && lineReq.getExpiryDate() == null) {
                throw new BusinessRuleViolationException("EXPIRY_DATE_REQUIRED",
                        "期限管理対象商品には賞味/使用期限日が必須です (productId=" + product.getId() + ")");
            }

            return new ProductLineInfo(product, lineReq);
        }).toList();

        // Generate slip number (MAX-based to handle gaps from deleted slips)
        String dateStr = today.format(DATE_FORMAT);
        String slipNumber = generateSlipNumber(dateStr);

        // Build header
        InboundSlip slip = InboundSlip.builder()
                .slipNumber(slipNumber)
                .slipType(request.getSlipType().getValue())
                .warehouseId(warehouse.getId())
                .warehouseCode(warehouse.getWarehouseCode())
                .warehouseName(warehouse.getWarehouseName())
                .partnerId(partner != null ? partner.getId() : null)
                .partnerCode(partner != null ? partner.getPartnerCode() : null)
                .partnerName(partner != null ? partner.getPartnerName() : null)
                .plannedDate(request.getPlannedDate())
                .status(InboundSlipStatus.PLANNED.getValue())
                .note(request.getNote())
                .build();

        // Build lines
        int lineNo = 1;
        for (ProductLineInfo info : lineInfos) {
            InboundSlipLine line = InboundSlipLine.builder()
                    .lineNo(lineNo++)
                    .productId(info.product.getId())
                    .productCode(info.product.getProductCode())
                    .productName(info.product.getProductName())
                    .unitType(info.lineReq.getUnitType().getValue())
                    .plannedQty(info.lineReq.getPlannedQty())
                    .lotNumber(info.lineReq.getLotNumber())
                    .expiryDate(info.lineReq.getExpiryDate())
                    .lineStatus(InboundLineStatus.PENDING.getValue())
                    .build();
            slip.addLine(line);
        }

        try {
            InboundSlip saved = inboundSlipRepository.save(slip);
            log.info("InboundSlip created: slipNumber={}, warehouseId={}, lineCount={}",
                    saved.getSlipNumber(), saved.getWarehouseId(), saved.getLines().size());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Slip number collision (race condition) — retry with fresh sequence
            log.warn("Slip number collision detected, retrying: {}", slipNumber);
            String retrySlipNumber = generateSlipNumber(dateStr);
            slip = InboundSlip.builder()
                    .slipNumber(retrySlipNumber)
                    .slipType(slip.getSlipType())
                    .warehouseId(slip.getWarehouseId())
                    .warehouseCode(slip.getWarehouseCode())
                    .warehouseName(slip.getWarehouseName())
                    .partnerId(slip.getPartnerId())
                    .partnerCode(slip.getPartnerCode())
                    .partnerName(slip.getPartnerName())
                    .plannedDate(slip.getPlannedDate())
                    .status(slip.getStatus())
                    .note(slip.getNote())
                    .build();
            for (int i = 0; i < lineInfos.size(); i++) {
                ProductLineInfo info = lineInfos.get(i);
                InboundSlipLine line = InboundSlipLine.builder()
                        .lineNo(i + 1)
                        .productId(info.product.getId())
                        .productCode(info.product.getProductCode())
                        .productName(info.product.getProductName())
                        .unitType(info.lineReq.getUnitType().getValue())
                        .plannedQty(info.lineReq.getPlannedQty())
                        .lotNumber(info.lineReq.getLotNumber())
                        .expiryDate(info.lineReq.getExpiryDate())
                        .lineStatus(InboundLineStatus.PENDING.getValue())
                        .build();
                slip.addLine(line);
            }
            InboundSlip saved = inboundSlipRepository.save(slip);
            log.info("InboundSlip created (retry): slipNumber={}", saved.getSlipNumber());
            return saved;
        }
    }

    @Transactional
    public InboundSlip confirm(Long id) {
        InboundSlip slip = findByIdWithLines(id);

        if (!InboundSlipStatus.PLANNED.getValue().equals(slip.getStatus())) {
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "PLANNED以外のステータスの入荷伝票は確定できません (status=" + slip.getStatus() + ")");
        }

        slip.setStatus(InboundSlipStatus.CONFIRMED.getValue());
        InboundSlip saved = inboundSlipRepository.save(slip);
        log.info("InboundSlip confirmed: id={}, slipNumber={}", id, saved.getSlipNumber());
        return saved;
    }

    @Transactional
    public InboundSlip cancel(Long id) {
        InboundSlip slip = findByIdWithLines(id);

        if (InboundSlipStatus.STORED.getValue().equals(slip.getStatus())
                || InboundSlipStatus.CANCELLED.getValue().equals(slip.getStatus())) {
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "STOREDまたはCANCELLEDステータスの入荷伝票はキャンセルできません (status=" + slip.getStatus() + ")");
        }

        // If PARTIAL_STORED, rollback stored inventory for lines with lineStatus == STORED
        if (InboundSlipStatus.PARTIAL_STORED.getValue().equals(slip.getStatus())) {
            Long currentUserId = getCurrentUserId();
            OffsetDateTime now = OffsetDateTime.now();

            for (InboundSlipLine line : slip.getLines()) {
                if (InboundLineStatus.STORED.getValue().equals(line.getLineStatus())) {
                    Inventory inventory = inventoryRepository
                            .findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                                    line.getPutawayLocationId(), line.getProductId(),
                                    line.getUnitType(), line.getLotNumber(), line.getExpiryDate())
                            .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                                    "在庫が見つかりません (locationId=" + line.getPutawayLocationId()
                                            + ", productId=" + line.getProductId() + ")"));

                    inventory.setQuantity(inventory.getQuantity() - line.getInspectedQty());
                    inventoryRepository.save(inventory);

                    InventoryMovement movement = InventoryMovement.builder()
                            .warehouseId(slip.getWarehouseId())
                            .locationId(line.getPutawayLocationId())
                            .locationCode(line.getPutawayLocationCode())
                            .productId(line.getProductId())
                            .productCode(line.getProductCode())
                            .productName(line.getProductName())
                            .unitType(line.getUnitType())
                            .lotNumber(line.getLotNumber())
                            .expiryDate(line.getExpiryDate())
                            .movementType("INBOUND_CANCEL")
                            .quantity(-line.getInspectedQty())
                            .quantityAfter(inventory.getQuantity())
                            .referenceId(slip.getId())
                            .referenceType("INBOUND_SLIP")
                            .executedAt(now)
                            .executedBy(currentUserId)
                            .build();
                    inventoryMovementRepository.save(movement);
                }
            }
        }

        Long currentUserId = getCurrentUserId();
        slip.setStatus(InboundSlipStatus.CANCELLED.getValue());
        slip.setCancelledAt(OffsetDateTime.now());
        slip.setCancelledBy(currentUserId);
        InboundSlip saved = inboundSlipRepository.save(slip);
        log.info("InboundSlip cancelled: id={}, slipNumber={}", id, saved.getSlipNumber());
        return saved;
    }

    Long getCurrentUserId() {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }

    @Transactional
    public void delete(Long id) {
        InboundSlip slip = findByIdWithLines(id);

        if (!InboundSlipStatus.PLANNED.getValue().equals(slip.getStatus())) {
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "PLANNED以外のステータスの入荷伝票は削除できません (status=" + slip.getStatus() + ")");
        }

        // CascadeType.ALL + orphanRemoval handles line deletion
        inboundSlipRepository.delete(slip);
        log.info("InboundSlip deleted: id={}, slipNumber={}", id, slip.getSlipNumber());
    }

    private String generateSlipNumber(String dateStr) {
        int maxSeq = inboundSlipRepository.findMaxSequenceByDate(dateStr);
        return String.format("INB-%s-%04d", dateStr, maxSeq + 1);
    }

    private record ProductLineInfo(Product product, CreateInboundLineRequest lineReq) {}
}
