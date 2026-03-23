package com.wms.outbound.service;

import com.wms.allocation.entity.AllocationDetail;
import com.wms.allocation.repository.AllocationDetailRepository;
import com.wms.generated.model.*;
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
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.entity.PickingInstructionLine;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.outbound.repository.PickingInstructionLineRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
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
import java.util.*;
import java.util.stream.Collectors;

import static com.wms.shared.util.LikeEscapeUtil.escape;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OutboundSlipService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 現時点ではORDEREDのみキャンセル可。PARTIAL_ALLOCATED/ALLOCATEDは引当解放ロジック実装後に拡張する
    private static final Set<String> CANCELLABLE_STATUSES = Set.of(
            OutboundSlipStatus.ORDERED.getValue()
    );

    private final OutboundSlipRepository outboundSlipRepository;
    private final PickingInstructionLineRepository pickingInstructionLineRepository;
    private final AllocationDetailRepository allocationDetailRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final WarehouseService warehouseService;
    private final PartnerService partnerService;
    private final ProductService productService;
    private final BusinessDateProvider businessDateProvider;
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

    public OutboundSlip findBySlipLineId(Long slipLineId) {
        return outboundSlipRepository.findBySlipLineId(slipLineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OUTBOUND_SLIP_NOT_FOUND",
                        "出荷伝票が見つかりません (slipLineId=" + slipLineId + ")"));
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

    @Transactional
    public OutboundSlip inspect(Long id, InspectOutboundRequest request) {
        OutboundSlip slip = outboundSlipRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OUTBOUND_SLIP_NOT_FOUND",
                        "出荷伝票が見つかりません (id=" + id + ")"));

        // ステータスチェック: PICKING_COMPLETED または INSPECTING のみ検品可
        if (!OutboundSlipStatus.PICKING_COMPLETED.getValue().equals(slip.getStatus())
                && !OutboundSlipStatus.INSPECTING.getValue().equals(slip.getStatus())) {
            throw new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                    "検品登録可能なステータスではありません (status=" + slip.getStatus() + ")");
        }

        // 明細IDのマップを構築
        Map<Long, OutboundSlipLine> lineMap = slip.getLines().stream()
                .collect(Collectors.toMap(OutboundSlipLine::getId, l -> l));

        // 重複lineIdチェック
        Set<Long> requestLineIds = new HashSet<>();
        for (InspectOutboundLineRequest lineReq : request.getLines()) {
            if (!requestLineIds.add(lineReq.getLineId())) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "同一明細IDが重複しています (lineId=" + lineReq.getLineId() + ")");
            }
        }

        // 各明細に対して inspectedQty をセット
        for (InspectOutboundLineRequest lineReq : request.getLines()) {
            OutboundSlipLine line = lineMap.get(lineReq.getLineId());
            if (line == null) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "指定された明細IDは当該伝票に属していません (lineId=" + lineReq.getLineId() + ")");
            }
            line.setInspectedQty(lineReq.getInspectedQty());
        }

        // 伝票ステータスを INSPECTING に更新
        slip.setStatus(OutboundSlipStatus.INSPECTING.getValue());

        OutboundSlip saved = outboundSlipRepository.save(slip);
        log.info("OutboundSlip inspected: id={}, slipNumber={}, lineCount={}",
                id, saved.getSlipNumber(), request.getLines().size());
        return saved;
    }

    @Transactional
    public OutboundSlip ship(Long id, ShipOutboundRequest request) {
        OutboundSlip slip = outboundSlipRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OUTBOUND_SLIP_NOT_FOUND",
                        "出荷伝票が見つかりません (id=" + id + ")"));

        // ステータスチェック: INSPECTING のみ出荷完了可
        if (!OutboundSlipStatus.INSPECTING.getValue().equals(slip.getStatus())) {
            throw new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                    "出荷完了可能なステータスではありません (status=" + slip.getStatus() + ")");
        }

        // 出荷日バリデーション: 未来日は不可
        LocalDate today = businessDateProvider.today();
        if (request.getShippedDate() != null && request.getShippedDate().isAfter(today)) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR",
                    "出荷日は当日以前の日付を指定してください");
        }

        // 全明細が検品済みであることを確認
        for (OutboundSlipLine line : slip.getLines()) {
            if (line.getInspectedQty() == null) {
                throw new BusinessRuleViolationException("VALIDATION_ERROR",
                        "未検品の明細が存在します (lineId=" + line.getId() + ")");
            }
        }

        Long currentUserId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        // 在庫減算: allocation_details を参照して引当元在庫から減算
        // 明細IDリストを収集
        List<Long> slipLineIds = slip.getLines().stream()
                .map(OutboundSlipLine::getId)
                .toList();

        // ピッキング指示明細を取得（引当情報を含む）
        List<PickingInstructionLine> pickingLines =
                pickingInstructionLineRepository.findByOutboundSlipLineIdIn(slipLineIds);

        // 在庫減算のための集約: inventoryをロケーション+商品+ロット+期限で特定
        // デッドロック防止のため inventory_id 昇順で処理
        // allocation_details からロケーション・ロット情報を取得
        List<AllocationDetail> allocationDetails = allocationDetailRepository.findByOutboundSlipId(slip.getId());

        // inventory_id でグループ化して減算量を集約
        Map<Long, Integer> deductionByInventoryId = new TreeMap<>(); // TreeMap for sorted order
        Map<Long, AllocationDetail> detailByInventoryId = new HashMap<>();
        for (AllocationDetail detail : allocationDetails) {
            deductionByInventoryId.merge(detail.getInventoryId(), detail.getAllocatedQty(), Integer::sum);
            detailByInventoryId.putIfAbsent(detail.getInventoryId(), detail);
        }

        // inventory_id 昇順でロックして在庫減算
        for (Map.Entry<Long, Integer> entry : deductionByInventoryId.entrySet()) {
            Long inventoryId = entry.getKey();
            int deductQty = entry.getValue();

            Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                            "在庫が見つかりません (inventoryId=" + inventoryId + ")"));

            if (inventory.getQuantity() < deductQty) {
                throw new BusinessRuleViolationException("INVENTORY_INSUFFICIENT",
                        "在庫が不足しています (inventoryId=" + inventoryId
                                + ", quantity=" + inventory.getQuantity()
                                + ", required=" + deductQty + ")");
            }

            int newQty = inventory.getQuantity() - deductQty;
            int newAllocatedQty = inventory.getAllocatedQty() - deductQty;
            inventory.setQuantity(newQty);
            inventory.setAllocatedQty(Math.max(0, newAllocatedQty));
            inventoryRepository.save(inventory);

            // inventory_movements に OUTBOUND 記録
            AllocationDetail detail = detailByInventoryId.get(inventoryId);
            // ピッキング指示明細からロケーション情報を取得
            String locationCode = pickingLines.stream()
                    .filter(pl -> pl.getLocationId().equals(detail.getLocationId())
                            && pl.getProductId().equals(detail.getProductId()))
                    .map(PickingInstructionLine::getLocationCode)
                    .findFirst()
                    .orElse("");

            // 商品名を取得
            String productCode = slip.getLines().stream()
                    .filter(l -> l.getProductId().equals(detail.getProductId()))
                    .map(OutboundSlipLine::getProductCode)
                    .findFirst()
                    .orElse("");
            String productName = slip.getLines().stream()
                    .filter(l -> l.getProductId().equals(detail.getProductId()))
                    .map(OutboundSlipLine::getProductName)
                    .findFirst()
                    .orElse("");

            InventoryMovement movement = InventoryMovement.builder()
                    .warehouseId(slip.getWarehouseId())
                    .locationId(detail.getLocationId())
                    .locationCode(locationCode)
                    .productId(detail.getProductId())
                    .productCode(productCode)
                    .productName(productName)
                    .unitType(detail.getUnitType())
                    .lotNumber(detail.getLotNumber())
                    .expiryDate(detail.getExpiryDate())
                    .movementType("OUTBOUND")
                    .quantity(-deductQty)
                    .quantityAfter(newQty)
                    .referenceId(slip.getId())
                    .referenceType("OUTBOUND_SLIP")
                    .executedAt(now)
                    .executedBy(currentUserId)
                    .build();
            inventoryMovementRepository.save(movement);

            log.info("Inventory deducted for outbound: inventoryId={}, qty=-{}, after={}",
                    inventoryId, deductQty, newQty);
        }

        // 各明細の shippedQty = inspectedQty、lineStatus = SHIPPED
        for (OutboundSlipLine line : slip.getLines()) {
            line.setShippedQty(line.getInspectedQty() != null ? line.getInspectedQty() : 0);
            line.setLineStatus(OutboundLineStatus.SHIPPED.getValue());
        }

        // 伝票ヘッダー更新
        slip.setStatus(OutboundSlipStatus.SHIPPED.getValue());
        slip.setCarrier(request.getCarrier());
        slip.setTrackingNumber(request.getTrackingNumber());
        if (request.getNote() != null) {
            slip.setNote(request.getNote());
        }
        slip.setShippedAt(now);
        slip.setShippedBy(currentUserId);

        OutboundSlip saved = outboundSlipRepository.save(slip);
        log.info("OutboundSlip shipped: id={}, slipNumber={}", id, saved.getSlipNumber());
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
