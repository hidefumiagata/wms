package com.wms.outbound.service;

import com.wms.generated.model.*;
import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.PartnerService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
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
public class OutboundSlipService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> CANCELLABLE_STATUSES = Set.of(
            OutboundSlipStatus.ORDERED.getValue(),
            OutboundSlipStatus.PARTIAL_ALLOCATED.getValue(),
            OutboundSlipStatus.ALLOCATED.getValue()
    );

    private final OutboundSlipRepository outboundSlipRepository;
    private final WarehouseService warehouseService;
    private final PartnerService partnerService;
    private final ProductService productService;
    private final BusinessDateProvider businessDateProvider;
    private final UserRepository userRepository;

    public Page<OutboundSlip> search(Long warehouseId, String slipNumber,
                                      List<String> statuses, LocalDate plannedDateFrom,
                                      LocalDate plannedDateTo, Long partnerId,
                                      Pageable pageable) {
        warehouseService.findById(warehouseId);

        String escapedSlipNumber = slipNumber != null ? escape(slipNumber) : null;

        log.debug("OutboundSlip search: warehouseId={}, slipNumber={}, statuses={}", warehouseId, slipNumber, statuses);
        return outboundSlipRepository.search(
                warehouseId, escapedSlipNumber, statuses,
                plannedDateFrom, plannedDateTo, partnerId, pageable);
    }

    public OutboundSlip findByIdWithLines(Long id) {
        return outboundSlipRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OUTBOUND_SLIP_NOT_FOUND",
                        "出荷伝票が見つかりません (id=" + id + ")"));
    }

    public long countLinesBySlipId(Long slipId) {
        return outboundSlipRepository.countLinesBySlipId(slipId);
    }

    public String resolveUserName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> u.getFullName())
                .orElse(null);
    }

    @Transactional
    public OutboundSlip create(CreateOutboundSlipRequest request) {
        LocalDate today = businessDateProvider.today();

        // 出荷予定日が営業日以降であること
        if (request.getPlannedDate().isBefore(today)) {
            throw new BusinessRuleViolationException("PLANNED_DATE_TOO_EARLY",
                    "出荷予定日は営業日以降を指定してください");
        }

        // 明細重複チェック（DB呼び出し前の安価なチェック）
        Set<Long> productIds = new HashSet<>();
        for (CreateOutboundLineRequest line : request.getLines()) {
            if (!productIds.add(line.getProductId())) {
                throw new DuplicateResourceException("DUPLICATE_PRODUCT_IN_LINES",
                        "同一伝票内に同じ商品が複数指定されています (productId=" + line.getProductId() + ")");
            }
        }

        // 倉庫チェック
        Warehouse warehouse = warehouseService.findById(request.getWarehouseId());

        // 出荷先チェック
        Partner partner = null;
        if (request.getSlipType() == OutboundSlipType.NORMAL) {
            if (request.getPartnerId() == null) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "通常出荷の場合、出荷先IDは必須です");
            }
            partner = partnerService.findById(request.getPartnerId());
            if (partner.getPartnerType() != PartnerType.CUSTOMER
                    && partner.getPartnerType() != PartnerType.BOTH) {
                throw new BusinessRuleViolationException("OUTBOUND_PARTNER_NOT_CUSTOMER",
                        "出荷先の取引先種別がCUSTOMERまたはBOTHではありません (partnerType=" + partner.getPartnerType() + ")");
            }
        } else if (request.getPartnerId() != null) {
            partner = partnerService.findById(request.getPartnerId());
        }

        // 各明細の商品チェック
        List<ProductLineInfo> lineInfos = request.getLines().stream().map(lineReq -> {
            Product product = productService.findById(lineReq.getProductId());

            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "無効な商品が指定されています (productId=" + product.getId() + ")");
            }

            if (Boolean.TRUE.equals(product.getShipmentStopFlag())) {
                throw new BusinessRuleViolationException("OUTBOUND_PRODUCT_SHIPMENT_STOPPED",
                        "出荷禁止フラグが設定されている商品です (productId=" + product.getId() + ")");
            }

            return new ProductLineInfo(product, lineReq);
        }).toList();

        // 伝票番号採番
        String dateStr = today.format(DATE_FORMAT);
        String slipNumber = generateSlipNumber(dateStr);

        // ヘッダー構築
        OutboundSlip slip = OutboundSlip.builder()
                .slipNumber(slipNumber)
                .slipType(request.getSlipType().getValue())
                .warehouseId(warehouse.getId())
                .warehouseCode(warehouse.getWarehouseCode())
                .warehouseName(warehouse.getWarehouseName())
                .partnerId(partner != null ? partner.getId() : null)
                .partnerCode(partner != null ? partner.getPartnerCode() : null)
                .partnerName(partner != null ? partner.getPartnerName() : null)
                .plannedDate(request.getPlannedDate())
                .status(OutboundSlipStatus.ORDERED.getValue())
                .note(request.getNote())
                .build();

        // 明細構築
        int lineNo = 1;
        for (ProductLineInfo info : lineInfos) {
            OutboundSlipLine line = OutboundSlipLine.builder()
                    .lineNo(lineNo++)
                    .productId(info.product.getId())
                    .productCode(info.product.getProductCode())
                    .productName(info.product.getProductName())
                    .unitType(info.lineReq.getUnitType().getValue())
                    .orderedQty(info.lineReq.getOrderedQty())
                    .shippedQty(0)
                    .lineStatus(OutboundLineStatus.ORDERED.getValue())
                    .build();
            slip.addLine(line);
        }

        try {
            OutboundSlip saved = outboundSlipRepository.save(slip);
            log.info("OutboundSlip created: slipNumber={}, warehouseId={}, lineCount={}",
                    saved.getSlipNumber(), saved.getWarehouseId(), saved.getLines().size());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // 伝票番号衝突（競合状態）→ リトライ
            log.warn("Slip number collision detected, retrying: {}", slipNumber);
            String retrySlipNumber = generateSlipNumber(dateStr);
            slip = rebuildSlip(retrySlipNumber, request, warehouse, partner, lineInfos);
            OutboundSlip saved = outboundSlipRepository.save(slip);
            log.info("OutboundSlip created (retry): slipNumber={}", saved.getSlipNumber());
            return saved;
        }
    }

    @Transactional
    public void delete(Long id) {
        OutboundSlip slip = outboundSlipRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OUTBOUND_SLIP_NOT_FOUND",
                        "出荷伝票が見つかりません (id=" + id + ")"));

        if (!OutboundSlipStatus.ORDERED.getValue().equals(slip.getStatus())) {
            throw new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                    "ORDERED以外のステータスの出荷伝票は削除できません (status=" + slip.getStatus() + ")");
        }

        outboundSlipRepository.delete(slip);
        log.info("OutboundSlip deleted: id={}, slipNumber={}", id, slip.getSlipNumber());
    }

    @Transactional
    public OutboundSlip cancel(Long id, CancelOutboundRequest request) {
        OutboundSlip slip = outboundSlipRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OUTBOUND_SLIP_NOT_FOUND",
                        "出荷伝票が見つかりません (id=" + id + ")"));

        if (!CANCELLABLE_STATUSES.contains(slip.getStatus())) {
            throw new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                    "キャンセル可能なステータスではありません (status=" + slip.getStatus() + ")");
        }

        // TODO: PARTIAL_ALLOCATED/ALLOCATED の場合は引当解放が必要（allocation実装時に拡張）
        // 現時点ではORDEREDのみキャンセル可能。PARTIAL_ALLOCATED/ALLOCATEDは引当実装後に対応

        Long currentUserId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        slip.setStatus(OutboundSlipStatus.CANCELLED.getValue());
        slip.setCancelledAt(now);
        slip.setCancelledBy(currentUserId);
        slip.setCancelReason(request != null ? request.getReason() : null);

        // 全明細をCANCELLEDに
        for (OutboundSlipLine line : slip.getLines()) {
            line.setLineStatus(OutboundLineStatus.CANCELLED.getValue());
        }

        OutboundSlip saved = outboundSlipRepository.save(slip);
        log.info("OutboundSlip cancelled: id={}, slipNumber={}", id, saved.getSlipNumber());
        return saved;
    }

    private OutboundSlip rebuildSlip(String slipNumber, CreateOutboundSlipRequest request,
                                      Warehouse warehouse, Partner partner,
                                      List<ProductLineInfo> lineInfos) {
        OutboundSlip slip = OutboundSlip.builder()
                .slipNumber(slipNumber)
                .slipType(request.getSlipType().getValue())
                .warehouseId(warehouse.getId())
                .warehouseCode(warehouse.getWarehouseCode())
                .warehouseName(warehouse.getWarehouseName())
                .partnerId(partner != null ? partner.getId() : null)
                .partnerCode(partner != null ? partner.getPartnerCode() : null)
                .partnerName(partner != null ? partner.getPartnerName() : null)
                .plannedDate(request.getPlannedDate())
                .status(OutboundSlipStatus.ORDERED.getValue())
                .note(request.getNote())
                .build();

        int lineNo = 1;
        for (ProductLineInfo info : lineInfos) {
            OutboundSlipLine line = OutboundSlipLine.builder()
                    .lineNo(lineNo++)
                    .productId(info.product.getId())
                    .productCode(info.product.getProductCode())
                    .productName(info.product.getProductName())
                    .unitType(info.lineReq.getUnitType().getValue())
                    .orderedQty(info.lineReq.getOrderedQty())
                    .shippedQty(0)
                    .lineStatus(OutboundLineStatus.ORDERED.getValue())
                    .build();
            slip.addLine(line);
        }
        return slip;
    }

    private Long getCurrentUserId() {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }

    private String generateSlipNumber(String dateStr) {
        int maxSeq = outboundSlipRepository.findMaxSequenceByDate(dateStr);
        return String.format("OUT-%s-%04d", dateStr, maxSeq + 1);
    }

    private record ProductLineInfo(Product product, CreateOutboundLineRequest lineReq) {}
}
