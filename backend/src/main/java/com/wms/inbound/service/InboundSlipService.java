package com.wms.inbound.service;

import com.wms.generated.model.CreateInboundLineRequest;
import com.wms.generated.model.CreateInboundSlipRequest;
import com.wms.generated.model.InboundSlipType;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.inbound.repository.InboundSlipLineRepository;
import com.wms.inbound.repository.InboundSlipRepository;
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
import com.wms.shared.util.BusinessDateProvider;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

        // Generate slip number
        String dateStr = today.format(DATE_FORMAT);
        long count = inboundSlipRepository.countBySlipDate(dateStr);
        String slipNumber = String.format("INB-%s-%04d", dateStr, count + 1);

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
                .status("PLANNED")
                .note(request.getNote())
                .build();

        // Build lines
        int lineNo = 1;
        for (CreateInboundLineRequest lineReq : request.getLines()) {
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

            InboundSlipLine line = InboundSlipLine.builder()
                    .lineNo(lineNo++)
                    .productId(product.getId())
                    .productCode(product.getProductCode())
                    .productName(product.getProductName())
                    .unitType(lineReq.getUnitType().getValue())
                    .plannedQty(lineReq.getPlannedQty())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .lineStatus("PENDING")
                    .build();
            slip.addLine(line);
        }

        InboundSlip saved = inboundSlipRepository.save(slip);
        log.info("InboundSlip created: slipNumber={}, warehouseId={}, lineCount={}",
                saved.getSlipNumber(), saved.getWarehouseId(), saved.getLines().size());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        InboundSlip slip = findById(id);

        if (!"PLANNED".equals(slip.getStatus())) {
            throw new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "PLANNED以外のステータスの入荷伝票は削除できません (status=" + slip.getStatus() + ")");
        }

        inboundSlipLineRepository.deleteByInboundSlipId(id);
        inboundSlipRepository.delete(slip);
        log.info("InboundSlip deleted: id={}, slipNumber={}", id, slip.getSlipNumber());
    }
}
