package com.wms.inbound.service;

import com.wms.generated.model.CreateInboundLineRequest;
import com.wms.generated.model.CreateInboundSlipRequest;
import com.wms.generated.model.InboundSlipType;
import com.wms.generated.model.InspectInboundRequest;
import com.wms.generated.model.InspectLineRequest;
import com.wms.generated.model.StoreInboundRequest;
import com.wms.generated.model.StoreLineRequest;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.inbound.repository.InboundSlipLineRepository;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.inventory.service.InventoryService;
import com.wms.master.entity.Area;
import com.wms.master.entity.Location;
import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
import com.wms.master.service.LocationService;
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
import java.time.ZoneId;
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
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final InboundSlipRepository inboundSlipRepository;
    private final InboundSlipLineRepository inboundSlipLineRepository;
    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final PartnerService partnerService;
    private final ProductService productService;
    private final LocationService locationService;
    private final AreaService areaService;
    private final BusinessDateProvider businessDateProvider;
    private final UserService userService;

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

    public Page<InboundSlipLine> findResults(Long warehouseId, LocalDate storedDateFrom,
                                              LocalDate storedDateTo, Long partnerId,
                                              String slipNumber, String productCode,
                                              Pageable pageable) {
        warehouseService.findById(warehouseId);

        LocalDate today = businessDateProvider.today();
        LocalDate fromDate = storedDateFrom != null ? storedDateFrom : today.withDayOfMonth(1);
        LocalDate toDate = storedDateTo != null ? storedDateTo : today;

        OffsetDateTime fromDateTime = fromDate.atStartOfDay(JST).toOffsetDateTime();
        OffsetDateTime toDateTime = toDate.plusDays(1).atStartOfDay(JST).toOffsetDateTime();

        String escapedSlipNumber = slipNumber != null ? escape(slipNumber) : null;
        String escapedProductCode = productCode != null ? escape(productCode) : null;

        log.debug("InboundResult search: warehouseId={}, storedDateFrom={}, storedDateTo={}", warehouseId, fromDate, toDate);
        return inboundSlipLineRepository.searchResults(
                warehouseId, fromDateTime, toDateTime, partnerId,
                escapedSlipNumber, escapedProductCode, pageable);
    }

    public InboundSlip findById(Long id) {
        return inboundSlipRepository.findById(id)
                .orElseThrow(() -> {
                    // SEC-R2-Min-1 (OWASP A09): 例外メッセージには内部 id を含めない。
                    // 追跡用の id はログ側に出力する。
                    log.info("InboundSlip not found: id={}", id);
                    return new ResourceNotFoundException(
                            "INBOUND_SLIP_NOT_FOUND",
                            "入荷伝票が見つかりません");
                });
    }

    public InboundSlip findByIdWithLines(Long id) {
        return inboundSlipRepository.findByIdWithLines(id)
                .orElseThrow(() -> {
                    log.info("InboundSlip not found (withLines): id={}", id);
                    return new ResourceNotFoundException(
                            "INBOUND_SLIP_NOT_FOUND",
                            "入荷伝票が見つかりません");
                });
    }

    public long countLinesBySlipId(Long slipId) {
        return inboundSlipLineRepository.countByInboundSlipId(slipId);
    }

    public String resolveUserName(Long userId) {
        return userService.getUserFullName(userId);
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
                log.info("InboundSlip create duplicate product in lines: productId={}", line.getProductId());
                throw new DuplicateResourceException("DUPLICATE_PRODUCT_IN_LINES",
                        "同一伝票内に同じ商品が複数指定されています");
            }
        }

        // Validate warehouse
        Warehouse warehouse = warehouseService.findById(request.getWarehouseId());

        // Validate partner (required for NORMAL, not for WAREHOUSE_TRANSFER)
        Partner partner = null;
        if (request.getSlipType() == InboundSlipType.NORMAL) {
            if (request.getPartnerId() == null) {
                throw new BusinessRuleViolationException("INBOUND_PARTNER_REQUIRED",
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
                log.info("InboundSlip create product inactive: productId={}", product.getId());
                throw new BusinessRuleViolationException("PRODUCT_INACTIVE",
                        "無効な商品が指定されています");
            }

            if (Boolean.TRUE.equals(product.getLotManageFlag())
                    && (lineReq.getLotNumber() == null || lineReq.getLotNumber().isBlank())) {
                log.info("InboundSlip create lot number required: productId={}", product.getId());
                throw new BusinessRuleViolationException("LOT_NUMBER_REQUIRED",
                        "ロット管理対象商品にはロット番号が必須です");
            }

            if (Boolean.TRUE.equals(product.getExpiryManageFlag()) && lineReq.getExpiryDate() == null) {
                log.info("InboundSlip create expiry date required: productId={}", product.getId());
                throw new BusinessRuleViolationException("EXPIRY_DATE_REQUIRED",
                        "期限管理対象商品には賞味/使用期限日が必須です");
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
            log.info("InboundSlip confirm rejected due to status: id={}, status={}", id, slip.getStatus());
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "現在のステータスでは入荷伝票を確定できません");
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
            log.info("InboundSlip cancel rejected due to status: id={}, status={}", id, slip.getStatus());
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "現在のステータスでは入荷伝票をキャンセルできません");
        }

        Long currentUserId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        // If PARTIAL_STORED, rollback stored inventory for lines with lineStatus == STORED
        if (InboundSlipStatus.PARTIAL_STORED.getValue().equals(slip.getStatus())) {
            for (InboundSlipLine line : slip.getLines()) {
                if (InboundLineStatus.STORED.getValue().equals(line.getLineStatus())) {
                    rollbackLineInventory(slip, line, currentUserId, now);
                }
            }
        }

        // Update all lines to CANCELLED
        for (InboundSlipLine line : slip.getLines()) {
            line.setLineStatus(InboundLineStatus.CANCELLED.getValue());
        }

        slip.setStatus(InboundSlipStatus.CANCELLED.getValue());
        slip.setCancelledAt(now);
        slip.setCancelledBy(currentUserId);
        InboundSlip saved = inboundSlipRepository.save(slip);
        log.info("InboundSlip cancelled: id={}, slipNumber={}", id, saved.getSlipNumber());
        return saved;
    }

    @Transactional
    public InboundSlip inspect(Long id, InspectInboundRequest request) {
        InboundSlip slip = findByIdWithLines(id);

        String status = slip.getStatus();
        if (!InboundSlipStatus.CONFIRMED.getValue().equals(status)
                && !InboundSlipStatus.INSPECTING.getValue().equals(status)
                && !InboundSlipStatus.PARTIAL_STORED.getValue().equals(status)) {
            log.info("InboundSlip inspect rejected due to status: id={}, status={}", id, status);
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "現在のステータスでは検品できません");
        }

        // Duplicate lineId check
        // SEC-R2-Min-1 (OWASP A09): 例外メッセージからは内部 lineId を除去し、log 側に出力する。
        Set<Long> inspectLineIds = new HashSet<>();
        for (InspectLineRequest lineReq : request.getLines()) {
            if (!inspectLineIds.add(lineReq.getLineId())) {
                log.warn("InboundSlip inspect duplicate lineId: slipId={}, lineId={}", id, lineReq.getLineId());
                throw new BusinessRuleViolationException("DUPLICATE_LINE_IN_REQUEST",
                        "リクエスト内に同じ明細が重複しています");
            }
        }

        Long currentUserId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        for (InspectLineRequest lineReq : request.getLines()) {
            InboundSlipLine line = slip.getLines().stream()
                    .filter(l -> l.getId().equals(lineReq.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> {
                        log.info("InboundSlip inspect line not found: slipId={}, lineId={}", id, lineReq.getLineId());
                        return new ResourceNotFoundException("INBOUND_LINE_NOT_FOUND",
                                "入荷伝票明細が見つかりません");
                    });

            if (InboundLineStatus.STORED.getValue().equals(line.getLineStatus())) {
                log.info("InboundSlip inspect already stored: slipId={}, lineId={}", id, line.getId());
                throw new InvalidStateTransitionException("INBOUND_LINE_ALREADY_STORED",
                        "格納済みの明細は検品できません");
            }

            if (lineReq.getInspectedQty() < 0) {
                log.info("InboundSlip inspect negative qty: slipId={}, lineId={}, inspectedQty={}",
                        id, line.getId(), lineReq.getInspectedQty());
                throw new BusinessRuleViolationException("INBOUND_INSPECTED_QTY_NEGATIVE",
                        "検品数は0以上である必要があります");
            }

            line.setInspectedQty(lineReq.getInspectedQty());
            line.setLineStatus(InboundLineStatus.INSPECTED.getValue());
            line.setInspectedAt(now);
            line.setInspectedBy(currentUserId);
        }

        if (InboundSlipStatus.CONFIRMED.getValue().equals(status)) {
            slip.setStatus(InboundSlipStatus.INSPECTING.getValue());
        }

        InboundSlip saved = inboundSlipRepository.save(slip);
        log.info("InboundSlip inspected: id={}, slipNumber={}, inspectedLines={}",
                id, saved.getSlipNumber(), request.getLines().size());
        return saved;
    }

    @Transactional
    public InboundSlip store(Long id, StoreInboundRequest request) {
        InboundSlip slip = findByIdWithLines(id);

        String status = slip.getStatus();
        if (!InboundSlipStatus.INSPECTING.getValue().equals(status)
                && !InboundSlipStatus.PARTIAL_STORED.getValue().equals(status)) {
            log.info("InboundSlip store rejected due to status: id={}, status={}", id, status);
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "現在のステータスでは格納できません");
        }

        // Duplicate lineId check
        // SEC-R2-Min-1 (OWASP A09): 例外メッセージからは内部 lineId を除去し、log 側に出力する。
        Set<Long> storeLineIds = new HashSet<>();
        for (StoreLineRequest lineReq : request.getLines()) {
            if (!storeLineIds.add(lineReq.getLineId())) {
                log.warn("InboundSlip store duplicate lineId: slipId={}, lineId={}", id, lineReq.getLineId());
                throw new BusinessRuleViolationException("DUPLICATE_LINE_IN_REQUEST",
                        "リクエスト内に同じ明細が重複しています");
            }
        }

        Long currentUserId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        for (StoreLineRequest lineReq : request.getLines()) {
            InboundSlipLine line = slip.getLines().stream()
                    .filter(l -> l.getId().equals(lineReq.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> {
                        log.info("InboundSlip store line not found: slipId={}, lineId={}", id, lineReq.getLineId());
                        return new ResourceNotFoundException("INBOUND_LINE_NOT_FOUND",
                                "入荷伝票明細が見つかりません");
                    });

            if (!InboundLineStatus.INSPECTED.getValue().equals(line.getLineStatus())) {
                log.info("InboundSlip store line not inspected: slipId={}, lineId={}, lineStatus={}",
                        id, line.getId(), line.getLineStatus());
                throw new InvalidStateTransitionException("INBOUND_LINE_NOT_INSPECTED",
                        "検品済みでない明細は格納できません");
            }

            if (line.getInspectedQty() == null || line.getInspectedQty() <= 0) {
                log.info("InboundSlip store inspected qty zero: slipId={}, lineId={}", id, line.getId());
                throw new BusinessRuleViolationException("INSPECTED_QTY_ZERO",
                        "検品数が0の明細は格納できません");
            }

            Location location = locationService.findById(lineReq.getLocationId());

            if (!location.getWarehouseId().equals(slip.getWarehouseId())) {
                log.info("InboundSlip store location warehouse mismatch: slipId={}, locationId={}, locationWarehouseId={}, slipWarehouseId={}",
                        id, location.getId(), location.getWarehouseId(), slip.getWarehouseId());
                throw new BusinessRuleViolationException("LOCATION_WAREHOUSE_MISMATCH",
                        "ロケーションが入荷伝票の倉庫に属していません");
            }

            if (!Boolean.TRUE.equals(location.getIsActive())) {
                log.info("InboundSlip store location inactive: slipId={}, locationId={}", id, location.getId());
                throw new BusinessRuleViolationException("LOCATION_INACTIVE",
                        "無効なロケーションです");
            }

            Area area = areaService.findById(location.getAreaId());

            if (!"INBOUND".equals(area.getAreaType())) {
                log.info("InboundSlip store area not inbound: slipId={}, locationId={}, areaType={}",
                        id, location.getId(), area.getAreaType());
                throw new BusinessRuleViolationException("AREA_NOT_INBOUND",
                        "入荷エリア以外のロケーションには格納できません");
            }

            if (Boolean.TRUE.equals(location.getIsStocktakingLocked())) {
                log.info("InboundSlip store location stocktake locked: slipId={}, locationId={}",
                        id, location.getId());
                throw new BusinessRuleViolationException("LOCATION_STOCKTAKE_LOCKED",
                        "棚卸中のロケーションには格納できません");
            }

            if (inventoryService.existsDifferentProductAtLocation(location.getId(), line.getProductId())) {
                log.info("InboundSlip store different product at location: slipId={}, locationId={}, productId={}",
                        id, location.getId(), line.getProductId());
                throw new BusinessRuleViolationException("DIFFERENT_PRODUCT_AT_LOCATION",
                        "同一ロケーションに異なる商品の在庫が存在します");
            }

            inventoryService.storeInboundStock(new InventoryService.StoreInboundCommand(
                    slip.getWarehouseId(),
                    location.getId(), location.getLocationCode(),
                    line.getProductId(), line.getProductCode(), line.getProductName(),
                    line.getUnitType(), line.getLotNumber(), line.getExpiryDate(),
                    line.getInspectedQty(), slip.getId(), currentUserId, now));

            line.setLineStatus(InboundLineStatus.STORED.getValue());
            line.setPutawayLocationId(location.getId());
            line.setPutawayLocationCode(location.getLocationCode());
            line.setStoredAt(now);
            line.setStoredBy(currentUserId);
        }

        boolean allStored = slip.getLines().stream()
                .allMatch(l -> InboundLineStatus.STORED.getValue().equals(l.getLineStatus()));
        slip.setStatus(allStored
                ? InboundSlipStatus.STORED.getValue()
                : InboundSlipStatus.PARTIAL_STORED.getValue());

        InboundSlip saved = inboundSlipRepository.save(slip);
        log.info("InboundSlip stored: id={}, slipNumber={}, storedLines={}, newStatus={}",
                id, saved.getSlipNumber(), request.getLines().size(), saved.getStatus());
        return saved;
    }

    private void rollbackLineInventory(InboundSlip slip, InboundSlipLine line,
                                        Long userId, OffsetDateTime now) {
        inventoryService.rollbackInboundStock(new InventoryService.RollbackInboundCommand(
                slip.getWarehouseId(),
                line.getPutawayLocationId(), line.getPutawayLocationCode(),
                line.getProductId(), line.getProductCode(), line.getProductName(),
                line.getUnitType(), line.getLotNumber(), line.getExpiryDate(),
                line.getInspectedQty(), slip.getId(), userId, now));
    }

    private Long getCurrentUserId() {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }

    @Transactional
    public void delete(Long id) {
        InboundSlip slip = findByIdWithLines(id);

        if (!InboundSlipStatus.PLANNED.getValue().equals(slip.getStatus())) {
            log.info("InboundSlip delete rejected due to status: id={}, status={}", id, slip.getStatus());
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "現在のステータスでは入荷伝票を削除できません");
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
