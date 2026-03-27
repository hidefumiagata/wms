package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.master.entity.Location;
import com.wms.master.entity.Product;
import com.wms.master.service.LocationService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final WarehouseService warehouseService;
    private final LocationService locationService;
    private final ProductService productService;

    /**
     * LOCATIONモード: 在庫一覧をロケーション別に取得
     */
    public Page<Inventory> searchByLocation(Long warehouseId, String locationCodePrefix,
                                             Long productId, String unitType, String storageCondition,
                                             Pageable pageable) {
        warehouseService.findById(warehouseId);

        log.debug("Inventory search (LOCATION): warehouseId={}, locationCodePrefix={}, productId={}, unitType={}, storageCondition={}",
                warehouseId, locationCodePrefix, productId, unitType, storageCondition);

        return inventoryRepository.searchByLocation(
                warehouseId, locationCodePrefix, productId, unitType, storageCondition, pageable);
    }

    /**
     * ロケーションコードのマップを取得（RULE-SVC-002: LocationService経由）
     */
    public Map<Long, String> getLocationCodeMap(Set<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Location> locationMap = locationService.findByIds(locationIds);
        return locationMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getLocationCode()));
    }

    /**
     * 商品情報のマップを取得（RULE-SVC-002: ProductService経由）
     */
    public Map<Long, Product> getProductMap(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productService.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
    }

    /**
     * PRODUCT_SUMMARYモード: 商品別集計を取得
     */
    public Page<Object[]> searchProductSummary(Long warehouseId, Long productId,
                                                String storageCondition,
                                                Pageable pageable) {
        warehouseService.findById(warehouseId);

        log.debug("Inventory search (PRODUCT_SUMMARY): warehouseId={}, productId={}, storageCondition={}",
                warehouseId, productId, storageCondition);

        return inventoryRepository.searchProductSummary(
                warehouseId, productId, storageCondition, pageable);
    }
}
