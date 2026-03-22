package com.wms.master.controller;

import com.wms.generated.api.MasterWarehouseApi;
import com.wms.generated.model.CreateWarehouseRequest;
import com.wms.generated.model.ExistsResponse;
import com.wms.generated.model.ListWarehouses200Response;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateWarehouseRequest;
import com.wms.generated.model.WarehouseDetail;
import com.wms.generated.model.WarehouseListItem;
import com.wms.generated.model.WarehousePageResponse;
import com.wms.generated.model.WarehouseToggleResponse;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 倉庫マスタ CRUD コントローラー。
 * OpenAPI生成の MasterWarehouseApi を実装する。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WarehouseController implements MasterWarehouseApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "warehouseCode", "warehouseName", "createdAt", "updatedAt");
    private final WarehouseService warehouseService;

    /**
     * 倉庫一覧取得。all=true の場合はプルダウン用の全件リスト（WarehousePageResponseでラップ）、
     * それ以外はページング形式で返却する。
     */
    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<ListWarehouses200Response> listWarehouses(
            String warehouseCode,
            String warehouseName,
            Boolean isActive,
            Boolean all,
            Integer page,
            Integer size,
            String sort) {

        if (Boolean.TRUE.equals(all)) {
            List<Warehouse> allWarehouses = warehouseService.findAllSimple(isActive);
            List<WarehouseListItem> items = allWarehouses.stream()
                    .map(this::toListItem)
                    .toList();
            WarehousePageResponse response = new WarehousePageResponse()
                    .content(items)
                    .page(0)
                    .size(items.size())
                    .totalElements((long) items.size())
                    .totalPages(1);
            return ResponseEntity.ok(response);
        }

        Sort sortObj = parseSort(sort);
        Page<Warehouse> resultPage = warehouseService.search(
                warehouseCode, warehouseName, isActive,
                PageRequest.of(page, size, sortObj));
        return ResponseEntity.ok(toPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<WarehouseDetail> createWarehouse(
            CreateWarehouseRequest createWarehouseRequest) {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseCode(createWarehouseRequest.getWarehouseCode());
        warehouse.setWarehouseName(createWarehouseRequest.getWarehouseName());
        warehouse.setWarehouseNameKana(createWarehouseRequest.getWarehouseNameKana());
        warehouse.setAddress(createWarehouseRequest.getAddress());

        Warehouse created = warehouseService.create(warehouse);
        log.info("Warehouse created: code={}", created.getWarehouseCode());
        URI location = URI.create("/api/v1/master/warehouses/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<WarehouseDetail> getWarehouse(Long id) {
        Warehouse warehouse = warehouseService.findById(id);
        return ResponseEntity.ok(toDetail(warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<WarehouseDetail> updateWarehouse(
            Long id,
            UpdateWarehouseRequest updateWarehouseRequest) {
        Warehouse updated = warehouseService.update(
                id,
                updateWarehouseRequest.getWarehouseName(),
                updateWarehouseRequest.getWarehouseNameKana(),
                updateWarehouseRequest.getAddress(),
                updateWarehouseRequest.getVersion());
        log.info("Warehouse updated: id={}, name={}", id, updateWarehouseRequest.getWarehouseName());
        return ResponseEntity.ok(toDetail(updated));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<WarehouseToggleResponse> toggleWarehouseActive(
            Long id,
            ToggleActiveRequest toggleActiveRequest) {
        Warehouse updated = warehouseService.toggleActive(
                id, toggleActiveRequest.getIsActive(), toggleActiveRequest.getVersion());
        log.info("Warehouse toggled: id={}, isActive={}", id, toggleActiveRequest.getIsActive());
        return ResponseEntity.ok(toToggleResponse(updated));
    }

    // TODO: #74 パターン — 列挙攻撃対策として RateLimiterService の適用を検討
    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<ExistsResponse> checkWarehouseCodeExists(
            String warehouseCode) {
        boolean exists = warehouseService.existsByCode(warehouseCode);
        return ResponseEntity.ok(new ExistsResponse().exists(exists));
    }

    // --- Converters ---

    private WarehouseDetail toDetail(Warehouse w) {
        return new WarehouseDetail()
                .id(w.getId())
                .warehouseCode(w.getWarehouseCode())
                .warehouseName(w.getWarehouseName())
                .warehouseNameKana(w.getWarehouseNameKana())
                .address(w.getAddress())
                .isActive(w.getIsActive())
                .version(w.getVersion())
                .createdAt(toLocalDateTime(w.getCreatedAt()))
                .updatedAt(toLocalDateTime(w.getUpdatedAt()));
    }

    private WarehouseToggleResponse toToggleResponse(Warehouse w) {
        return new WarehouseToggleResponse()
                .id(w.getId())
                .warehouseCode(w.getWarehouseCode())
                .warehouseName(w.getWarehouseName())
                .isActive(w.getIsActive())
                .version(w.getVersion())
                .updatedAt(toLocalDateTime(w.getUpdatedAt()));
    }

    private WarehousePageResponse toPageResponse(Page<Warehouse> page) {
        List<WarehouseListItem> items = page.getContent().stream()
                .map(this::toListItem)
                .toList();
        return new WarehousePageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private WarehouseListItem toListItem(Warehouse w) {
        return new WarehouseListItem()
                .id(w.getId())
                .warehouseCode(w.getWarehouseCode())
                .warehouseName(w.getWarehouseName())
                .warehouseNameKana(w.getWarehouseNameKana())
                .address(w.getAddress())
                .isActive(w.getIsActive())
                .createdAt(toLocalDateTime(w.getCreatedAt()))
                .updatedAt(toLocalDateTime(w.getUpdatedAt()));
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "warehouseCode";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
