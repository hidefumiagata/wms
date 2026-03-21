package com.wms.master.controller;

import com.wms.generated.model.CreateWarehouseRequest;
import com.wms.generated.model.ExistsResponse;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateWarehouseRequest;
import com.wms.generated.model.WarehouseDetail;
import com.wms.generated.model.WarehouseListItem;
import com.wms.generated.model.WarehousePageResponse;
import com.wms.generated.model.WarehouseSimple;
import com.wms.generated.model.WarehouseToggleResponse;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.WarehouseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * 倉庫マスタ CRUD コントローラー。
 * OpenAPI生成の MasterFacilityApi は全施設(倉庫・棟・エリア・ロケーション)を含むため、
 * 施設種別ごとに段階的に実装する設計とし、個別コントローラーとして定義する。
 * 全施設の実装完了後に MasterFacilityApi implements へのリファクタリングを検討する。
 */
@RestController
@RequestMapping("/api/v1/master/warehouses")
@RequiredArgsConstructor
@Validated
public class WarehouseController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "warehouseCode", "warehouseName", "createdAt", "updatedAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final WarehouseService warehouseService;

    /**
     * 倉庫一覧取得。all=true の場合はプルダウン用の全件リスト、
     * それ以外はページング形式で返却する。
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<?> listWarehouses(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean all,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            @RequestParam(defaultValue = "warehouseCode,asc") String sort) {

        if (Boolean.TRUE.equals(all)) {
            List<WarehouseSimple> simpleList = warehouseService.findAllSimple(isActive).stream()
                    .map(this::toSimple)
                    .toList();
            return ResponseEntity.ok(simpleList);
        }

        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Sort sortObj = parseSort(sort);
        Page<Warehouse> resultPage = warehouseService.search(
                warehouseCode, warehouseName, isActive,
                PageRequest.of(page, cappedSize, sortObj));
        return ResponseEntity.ok(toPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PostMapping
    public ResponseEntity<WarehouseDetail> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request) {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseCode(request.getWarehouseCode());
        warehouse.setWarehouseName(request.getWarehouseName());
        warehouse.setWarehouseNameKana(request.getWarehouseNameKana());
        warehouse.setAddress(request.getAddress());

        Warehouse created = warehouseService.create(warehouse);
        URI location = URI.create("/api/v1/master/warehouses/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<WarehouseDetail> getWarehouse(@PathVariable Long id) {
        Warehouse warehouse = warehouseService.findById(id);
        return ResponseEntity.ok(toDetail(warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<WarehouseDetail> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        Warehouse updated = warehouseService.update(
                id,
                request.getWarehouseName(),
                request.getWarehouseNameKana(),
                request.getAddress(),
                request.getVersion());
        return ResponseEntity.ok(toDetail(updated));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<WarehouseToggleResponse> toggleWarehouseActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request) {
        Warehouse updated = warehouseService.toggleActive(
                id, request.getIsActive(), request.getVersion());
        return ResponseEntity.ok(toToggleResponse(updated));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/exists")
    public ResponseEntity<ExistsResponse> checkWarehouseCodeExists(
            @RequestParam String warehouseCode) {
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

    private WarehouseSimple toSimple(Warehouse w) {
        return new WarehouseSimple()
                .id(w.getId())
                .warehouseCode(w.getWarehouseCode())
                .warehouseName(w.getWarehouseName())
                .isActive(w.getIsActive());
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
