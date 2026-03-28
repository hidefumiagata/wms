package com.wms.inventory.controller;

import com.wms.generated.api.InventoryApi;
import com.wms.generated.model.BreakdownInventoryRequest;
import com.wms.generated.model.BreakdownInventoryResponse;
import com.wms.generated.model.ConfirmStocktakeResponse;
import com.wms.generated.model.CorrectionHistoryItem;
import com.wms.generated.model.LocationCapacityResponse;
import com.wms.generated.model.CorrectionInventoryRequest;
import com.wms.generated.model.CorrectionInventoryResponse;
import com.wms.generated.model.InventoryLocationItem;
import com.wms.generated.model.InventoryLocationPageResponse;
import com.wms.generated.model.InventoryProductSummaryItem;
import com.wms.generated.model.InventoryProductSummaryPageResponse;
import com.wms.generated.model.ListInventory200Response;
import com.wms.generated.model.MoveInventoryRequest;
import com.wms.generated.model.MoveInventoryResponse;
import com.wms.generated.model.SaveStocktakeLinesRequest;
import com.wms.generated.model.SaveStocktakeLinesResponse;
import com.wms.generated.model.StartStocktakeRequest;
import com.wms.generated.model.StartStocktakeResponse;
import com.wms.generated.model.StocktakeDetail;
import com.wms.generated.model.StocktakeDetailLines;
import com.wms.generated.model.StocktakeLineItem;
import com.wms.generated.model.StocktakeStatus;
import com.wms.generated.model.StocktakeSummary;
import com.wms.generated.model.StocktakeSummaryPageResponse;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.UnitType;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.service.InventoryBreakdownService;
import com.wms.inventory.service.InventoryCorrectionService;
import com.wms.inventory.service.InventoryMoveService;
import com.wms.inventory.service.InventoryQueryService;
import com.wms.inventory.service.StocktakeQueryService;
import com.wms.inventory.service.StocktakeService;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.WarehouseService;
import com.wms.system.service.UserService;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class InventoryController implements InventoryApi {

    private static final Set<String> LOCATION_SORT_PROPERTIES = Set.of(
            "locationCode", "productCode", "unitType", "quantity", "updatedAt");
    private static final Set<String> SUMMARY_SORT_PROPERTIES = Set.of(
            "productCode", "productName");

    private final InventoryQueryService inventoryQueryService;
    private final InventoryMoveService inventoryMoveService;
    private final InventoryBreakdownService inventoryBreakdownService;
    private final InventoryCorrectionService inventoryCorrectionService;
    private final StocktakeQueryService stocktakeQueryService;
    private final WarehouseService warehouseService;
    private final UserService userService;
    private final StocktakeService stocktakeService;

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Override
    public ResponseEntity<ListInventory200Response> listInventory(
            Long warehouseId, String locationCodePrefix, Long productId,
            UnitType unitType, StorageCondition storageCondition, String viewType,
            Integer page, Integer size, String sort) {

        String unitTypeStr = unitType != null ? unitType.getValue() : null;
        String storageConditionStr = storageCondition != null ? storageCondition.getValue() : null;

        if ("PRODUCT_SUMMARY".equals(viewType)) {
            return handleProductSummary(warehouseId, productId, storageConditionStr, page, size, sort);
        }

        return handleLocationView(warehouseId, locationCodePrefix, productId, unitTypeStr,
                storageConditionStr, page, size, sort);
    }

    private ResponseEntity<ListInventory200Response> handleLocationView(
            Long warehouseId, String locationCodePrefix, Long productId,
            String unitType, String storageCondition,
            Integer page, Integer size, String sort) {

        Sort sortObj = parseSort(sort, "locationCode", LOCATION_SORT_PROPERTIES);
        Page<Inventory> resultPage = inventoryQueryService.searchByLocation(
                warehouseId, locationCodePrefix, productId, unitType, storageCondition,
                PageRequest.of(page, size, sortObj));

        Set<Long> locationIds = resultPage.getContent().stream()
                .map(Inventory::getLocationId).collect(Collectors.toSet());
        Set<Long> productIds = resultPage.getContent().stream()
                .map(Inventory::getProductId).collect(Collectors.toSet());

        Map<Long, String> locationCodeMap = inventoryQueryService.getLocationCodeMap(locationIds);
        Map<Long, Product> productMap = inventoryQueryService.getProductMap(productIds);

        List<InventoryLocationItem> items = resultPage.getContent().stream()
                .map(inv -> toLocationItem(inv, locationCodeMap, productMap))
                .toList();

        InventoryLocationPageResponse response = new InventoryLocationPageResponse()
                .content(items)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<ListInventory200Response> handleProductSummary(
            Long warehouseId, Long productId, String storageCondition,
            Integer page, Integer size, String sort) {

        // PRODUCT_SUMMARY は Object[] プロジェクションのため Sort 適用不可。ページングのみ。
        Page<Object[]> resultPage = inventoryQueryService.searchProductSummary(
                warehouseId, productId, storageCondition,
                PageRequest.of(page, size));

        Set<Long> pIds = resultPage.getContent().stream()
                .map(row -> (Long) row[0]).collect(Collectors.toSet());
        Map<Long, Product> productMap = inventoryQueryService.getProductMap(pIds);

        List<InventoryProductSummaryItem> items = resultPage.getContent().stream()
                .map(row -> toProductSummaryItem(row, productMap))
                .toList();

        InventoryProductSummaryPageResponse response = new InventoryProductSummaryPageResponse()
                .content(items)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    private InventoryLocationItem toLocationItem(Inventory inv,
                                                   Map<Long, String> locationCodeMap,
                                                   Map<Long, Product> productMap) {
        Product product = productMap.get(inv.getProductId());
        int availableQty = inv.getQuantity() - inv.getAllocatedQty();

        return new InventoryLocationItem()
                .id(inv.getId())
                .locationId(inv.getLocationId())
                .locationCode(locationCodeMap.getOrDefault(inv.getLocationId(), ""))
                .productId(inv.getProductId())
                .productCode(product != null ? product.getProductCode() : "")
                .productName(product != null ? product.getProductName() : "")
                .unitType(UnitType.fromValue(inv.getUnitType()))
                .lotNumber(inv.getLotNumber())
                .expiryDate(inv.getExpiryDate())
                .quantity(inv.getQuantity())
                .allocatedQty(inv.getAllocatedQty())
                .availableQty(availableQty)
                .updatedAt(inv.getUpdatedAt());
    }

    private InventoryProductSummaryItem toProductSummaryItem(Object[] row,
                                                               Map<Long, Product> productMap) {
        Long pId = (Long) row[0];
        int caseQty = ((Number) row[1]).intValue();
        int ballQty = ((Number) row[2]).intValue();
        int pieceQty = ((Number) row[3]).intValue();
        int totalAllocated = ((Number) row[4]).intValue();
        int totalAvailable = ((Number) row[5]).intValue();

        Product product = productMap.get(pId);
        String productCode = product != null ? product.getProductCode() : "";
        String productName = product != null ? product.getProductName() : "";
        StorageCondition sc = null;
        try {
            if (product != null && product.getStorageCondition() != null) {
                sc = StorageCondition.fromValue(product.getStorageCondition());
            }
        } catch (IllegalArgumentException ignored) { }
        int casePieces = product != null ? product.getCaseQuantity() : 1;
        int ballPieces = product != null ? product.getBallQuantity() : 1;

        // case_quantity = ボール/ケース, ball_quantity = バラ/ボール
        // 総バラ換算 = ケース数 × ボール/ケース × バラ/ボール + ボール数 × バラ/ボール + バラ数
        long totalPieceEquivalent = (long) caseQty * casePieces * ballPieces + (long) ballQty * ballPieces + pieceQty;

        return new InventoryProductSummaryItem()
                .productId(pId)
                .productCode(productCode)
                .productName(productName)
                .storageCondition(sc)
                .caseQuantity(caseQty)
                .ballQuantity(ballQty)
                .pieceQuantity(pieceQty)
                .totalAllocatedQty(totalAllocated)
                .totalAvailableQty(totalAvailable)
                .totalPieceEquivalent(totalPieceEquivalent);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<MoveInventoryResponse> moveInventory(MoveInventoryRequest request) {
        InventoryMoveService.MoveResult result = inventoryMoveService.moveInventory(
                request.getFromLocationId(), request.getProductId(),
                request.getUnitType().getValue(), request.getLotNumber(),
                request.getExpiryDate(), request.getToLocationId(), request.getMoveQty());
        MoveInventoryResponse response = new MoveInventoryResponse()
                .fromInventoryId(result.fromInventoryId()).toInventoryId(result.toInventoryId())
                .fromLocationCode(result.fromLocationCode()).toLocationCode(result.toLocationCode())
                .productCode(result.productCode()).productName(result.productName())
                .unitType(UnitType.fromValue(result.unitType())).movedQty(result.movedQty())
                .fromQuantityAfter(result.fromQuantityAfter()).toQuantityAfter(result.toQuantityAfter());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Override
    public ResponseEntity<LocationCapacityResponse> getLocationCapacity(String unitType) {
        int maxQty = inventoryMoveService.getLocationCapacity(unitType);
        return ResponseEntity.ok(new LocationCapacityResponse()
                .unitType(UnitType.fromValue(unitType)).maxQuantity(maxQty));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<BreakdownInventoryResponse> breakdownInventory(BreakdownInventoryRequest request) {
        InventoryBreakdownService.BreakdownResult result = inventoryBreakdownService.breakdown(
                request.getFromLocationId(), request.getProductId(),
                request.getFromUnitType().getValue(), request.getBreakdownQty(),
                request.getToUnitType().getValue(), request.getToLocationId());
        BreakdownInventoryResponse response = new BreakdownInventoryResponse()
                .fromInventoryId(result.fromInventoryId()).toInventoryId(result.toInventoryId())
                .productCode(result.productCode()).productName(result.productName())
                .fromUnitType(result.fromUnitType()).toUnitType(result.toUnitType())
                .breakdownQty(result.breakdownQty()).convertedQty(result.convertedQty())
                .fromQuantityAfter(result.fromQuantityAfter()).toQuantityAfter(result.toQuantityAfter());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<List<CorrectionHistoryItem>> getCorrectionHistory(
            Long warehouseId, Long locationId, Long productId, UnitType unitType) {
        return ResponseEntity.ok(
                inventoryCorrectionService.getCorrectionHistory(
                        warehouseId, locationId, productId, unitType.getValue())
                .stream()
                .map(r -> new CorrectionHistoryItem()
                        .correctedAt(r.correctedAt()).quantityBefore(r.quantityBefore())
                        .quantityAfter(r.quantityAfter()).reason(r.reason())
                        .executedByName(r.executedByName()))
                .toList());
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<CorrectionInventoryResponse> correctInventory(CorrectionInventoryRequest request) {
        InventoryCorrectionService.CorrectionResult result = inventoryCorrectionService.correct(
                request.getLocationId(), request.getProductId(),
                request.getUnitType().getValue(),
                request.getLotNumber(), request.getExpiryDate(),
                request.getNewQty(), request.getReason());

        CorrectionInventoryResponse response = new CorrectionInventoryResponse()
                .inventoryId(result.inventoryId())
                .locationCode(result.locationCode())
                .productCode(result.productCode())
                .productName(result.productName())
                .unitType(UnitType.fromValue(result.unitType()))
                .quantityBefore(result.quantityBefore())
                .quantityAfter(result.quantityAfter())
                .reason(result.reason());

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Override
    public ResponseEntity<StocktakeSummaryPageResponse> listStocktakes(
            Long warehouseId, StocktakeStatus status, LocalDate dateFrom, LocalDate dateTo,
            String stocktakeNumber, Long buildingId,
            Integer page, Integer size, String sort) {

        String statusStr = status != null ? status.getValue() : null;
        String trimmedNumber = stocktakeNumber != null ? stocktakeNumber.trim() : null;
        if (trimmedNumber != null && trimmedNumber.isEmpty()) {
            trimmedNumber = null;
        }

        Sort sortObj = parseSort(sort, "startedAt", Set.of("startedAt", "stocktakeNumber", "status"));
        Page<StocktakeHeader> resultPage = stocktakeQueryService.search(
                warehouseId, statusStr, dateFrom, dateTo,
                trimmedNumber, buildingId,
                PageRequest.of(page, size, sortObj));

        // ユーザー名・倉庫名のバッチ取得（N+1回避）
        Set<Long> userIds = new java.util.HashSet<>();
        Set<Long> warehouseIds = new java.util.HashSet<>();
        for (StocktakeHeader h : resultPage.getContent()) {
            userIds.add(h.getStartedBy());
            if (h.getConfirmedBy() != null) {
                userIds.add(h.getConfirmedBy());
            }
            warehouseIds.add(h.getWarehouseId());
        }
        Map<Long, String> userNameMap = userService.getUserFullNameMap(userIds);
        Map<Long, String> warehouseNameMap = new java.util.HashMap<>();
        for (Long wId : warehouseIds) {
            try {
                Warehouse wh = warehouseService.findById(wId);
                warehouseNameMap.put(wId, wh.getWarehouseName());
            } catch (Exception e) {
                log.warn("Failed to resolve warehouse name for warehouseId={}: {}", wId, e.getMessage());
            }
        }

        List<StocktakeSummary> items = resultPage.getContent().stream()
                .map(h -> toStocktakeSummary(h, userNameMap, warehouseNameMap))
                .toList();

        StocktakeSummaryPageResponse response = new StocktakeSummaryPageResponse()
                .content(items)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    private StocktakeSummary toStocktakeSummary(StocktakeHeader h,
                                                 Map<Long, String> userNameMap,
                                                 Map<Long, String> warehouseNameMap) {
        long totalLines = stocktakeQueryService.countTotalLines(h.getId());
        long countedLines = stocktakeQueryService.countCountedLines(h.getId());

        return new StocktakeSummary()
                .id(h.getId())
                .stocktakeNumber(h.getStocktakeNumber())
                .warehouseId(h.getWarehouseId())
                .warehouseName(warehouseNameMap.getOrDefault(h.getWarehouseId(), ""))
                .targetDescription(h.getTargetDescription())
                .status(StocktakeStatus.fromValue(h.getStatus()))
                .totalLines((int) totalLines)
                .countedLines((int) countedLines)
                .startedAt(h.getStartedAt())
                .startedByName(userNameMap.getOrDefault(h.getStartedBy(), ""))
                .confirmedAt(h.getConfirmedAt())
                .confirmedByName(h.getConfirmedBy() != null ? userNameMap.getOrDefault(h.getConfirmedBy(), "") : null);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Override
    public ResponseEntity<StocktakeDetail> getStocktake(Long id, Boolean isCounted, String locationCodePrefix, Integer page, Integer size) {
        // ヘッダのみ取得（明細はsearchLinesでページング取得するため）
        com.wms.inventory.entity.StocktakeHeader header = stocktakeQueryService.findById(id);

        // 明細ページング
        Page<com.wms.inventory.entity.StocktakeLine> linesPage =
                stocktakeQueryService.searchLines(id, isCounted, locationCodePrefix, PageRequest.of(page, size));

        // ユーザー名解決
        Set<Long> uIds = new java.util.HashSet<>();
        uIds.add(header.getStartedBy());
        if (header.getConfirmedBy() != null) {
            uIds.add(header.getConfirmedBy());
        }
        linesPage.getContent().stream()
                .filter(l -> l.getCountedBy() != null)
                .forEach(l -> uIds.add(l.getCountedBy()));
        Map<Long, String> uNameMap = userService.getUserFullNameMap(uIds);

        // 倉庫名
        String whName = "";
        try {
            whName = warehouseService.findById(header.getWarehouseId()).getWarehouseName();
        } catch (Exception e) {
            log.warn("Failed to resolve warehouse name for id={}", header.getWarehouseId(), e);
        }

        long totalLines = stocktakeQueryService.countTotalLines(id);
        long countedLines = stocktakeQueryService.countCountedLines(id);

        List<StocktakeLineItem> lineItems = linesPage.getContent().stream()
                .<StocktakeLineItem>map(l -> new StocktakeLineItem()
                        .lineId(l.getId())
                        .locationId(l.getLocationId())
                        .locationCode(l.getLocationCode())
                        .productId(l.getProductId())
                        .productCode(l.getProductCode())
                        .productName(l.getProductName())
                        .unitType(UnitType.fromValue(l.getUnitType()))
                        .lotNumber(l.getLotNumber())
                        .expiryDate(l.getExpiryDate())
                        .quantityBefore(l.getQuantityBefore())
                        .quantityCounted(l.getQuantityCounted())
                        .quantityDiff("CONFIRMED".equals(header.getStatus()) ? l.getQuantityDiff() : null)
                        .isCounted(l.isCounted())
                        .countedAt(l.getCountedAt())
                        .countedByName(l.getCountedBy() != null ? uNameMap.getOrDefault(l.getCountedBy(), "") : null))
                .toList();

        StocktakeDetailLines detailLines = new StocktakeDetailLines()
                .content(lineItems)
                .page(linesPage.getNumber())
                .size(linesPage.getSize())
                .totalElements(linesPage.getTotalElements())
                .totalPages(linesPage.getTotalPages());

        StocktakeDetail detail = new StocktakeDetail()
                .id(header.getId())
                .stocktakeNumber(header.getStocktakeNumber())
                .warehouseId(header.getWarehouseId())
                .warehouseName(whName)
                .targetDescription(header.getTargetDescription())
                .status(StocktakeStatus.fromValue(header.getStatus()))
                .totalLines((int) totalLines)
                .countedLines((int) countedLines)
                .startedAt(header.getStartedAt())
                .startedByName(uNameMap.getOrDefault(header.getStartedBy(), ""))
                .confirmedAt(header.getConfirmedAt())
                .confirmedByName(header.getConfirmedBy() != null ? uNameMap.getOrDefault(header.getConfirmedBy(), "") : null)
                .lines(detailLines);

        return ResponseEntity.ok(detail);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<StartStocktakeResponse> startStocktake(StartStocktakeRequest request) {
        StocktakeService.StartResult result = stocktakeService.startStocktake(
                request.getWarehouseId(), request.getBuildingId(), request.getAreaId(),
                request.getStocktakeDate(), request.getNote());

        StartStocktakeResponse response = new StartStocktakeResponse()
                .id(result.id())
                .stocktakeNumber(result.stocktakeNumber())
                .targetDescription(result.targetDescription())
                .status(StocktakeStatus.fromValue(result.status()))
                .totalLines(result.totalLines())
                .startedAt(result.startedAt());

        return ResponseEntity.status(201).body(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<SaveStocktakeLinesResponse> saveStocktakeLines(Long id, SaveStocktakeLinesRequest request) {
        List<StocktakeService.LineInput> inputs = request.getLines().stream()
                .map(l -> new StocktakeService.LineInput(l.getLineId(), l.getActualQty()))
                .toList();

        StocktakeService.InputResult result = stocktakeService.saveStocktakeLines(id, inputs);

        SaveStocktakeLinesResponse response = new SaveStocktakeLinesResponse()
                .updatedCount(result.updatedCount())
                .totalLines((int) result.totalLines())
                .countedLines((int) result.countedLines());

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<ConfirmStocktakeResponse> confirmStocktake(Long id) {
        StocktakeService.ConfirmResult result = stocktakeService.confirmStocktake(id);

        ConfirmStocktakeResponse response = new ConfirmStocktakeResponse()
                .id(result.id())
                .stocktakeNumber(result.stocktakeNumber())
                .status(StocktakeStatus.fromValue(result.status()))
                .totalLines(result.totalLines())
                .adjustedLines(result.adjustedLines())
                .confirmedAt(result.confirmedAt());

        return ResponseEntity.ok(response);
    }

    private Sort parseSort(String sort, String defaultProperty, Set<String> allowedProperties) {
        String[] parts = sort.split(",");
        String property = allowedProperties.contains(parts[0]) ? parts[0] : defaultProperty;
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
