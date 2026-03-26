package com.wms.allocation.service;

import com.wms.allocation.entity.AllocationDetail;
import com.wms.allocation.entity.UnpackInstruction;
import com.wms.allocation.repository.AllocationDetailRepository;
import com.wms.allocation.repository.UnpackInstructionRepository;
import com.wms.generated.model.ExecuteAllocationRequest;
import com.wms.generated.model.ReleaseAllocationRequest;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.master.entity.Location;
import com.wms.master.entity.Product;
import com.wms.master.repository.LocationRepository;
import com.wms.master.service.ProductService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.wms.shared.util.LikeEscapeUtil.escape;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AllocationService {

    private static final Set<String> ALLOCATABLE_STATUSES = Set.of("ORDERED", "PARTIAL_ALLOCATED");
    private static final Set<String> RELEASABLE_STATUSES = Set.of("ALLOCATED", "PARTIAL_ALLOCATED");
    private final OutboundSlipRepository outboundSlipRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final AllocationDetailRepository allocationDetailRepository;
    private final UnpackInstructionRepository unpackInstructionRepository;
    private final ProductService productService;
    private final LocationRepository locationRepository;

    // --- 引当対象受注一覧 ---

    public Page<OutboundSlip> searchOrders(Long warehouseId, List<String> statuses,
                                            LocalDate shippingDateFrom, LocalDate shippingDateTo,
                                            String partnerName, Pageable pageable) {
        List<String> effectiveStatuses = statuses != null && !statuses.isEmpty()
                ? statuses
                : List.of("ORDERED", "PARTIAL_ALLOCATED");

        String escapedPartnerName = partnerName != null ? escape(partnerName) : null;

        log.debug("searchOrders: warehouseId={}, statuses={}, dateFrom={}, dateTo={}, partnerName={}",
                warehouseId, effectiveStatuses, shippingDateFrom, shippingDateTo, partnerName);

        return outboundSlipRepository.searchForAllocation(
                warehouseId, effectiveStatuses,
                shippingDateFrom, shippingDateTo,
                escapedPartnerName, pageable);
    }

    // --- 引当済み受注一覧 ---

    public Page<OutboundSlip> searchAllocatedOrders(Pageable pageable) {
        return outboundSlipRepository.findByStatusIn(
                List.of("ALLOCATED", "PARTIAL_ALLOCATED"), pageable);
    }

    // --- ばらし指示一覧 ---

    public Page<UnpackInstruction> searchUnpackInstructions(Long outboundSlipId, String status,
                                                             Pageable pageable) {
        return unpackInstructionRepository.search(outboundSlipId, status, pageable);
    }

    public long countLinesBySlipId(Long slipId) {
        return outboundSlipRepository.countLinesBySlipId(slipId);
    }

    public long countAllocatedLinesBySlipId(Long slipId) {
        return outboundSlipRepository.countAllocatedLinesBySlipId(slipId);
    }

    // --- 引当数量集計 ---

    public Map<Long, Integer> sumAllocatedQtyBySlipId(Long slipId) {
        return allocationDetailRepository.findByOutboundSlipId(slipId).stream()
                .collect(Collectors.groupingBy(
                        AllocationDetail::getOutboundSlipLineId,
                        Collectors.summingInt(AllocationDetail::getAllocatedQty)));
    }

    // --- 引当実行 ---

    @Transactional
    public AllocationResult executeAllocation(ExecuteAllocationRequest request) {
        List<Long> slipIds = request.getOutboundSlipIds();
        List<AllocatedSlipInfo> allocatedSlips = new ArrayList<>();
        List<UnpackInstructionInfo> unpackInstructions = new ArrayList<>();
        List<UnallocatedLineInfo> unallocatedLines = new ArrayList<>();

        for (Long slipId : slipIds) {
            OutboundSlip slip = outboundSlipRepository.findByIdForUpdate(slipId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "OUTBOUND_SLIP_NOT_FOUND",
                            "出荷伝票が見つかりません (id=" + slipId + ")"));

            if (!ALLOCATABLE_STATUSES.contains(slip.getStatus())) {
                throw new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                        "引当可能なステータスではありません (id=" + slipId + ", status=" + slip.getStatus() + ")");
            }

            List<AllocatedLineInfo> allocatedLines = new ArrayList<>();
            boolean allLinesAllocated = true;

            for (OutboundSlipLine line : slip.getLines()) {
                if ("ALLOCATED".equals(line.getLineStatus())) {
                    // 既に引当済みの明細はスキップ
                    allocatedLines.add(new AllocatedLineInfo(
                            line.getLineNo(), line.getProductCode(), line.getProductName(),
                            line.getOrderedQty(), line.getOrderedQty()));
                    continue;
                }

                Product product = productService.findById(line.getProductId());

                // 出荷禁止商品チェック
                if (Boolean.TRUE.equals(product.getShipmentStopFlag())) {
                    log.warn("Skipping allocation for shipment-stopped product: productId={}", product.getId());
                    allLinesAllocated = false;
                    unallocatedLines.add(new UnallocatedLineInfo(
                            slip.getId(), slip.getSlipNumber(),
                            line.getLineNo(), line.getProductCode(), line.getProductName(),
                            line.getOrderedQty()));
                    continue;
                }

                int neededQty = line.getOrderedQty();
                String requestedUnitType = line.getUnitType();

                // 同一倉庫・同一商品の有効在庫を FEFO/FIFO 順で検索
                List<Inventory> availableStocks = inventoryRepository.findAvailableStock(
                        slip.getWarehouseId(), line.getProductId());

                int totalAllocated = allocateFromStocks(
                        availableStocks, requestedUnitType, neededQty,
                        slip, line, product, unpackInstructions);

                if (totalAllocated >= neededQty) {
                    line.setLineStatus("ALLOCATED");
                    allocatedLines.add(new AllocatedLineInfo(
                            line.getLineNo(), line.getProductCode(), line.getProductName(),
                            line.getOrderedQty(), neededQty));
                } else if (totalAllocated > 0) {
                    line.setLineStatus("PARTIAL_ALLOCATED");
                    allLinesAllocated = false;
                    allocatedLines.add(new AllocatedLineInfo(
                            line.getLineNo(), line.getProductCode(), line.getProductName(),
                            line.getOrderedQty(), totalAllocated));
                    unallocatedLines.add(new UnallocatedLineInfo(
                            slip.getId(), slip.getSlipNumber(),
                            line.getLineNo(), line.getProductCode(), line.getProductName(),
                            neededQty - totalAllocated));
                } else {
                    allLinesAllocated = false;
                    unallocatedLines.add(new UnallocatedLineInfo(
                            slip.getId(), slip.getSlipNumber(),
                            line.getLineNo(), line.getProductCode(), line.getProductName(),
                            neededQty));
                }
            }

            // 伝票ステータス更新
            boolean anyAllocated = slip.getLines().stream()
                    .anyMatch(l -> "ALLOCATED".equals(l.getLineStatus())
                            || "PARTIAL_ALLOCATED".equals(l.getLineStatus()));
            if (allLinesAllocated) {
                slip.setStatus("ALLOCATED");
            } else if (anyAllocated) {
                slip.setStatus("PARTIAL_ALLOCATED");
            }
            // anyAllocated==false の場合はステータス変更なし（ORDERED のまま）

            outboundSlipRepository.save(slip);

            if (!allocatedLines.isEmpty()) {
                String newStatus = allLinesAllocated ? "ALLOCATED" : "PARTIAL_ALLOCATED";
                allocatedSlips.add(new AllocatedSlipInfo(
                        slip.getId(), slip.getSlipNumber(), newStatus, allocatedLines));
            }
        }

        int allocatedCount = (int) allocatedSlips.stream()
                .filter(s -> "ALLOCATED".equals(s.status()))
                .count();

        log.info("Allocation executed: allocatedCount={}, unpackInstructions={}, unallocatedLines={}",
                allocatedCount, unpackInstructions.size(), unallocatedLines.size());

        return new AllocationResult(allocatedCount, allocatedSlips, unpackInstructions, unallocatedLines);
    }

    private int allocateFromStocks(List<Inventory> availableStocks, String requestedUnitType,
                                    int neededQty, OutboundSlip slip, OutboundSlipLine line,
                                    Product product, List<UnpackInstructionInfo> unpackInstructions) {
        int totalAllocated = 0;
        int remaining = neededQty;

        // Phase 1: 同一荷姿の在庫を優先引当
        for (Inventory stock : availableStocks) {
            if (remaining <= 0) break;
            if (!stock.getUnitType().equals(requestedUnitType)) continue;

            int available = stock.getQuantity() - stock.getAllocatedQty();
            int allocateQty = Math.min(available, remaining);
            if (allocateQty <= 0) continue;

            Inventory locked = inventoryRepository.findByIdForUpdate(stock.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                            "在庫が見つかりません (id=" + stock.getId() + ")"));

            // 再計算（ロック後に在庫が変わっている可能性）
            available = locked.getQuantity() - locked.getAllocatedQty();
            allocateQty = Math.min(available, remaining);
            if (allocateQty <= 0) continue;

            locked.setAllocatedQty(locked.getAllocatedQty() + allocateQty);
            inventoryRepository.save(locked);

            AllocationDetail detail = AllocationDetail.builder()
                    .outboundSlipId(slip.getId())
                    .outboundSlipLineId(line.getId())
                    .inventoryId(locked.getId())
                    .locationId(locked.getLocationId())
                    .productId(locked.getProductId())
                    .unitType(locked.getUnitType())
                    .lotNumber(locked.getLotNumber())
                    .expiryDate(locked.getExpiryDate())
                    .allocatedQty(allocateQty)
                    .warehouseId(slip.getWarehouseId())
                    .build();
            allocationDetailRepository.save(detail);

            totalAllocated += allocateQty;
            remaining -= allocateQty;
        }

        // Phase 2: 同一荷姿不足時、上位荷姿からばらし指示生成
        if (remaining > 0) {
            totalAllocated += allocateFromUpperUnitTypes(
                    availableStocks, requestedUnitType, remaining,
                    slip, line, product, unpackInstructions);
        }

        return totalAllocated;
    }

    private int allocateFromUpperUnitTypes(List<Inventory> availableStocks, String requestedUnitType,
                                            int remaining, OutboundSlip slip, OutboundSlipLine line,
                                            Product product, List<UnpackInstructionInfo> unpackInstructions) {
        int totalAllocated = 0;

        for (Inventory stock : availableStocks) {
            if (remaining <= 0) break;

            String stockUnitType = stock.getUnitType();
            // 上位荷姿のみ対象
            int conversionRate = getConversionRate(stockUnitType, requestedUnitType, product);
            if (conversionRate <= 0) continue;

            int available = stock.getQuantity() - stock.getAllocatedQty();
            if (available <= 0) continue;

            // 必要なばらし元数量を計算
            int neededFromQty = (int) Math.ceil((double) remaining / conversionRate);
            int fromQty = Math.min(available, neededFromQty);
            int toQty = fromQty * conversionRate;
            int allocateQty = Math.min(toQty, remaining);

            Inventory locked = inventoryRepository.findByIdForUpdate(stock.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                            "在庫が見つかりません (id=" + stock.getId() + ")"));

            available = locked.getQuantity() - locked.getAllocatedQty();
            fromQty = Math.min(available, neededFromQty);
            if (fromQty <= 0) continue;
            toQty = fromQty * conversionRate;
            allocateQty = Math.min(toQty, remaining);

            // 元在庫のallocated_qtyを仮確保
            locked.setAllocatedQty(locked.getAllocatedQty() + fromQty);
            inventoryRepository.save(locked);

            // ばらし指示生成
            UnpackInstruction unpack = UnpackInstruction.builder()
                    .outboundSlipId(slip.getId())
                    .locationId(locked.getLocationId())
                    .productId(locked.getProductId())
                    .sourceInventoryId(locked.getId())
                    .fromUnitType(stockUnitType)
                    .fromQty(fromQty)
                    .toUnitType(requestedUnitType)
                    .toQty(toQty)
                    .status("INSTRUCTED")
                    .warehouseId(slip.getWarehouseId())
                    .build();
            unpackInstructionRepository.save(unpack);

            // 引当記録（ばらし先在庫はまだないので元在庫IDで仮記録）
            AllocationDetail detail = AllocationDetail.builder()
                    .outboundSlipId(slip.getId())
                    .outboundSlipLineId(line.getId())
                    .inventoryId(locked.getId())
                    .locationId(locked.getLocationId())
                    .productId(locked.getProductId())
                    .unitType(requestedUnitType)
                    .lotNumber(locked.getLotNumber())
                    .expiryDate(locked.getExpiryDate())
                    .allocatedQty(allocateQty)
                    .warehouseId(slip.getWarehouseId())
                    .build();
            allocationDetailRepository.save(detail);

            unpackInstructions.add(new UnpackInstructionInfo(
                    unpack.getId(), product.getProductCode(), product.getProductName(),
                    stockUnitType, requestedUnitType, fromQty));

            totalAllocated += allocateQty;
            remaining -= allocateQty;
        }

        return totalAllocated;
    }

    /**
     * 荷姿変換レートを返す。上位→下位の変換のみ正の値を返す。
     * CASE→PIECE = caseQuantity
     * BALL→PIECE = ballQuantity
     * CASE→BALL = caseQuantity / ballQuantity
     */
    int getConversionRate(String fromUnitType, String toUnitType, Product product) {
        if ("CASE".equals(fromUnitType) && "PIECE".equals(toUnitType)) {
            return product.getCaseQuantity();
        }
        if ("BALL".equals(fromUnitType) && "PIECE".equals(toUnitType)) {
            return product.getBallQuantity();
        }
        if ("CASE".equals(fromUnitType) && "BALL".equals(toUnitType)) {
            if (product.getBallQuantity() == 0) return 0;
            return product.getCaseQuantity() / product.getBallQuantity();
        }
        return 0; // 同一荷姿 or 下位→上位は対象外
    }

    // --- ばらし完了 ---

    @Transactional
    public UnpackCompletionInfo completeUnpackInstruction(Long id) {
        UnpackInstruction unpack = unpackInstructionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BREAKDOWN_INSTRUCTION_NOT_FOUND",
                        "ばらし指示が見つかりません (id=" + id + ")"));

        if (!"INSTRUCTED".equals(unpack.getStatus())) {
            throw new InvalidStateTransitionException("ALREADY_COMPLETED",
                    "既に完了済みのばらし指示です (id=" + id + ", status=" + unpack.getStatus() + ")");
        }

        Product product = productService.findById(unpack.getProductId());
        Location location = locationRepository.findById(unpack.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("LOCATION_NOT_FOUND",
                        "ロケーションが見つかりません (id=" + unpack.getLocationId() + ")"));

        Long currentUserId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        // Step1-2: ばらし元在庫のallocated_qtyとquantityを減算
        if (unpack.getSourceInventoryId() == null) {
            throw new BusinessRuleViolationException("SOURCE_INVENTORY_ID_MISSING",
                    "ばらし指示に元在庫IDが設定されていません (unpackId=" + unpack.getId() + ")");
        }
        Inventory lockedSource = inventoryRepository.findByIdForUpdate(unpack.getSourceInventoryId())
                .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                        "ばらし元在庫が見つかりません (id=" + unpack.getSourceInventoryId() + ")"));

        lockedSource.setAllocatedQty(lockedSource.getAllocatedQty() - unpack.getFromQty());
        lockedSource.setQuantity(lockedSource.getQuantity() - unpack.getFromQty());
        inventoryRepository.save(lockedSource);

        int sourceQtyAfter = lockedSource.getQuantity();

        // Step3: ばらし先在庫をUPSERTし、quantity加算
        Inventory targetInventory = inventoryRepository
                .findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                        unpack.getLocationId(), unpack.getProductId(),
                        unpack.getToUnitType(), lockedSource.getLotNumber(), lockedSource.getExpiryDate())
                .orElse(null);

        int targetQtyAfter;
        if (targetInventory != null) {
            Inventory lockedTarget = inventoryRepository.findByIdForUpdate(targetInventory.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_NOT_FOUND",
                            "ばらし先在庫が見つかりません"));
            lockedTarget.setQuantity(lockedTarget.getQuantity() + unpack.getToQty());

            // Step4: ばらし先allocated_qty加算（引当分）
            // allocation_detailsで仮記録された分をばらし先に移す
            List<AllocationDetail> details = allocationDetailRepository.findByOutboundSlipId(unpack.getOutboundSlipId());
            int allocatedForThisUnpack = 0;
            for (AllocationDetail detail : details) {
                if (detail.getInventoryId().equals(lockedSource.getId())
                        && detail.getUnitType().equals(unpack.getToUnitType())) {
                    detail.setInventoryId(lockedTarget.getId());
                    allocationDetailRepository.save(detail);
                    allocatedForThisUnpack += detail.getAllocatedQty();
                }
            }
            lockedTarget.setAllocatedQty(lockedTarget.getAllocatedQty() + allocatedForThisUnpack);
            inventoryRepository.save(lockedTarget);
            targetQtyAfter = lockedTarget.getQuantity();
        } else {
            // allocation_detailsの引当数を計算
            List<AllocationDetail> details = allocationDetailRepository.findByOutboundSlipId(unpack.getOutboundSlipId());
            int allocatedForThisUnpack = 0;
            for (AllocationDetail detail : details) {
                if (detail.getInventoryId().equals(lockedSource.getId())
                        && detail.getUnitType().equals(unpack.getToUnitType())) {
                    allocatedForThisUnpack += detail.getAllocatedQty();
                }
            }

            Inventory newTarget = Inventory.builder()
                    .warehouseId(unpack.getWarehouseId())
                    .locationId(unpack.getLocationId())
                    .productId(unpack.getProductId())
                    .unitType(unpack.getToUnitType())
                    .lotNumber(lockedSource.getLotNumber())
                    .expiryDate(lockedSource.getExpiryDate())
                    .quantity(unpack.getToQty())
                    .allocatedQty(allocatedForThisUnpack)
                    .build();
            Inventory savedTarget = inventoryRepository.save(newTarget);
            targetQtyAfter = savedTarget.getQuantity();

            // allocation_detailsのinventory_idを付け替え
            for (AllocationDetail detail : details) {
                if (detail.getInventoryId().equals(lockedSource.getId())
                        && detail.getUnitType().equals(unpack.getToUnitType())) {
                    detail.setInventoryId(savedTarget.getId());
                    allocationDetailRepository.save(detail);
                }
            }
        }

        // inventory_movements記録
        InventoryMovement outMovement = InventoryMovement.builder()
                .warehouseId(unpack.getWarehouseId())
                .locationId(unpack.getLocationId())
                .locationCode(location.getLocationCode())
                .productId(unpack.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getProductName())
                .unitType(unpack.getFromUnitType())
                .movementType("BREAKDOWN_OUT")
                .quantity(-unpack.getFromQty())
                .quantityAfter(sourceQtyAfter)
                .referenceId(unpack.getId())
                .referenceType("UNPACK_INSTRUCTION")
                .executedAt(now)
                .executedBy(currentUserId)
                .build();
        inventoryMovementRepository.save(outMovement);

        InventoryMovement inMovement = InventoryMovement.builder()
                .warehouseId(unpack.getWarehouseId())
                .locationId(unpack.getLocationId())
                .locationCode(location.getLocationCode())
                .productId(unpack.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getProductName())
                .unitType(unpack.getToUnitType())
                .movementType("BREAKDOWN_IN")
                .quantity(unpack.getToQty())
                .quantityAfter(targetQtyAfter)
                .referenceId(unpack.getId())
                .referenceType("UNPACK_INSTRUCTION")
                .executedAt(now)
                .executedBy(currentUserId)
                .build();
        inventoryMovementRepository.save(inMovement);

        // ステータス更新
        unpack.setStatus("COMPLETED");
        unpack.setCompletedAt(now);
        unpack.setCompletedBy(currentUserId);
        unpackInstructionRepository.save(unpack);

        log.info("UnpackInstruction completed: id={}, fromUnitType={}, toUnitType={}, fromQty={}, toQty={}",
                id, unpack.getFromUnitType(), unpack.getToUnitType(), unpack.getFromQty(), unpack.getToQty());

        List<MovementInfo> movements = List.of(
                new MovementInfo("BREAKDOWN_OUT", product.getProductCode(), unpack.getFromUnitType(),
                        -unpack.getFromQty(), location.getLocationCode()),
                new MovementInfo("BREAKDOWN_IN", product.getProductCode(), unpack.getToUnitType(),
                        unpack.getToQty(), location.getLocationCode())
        );

        return new UnpackCompletionInfo(unpack.getId(), "COMPLETED", now, movements);
    }

    // --- 引当解放 ---

    @Transactional
    public AllocationReleaseInfo releaseAllocation(ReleaseAllocationRequest request) {
        List<Long> slipIds = request.getOutboundSlipIds();
        List<ReleasedSlipInfo> releasedSlips = new ArrayList<>();

        for (Long slipId : slipIds) {
            OutboundSlip slip = outboundSlipRepository.findByIdForUpdate(slipId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "OUTBOUND_SLIP_NOT_FOUND",
                            "出荷伝票が見つかりません (id=" + slipId + ")"));

            if (!RELEASABLE_STATUSES.contains(slip.getStatus())) {
                throw new InvalidStateTransitionException("RELEASE_NOT_ALLOWED",
                        "引当解放可能なステータスではありません (id=" + slipId + ", status=" + slip.getStatus() + ")");
            }

            String previousStatus = slip.getStatus();

            // 各allocation_detailについてinventories.allocated_qty減算
            List<AllocationDetail> details = allocationDetailRepository.findByOutboundSlipId(slipId);
            for (AllocationDetail detail : details) {
                Inventory locked = inventoryRepository.findByIdForUpdate(detail.getInventoryId())
                        .orElse(null);
                if (locked != null) {
                    locked.setAllocatedQty(Math.max(0, locked.getAllocatedQty() - detail.getAllocatedQty()));
                    inventoryRepository.save(locked);
                }
            }

            // allocation_details削除
            allocationDetailRepository.deleteByOutboundSlipId(slipId);

            // 未完了unpack_instructions削除
            unpackInstructionRepository.deleteInstructedByOutboundSlipId(slipId);

            // 明細ステータスをORDEREDに戻す
            for (OutboundSlipLine line : slip.getLines()) {
                line.setLineStatus("ORDERED");
            }

            // 伝票ステータスをORDEREDに戻す
            slip.setStatus("ORDERED");
            outboundSlipRepository.save(slip);

            releasedSlips.add(new ReleasedSlipInfo(slipId, slip.getSlipNumber(), previousStatus, "ORDERED"));

            log.info("Allocation released: slipId={}, slipNumber={}, previousStatus={}",
                    slipId, slip.getSlipNumber(), previousStatus);
        }

        return new AllocationReleaseInfo(releasedSlips.size(), releasedSlips);
    }

    private Long getCurrentUserId() {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }

    // --- Result records ---

    public record AllocationResult(
            int allocatedCount,
            List<AllocatedSlipInfo> allocatedSlips,
            List<UnpackInstructionInfo> unpackInstructions,
            List<UnallocatedLineInfo> unallocatedLines) {}

    public record AllocatedSlipInfo(
            Long outboundSlipId, String slipNumber, String status,
            List<AllocatedLineInfo> allocatedLines) {}

    public record AllocatedLineInfo(
            int lineNo, String productCode, String productName,
            int orderedQty, int allocatedQty) {}

    public record UnpackInstructionInfo(
            Long id, String productCode, String productName,
            String fromUnitType, String toUnitType, int quantity) {}

    public record UnallocatedLineInfo(
            Long outboundSlipId, String slipNumber,
            int lineNo, String productCode, String productName,
            int shortageQty) {}

    public record UnpackCompletionInfo(
            Long id, String status, OffsetDateTime completedAt,
            List<MovementInfo> movements) {}

    public record MovementInfo(
            String movementType, String productCode, String unitType,
            int quantity, String locationCode) {}

    public record AllocationReleaseInfo(
            int releasedCount, List<ReleasedSlipInfo> releasedSlips) {}

    public record ReleasedSlipInfo(
            Long outboundSlipId, String slipNumber,
            String previousStatus, String newStatus) {}
}
