package com.wms.inventory.controller;

import com.wms.generated.api.InventoryApi;
import com.wms.generated.model.*;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.service.InventoryBreakdownService;
import com.wms.inventory.service.InventoryMoveService;
import com.wms.inventory.service.InventoryQueryService;
import com.wms.master.entity.Product;
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

    // --- Stub implementations for other inventory APIs ---

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<MoveInventoryResponse> moveInventory(MoveInventoryRequest request) {
        InventoryMoveService.MoveResult result = inventoryMoveService.moveInventory(
                request.getFromLocationId(),
                request.getProductId(),
                request.getUnitType().getValue(),
                request.getLotNumber(),
                request.getExpiryDate(),
                request.getToLocationId(),
                request.getMoveQty());

        MoveInventoryResponse response = new MoveInventoryResponse()
                .fromInventoryId(result.fromInventoryId())
                .toInventoryId(result.toInventoryId())
                .fromLocationCode(result.fromLocationCode())
                .toLocationCode(result.toLocationCode())
                .productCode(result.productCode())
                .productName(result.productName())
                .unitType(UnitType.fromValue(result.unitType()))
                .movedQty(result.movedQty())
                .fromQuantityAfter(result.fromQuantityAfter())
                .toQuantityAfter(result.toQuantityAfter());

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Override
    public ResponseEntity<BreakdownInventoryResponse> breakdownInventory(BreakdownInventoryRequest request) {
        InventoryBreakdownService.BreakdownResult result = inventoryBreakdownService.breakdown(
                request.getFromLocationId(), request.getProductId(),
                request.getFromUnitType().getValue(), request.getBreakdownQty(),
                request.getToUnitType().getValue(), request.getToLocationId());

        BreakdownInventoryResponse response = new BreakdownInventoryResponse()
                .fromInventoryId(result.fromInventoryId())
                .toInventoryId(result.toInventoryId())
                .productCode(result.productCode())
                .productName(result.productName())
                .fromUnitType(result.fromUnitType())
                .toUnitType(result.toUnitType())
                .breakdownQty(result.breakdownQty())
                .convertedQty(result.convertedQty())
                .fromQuantityAfter(result.fromQuantityAfter())
                .toQuantityAfter(result.toQuantityAfter());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CorrectionInventoryResponse> correctInventory(CorrectionInventoryRequest request) {
        throw new UnsupportedOperationException("在庫訂正は後続Issueで実装予定");
    }

    @Override
    public ResponseEntity<StocktakeSummaryPageResponse> listStocktakes(
            Long warehouseId, StocktakeStatus status, LocalDate dateFrom, LocalDate dateTo,
            Integer page, Integer size, String sort) {
        throw new UnsupportedOperationException("棚卸一覧は後続Issueで実装予定");
    }

    @Override
    public ResponseEntity<StocktakeDetail> getStocktake(Long id, Boolean isCounted, String locationCodePrefix, Integer page, Integer size) {
        throw new UnsupportedOperationException("棚卸詳細は後続Issueで実装予定");
    }

    @Override
    public ResponseEntity<StartStocktakeResponse> startStocktake(StartStocktakeRequest request) {
        throw new UnsupportedOperationException("棚卸開始は後続Issueで実装予定");
    }

    @Override
    public ResponseEntity<SaveStocktakeLinesResponse> saveStocktakeLines(Long id, SaveStocktakeLinesRequest request) {
        throw new UnsupportedOperationException("棚卸実数入力は後続Issueで実装予定");
    }

    @Override
    public ResponseEntity<ConfirmStocktakeResponse> confirmStocktake(Long id) {
        throw new UnsupportedOperationException("棚卸確定は後続Issueで実装予定");
    }

    private Sort parseSort(String sort, String defaultProperty, Set<String> allowedProperties) {
        String[] parts = sort.split(",");
        String property = allowedProperties.contains(parts[0]) ? parts[0] : defaultProperty;
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
