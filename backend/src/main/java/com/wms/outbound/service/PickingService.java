package com.wms.outbound.service;

import com.wms.allocation.entity.AllocationDetail;
import com.wms.allocation.entity.UnpackInstruction;
import com.wms.allocation.repository.AllocationDetailRepository;
import com.wms.allocation.repository.UnpackInstructionRepository;
import com.wms.generated.model.CompletePickingLineRequest;
import com.wms.generated.model.CompletePickingRequest;
import com.wms.generated.model.CreatePickingInstructionRequest;
import com.wms.generated.model.OutboundLineStatus;
import com.wms.generated.model.OutboundSlipStatus;
import com.wms.generated.model.PickingInstructionStatus;
import com.wms.generated.model.PickingLineStatus;
import com.wms.master.entity.Location;
import com.wms.master.repository.LocationRepository;
import com.wms.master.service.AreaService;
import com.wms.master.service.WarehouseService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.entity.PickingInstruction;
import com.wms.outbound.entity.PickingInstructionLine;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.outbound.repository.PickingInstructionLineRepository;
import com.wms.outbound.repository.PickingInstructionRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.wms.shared.util.LikeEscapeUtil.escape;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PickingService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PickingInstructionRepository pickingInstructionRepository;
    private final PickingInstructionLineRepository pickingInstructionLineRepository;
    private final OutboundSlipRepository outboundSlipRepository;
    private final AllocationDetailRepository allocationDetailRepository;
    private final UnpackInstructionRepository unpackInstructionRepository;
    private final WarehouseService warehouseService;
    private final AreaService areaService;
    private final LocationRepository locationRepository;
    private final BusinessDateProvider businessDateProvider;

    /**
     * ピッキング指示一覧検索 (API-OUT-011)
     */
    public Page<PickingInstruction> search(Long warehouseId, String instructionNumber,
                                            List<String> statuses,
                                            LocalDate createdDateFrom, LocalDate createdDateTo,
                                            Pageable pageable) {
        warehouseService.findById(warehouseId);

        String escapedNumber = instructionNumber != null ? escape(instructionNumber) : null;

        OffsetDateTime from = createdDateFrom != null
                ? createdDateFrom.atStartOfDay(JST).toOffsetDateTime() : null;
        OffsetDateTime to = createdDateTo != null
                ? createdDateTo.plusDays(1).atStartOfDay(JST).toOffsetDateTime() : null;

        log.debug("PickingInstruction search: warehouseId={}, instructionNumber={}, statuses={}",
                warehouseId, instructionNumber, statuses);
        return pickingInstructionRepository.search(
                warehouseId, escapedNumber, statuses, from, to, pageable);
    }

    /**
     * ピッキング指示詳細取得 (API-OUT-013)
     */
    public PickingInstruction findByIdWithLines(Long id) {
        return pickingInstructionRepository.findByIdWithLines(id)
                .orElseThrow(() -> {
                    // SEC-R2-Min-1 (OWASP A09): 追跡用 id はログへ
                    log.info("PickingInstruction not found: id={}", id);
                    return new ResourceNotFoundException(
                            "PICKING_NOT_FOUND",
                            "ピッキング指示が見つかりません");
                });
    }

    /**
     * ピッキング指示の明細件数を取得
     */
    public long countLinesByInstructionId(Long pickingInstructionId) {
        return pickingInstructionRepository.countLinesByInstructionId(pickingInstructionId);
    }

    public Map<Long, Integer> sumPickedQtyBySlipLineIds(List<Long> slipLineIds) {
        if (slipLineIds == null || slipLineIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = pickingInstructionLineRepository.findByOutboundSlipLineIdIn(slipLineIds).stream()
                .collect(Collectors.groupingBy(
                        PickingInstructionLine::getOutboundSlipLineId,
                        Collectors.summingInt(PickingInstructionLine::getQtyPicked)));
        result.values().removeIf(v -> v == 0);
        return result;
    }

    /**
     * ピッキング指示作成 (API-OUT-012)
     */
    @Transactional
    public PickingInstruction createPickingInstruction(CreatePickingInstructionRequest request) {
        List<Long> slipIds = request.getSlipIds();

        // エリア存在チェック
        Long areaId = request.getAreaId();
        if (areaId != null) {
            areaService.findById(areaId);
        }

        // 伝票の存在チェック・ステータスチェック
        Long warehouseId = null;
        List<OutboundSlip> slips = new ArrayList<>();
        for (Long slipId : slipIds) {
            OutboundSlip slip = outboundSlipRepository.findByIdWithLines(slipId)
                    .orElseThrow(() -> {
                        log.info("OutboundSlip not found during picking create: slipId={}", slipId);
                        return new ResourceNotFoundException(
                                "OUTBOUND_SLIP_NOT_FOUND",
                                "出荷伝票が見つかりません");
                    });

            if (!OutboundSlipStatus.ALLOCATED.getValue().equals(slip.getStatus())) {
                log.info("OutboundSlip not ALLOCATED during picking create: slipId={}, status={}",
                        slipId, slip.getStatus());
                throw new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                        "現在のステータスではピッキング指示を作成できません");
            }

            if (warehouseId == null) {
                warehouseId = slip.getWarehouseId();
            }
            slips.add(slip);
        }

        // ばらし指示の未完了チェック
        for (OutboundSlip slip : slips) {
            List<UnpackInstruction> instructed = unpackInstructionRepository
                    .findByOutboundSlipIdAndStatus(slip.getId(), "INSTRUCTED");
            if (!instructed.isEmpty()) {
                log.info("Picking creation blocked: pending unpack instructions exist: slipId={}", slip.getId());
                throw new InvalidStateTransitionException("UNPACK_NOT_COMPLETED",
                        "未完了のばらし指示が存在するため、ピッキング指示を作成できません");
            }
        }

        // 引当明細を取得してピッキング明細に展開
        List<PickingLineCandidate> candidates = new ArrayList<>();
        for (OutboundSlip slip : slips) {
            List<AllocationDetail> allocations = allocationDetailRepository.findByOutboundSlipId(slip.getId());

            for (AllocationDetail alloc : allocations) {
                // areaId が指定されている場合、ロケーションがそのエリアに属するかチェック
                if (areaId != null) {
                    Location location = locationRepository.findById(alloc.getLocationId()).orElse(null);
                    if (location == null || !areaId.equals(location.getAreaId())) {
                        continue;
                    }
                }

                Location location = locationRepository.findById(alloc.getLocationId())
                        .orElseThrow(() -> {
                            log.info("Location not found during picking create: locationId={}", alloc.getLocationId());
                            return new ResourceNotFoundException(
                                    "LOCATION_NOT_FOUND",
                                    "ロケーションが見つかりません");
                        });

                // 出荷明細からproduct_code/name取得
                OutboundSlipLine slipLine = slip.getLines().stream()
                        .filter(l -> l.getId().equals(alloc.getOutboundSlipLineId()))
                        .findFirst()
                        .orElse(null);

                String productName = slipLine != null ? slipLine.getProductName() : "";
                String productCode = slipLine != null ? slipLine.getProductCode() : "";

                candidates.add(new PickingLineCandidate(
                        alloc.getOutboundSlipLineId(),
                        alloc.getLocationId(),
                        location.getLocationCode(),
                        alloc.getProductId(),
                        productCode,
                        productName,
                        alloc.getUnitType(),
                        alloc.getLotNumber(),
                        alloc.getExpiryDate(),
                        alloc.getAllocatedQty()));
            }
        }

        if (candidates.isEmpty()) {
            throw new BusinessRuleViolationException("PICKING_NO_ALLOCATION_CANDIDATES",
                    "ピッキング対象の引当明細が存在しません");
        }

        // 指示番号採番
        String dateStr = businessDateProvider.today().format(DATE_FORMAT);
        String instructionNumber = generateInstructionNumber(dateStr);

        // ヘッダ構築
        PickingInstruction instruction = PickingInstruction.builder()
                .instructionNumber(instructionNumber)
                .warehouseId(warehouseId)
                .areaId(areaId)
                .status(PickingInstructionStatus.CREATED.getValue())
                .build();

        // 明細追加（行番号付与）
        int lineNo = 1;
        for (PickingLineCandidate c : candidates) {
            PickingInstructionLine line = PickingInstructionLine.builder()
                    .lineNo(lineNo++)
                    .outboundSlipLineId(c.outboundSlipLineId())
                    .locationId(c.locationId())
                    .locationCode(c.locationCode())
                    .productId(c.productId())
                    .productCode(c.productCode())
                    .productName(c.productName())
                    .unitType(c.unitType())
                    .lotNumber(c.lotNumber())
                    .expiryDate(c.expiryDate())
                    .qtyToPick(c.allocatedQty())
                    .qtyPicked(0)
                    .lineStatus(PickingLineStatus.PENDING.getValue())
                    .build();
            instruction.addLine(line);
        }

        PickingInstruction saved = pickingInstructionRepository.save(instruction);
        log.info("PickingInstruction created: instructionNumber={}, warehouseId={}, lineCount={}",
                saved.getInstructionNumber(), saved.getWarehouseId(), saved.getLines().size());
        return saved;
    }

    /**
     * ピッキング完了登録 (API-OUT-014)
     */
    @Transactional
    public PickingInstruction completePickingInstruction(Long id, CompletePickingRequest request) {
        PickingInstruction instruction = pickingInstructionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.info("PickingInstruction not found on complete: id={}", id);
                    return new ResourceNotFoundException(
                            "PICKING_NOT_FOUND",
                            "ピッキング指示が見つかりません");
                });

        if (PickingInstructionStatus.COMPLETED.getValue().equals(instruction.getStatus())) {
            log.info("PickingInstruction already completed: id={}", id);
            throw new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                    "既に完了済みのピッキング指示です");
        }

        // 明細IDのマップを作成
        Map<Long, PickingInstructionLine> lineMap = instruction.getLines().stream()
                .collect(Collectors.toMap(PickingInstructionLine::getId, l -> l));

        Long currentUserId = getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        // リクエストの各行を処理
        for (CompletePickingLineRequest lineReq : request.getLines()) {
            PickingInstructionLine line = lineMap.get(lineReq.getLineId());
            if (line == null) {
                log.info("Picking line not found: instructionId={}, lineId={}", id, lineReq.getLineId());
                throw new BusinessRuleViolationException("PICKING_LINE_NOT_FOUND",
                        "指定されたlineIdが当該ピッキング指示に存在しません");
            }

            if (lineReq.getQtyPicked() > line.getQtyToPick()) {
                log.warn("Picking qty exceeded: instructionId={}, lineId={}, qtyPicked={}, qtyToPick={}",
                        id, lineReq.getLineId(), lineReq.getQtyPicked(), line.getQtyToPick());
                throw new BusinessRuleViolationException("PICKING_QTY_EXCEEDED",
                        "ピッキング完了数量がピッキング予定数量を超えています");
            }

            line.setQtyPicked(lineReq.getQtyPicked());
            line.setLineStatus(PickingLineStatus.COMPLETED.getValue());
            line.setCompletedAt(now);
            line.setCompletedBy(currentUserId);
        }

        // ステータス判定
        boolean allCompleted = instruction.getLines().stream()
                .allMatch(l -> PickingLineStatus.COMPLETED.getValue().equals(l.getLineStatus()));

        if (allCompleted) {
            instruction.setStatus(PickingInstructionStatus.COMPLETED.getValue());
            instruction.setCompletedAt(now);
            instruction.setCompletedBy(currentUserId);

            // 関連する出荷伝票のステータスを更新
            updateOutboundSlipsToPickingCompleted(instruction);
        } else {
            // 初めての完了登録の場合 IN_PROGRESS に
            if (PickingInstructionStatus.CREATED.getValue().equals(instruction.getStatus())) {
                instruction.setStatus(PickingInstructionStatus.IN_PROGRESS.getValue());
            }
        }

        PickingInstruction saved = pickingInstructionRepository.save(instruction);
        log.info("PickingInstruction updated: id={}, instructionNumber={}, status={}",
                saved.getId(), saved.getInstructionNumber(), saved.getStatus());
        return saved;
    }

    private void updateOutboundSlipsToPickingCompleted(PickingInstruction instruction) {
        // ピッキング明細に紐づく出荷明細IDを抽出
        Set<Long> outboundSlipLineIds = instruction.getLines().stream()
                .map(PickingInstructionLine::getOutboundSlipLineId)
                .collect(Collectors.toSet());

        // 出荷伝票をロードして更新
        Set<Long> processedSlipIds = new HashSet<>();
        for (Long slipLineId : outboundSlipLineIds) {
            OutboundSlip slip = outboundSlipRepository.findBySlipLineId(slipLineId)
                    .orElse(null);
            if (slip != null && processedSlipIds.add(slip.getId())) {
                for (OutboundSlipLine slipLine : slip.getLines()) {
                    if (outboundSlipLineIds.contains(slipLine.getId())) {
                        slipLine.setLineStatus(OutboundLineStatus.PICKING_COMPLETED.getValue());
                    }
                }
                slip.setStatus(OutboundSlipStatus.PICKING_COMPLETED.getValue());
                outboundSlipRepository.save(slip);
                log.info("OutboundSlip updated to PICKING_COMPLETED: id={}, slipNumber={}",
                        slip.getId(), slip.getSlipNumber());
            }
        }
    }

    private String generateInstructionNumber(String dateStr) {
        int maxSeq = pickingInstructionRepository.findMaxSequenceByDate(dateStr);
        return String.format("PIC-%s-%03d", dateStr, maxSeq + 1);
    }

    private Long getCurrentUserId() {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }

    private record PickingLineCandidate(
            Long outboundSlipLineId,
            Long locationId,
            String locationCode,
            Long productId,
            String productCode,
            String productName,
            String unitType,
            String lotNumber,
            LocalDate expiryDate,
            Integer allocatedQty) {}
}
