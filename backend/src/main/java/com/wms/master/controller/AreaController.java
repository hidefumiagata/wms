package com.wms.master.controller;

import com.wms.generated.model.AreaDetail;
import com.wms.generated.model.AreaListItem;
import com.wms.generated.model.AreaPageResponse;
import com.wms.generated.model.AreaToggleResponse;
import com.wms.generated.model.AreaType;
import com.wms.generated.model.AreaUpdateResponse;
import com.wms.generated.model.CreateAreaRequest;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateAreaRequest;
import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
import com.wms.master.service.BuildingService;
import com.wms.master.service.WarehouseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * エリアマスタ CRUD コントローラー。
 * OpenAPI生成の MasterFacilityApi は全施設を含むため個別コントローラーとして定義する。
 * 全施設の実装完了後に MasterFacilityApi implements へのリファクタリングを検討する。
 */
@RestController
@RequestMapping("/api/v1/master/areas")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AreaController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "areaCode", "areaName", "areaType", "storageCondition", "createdAt", "updatedAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final AreaService areaService;
    private final BuildingService buildingService;
    private final WarehouseService warehouseService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<AreaPageResponse> listAreas(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) AreaType areaType,
            @RequestParam(required = false) StorageCondition storageCondition,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            @RequestParam(defaultValue = "areaCode,asc") String sort) {

        String areaTypeValue = areaType != null ? areaType.getValue() : null;
        String storageConditionValue = storageCondition != null ? storageCondition.getValue() : null;

        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Sort sortObj = parseSort(sort);
        Page<Area> resultPage = areaService.search(
                warehouseId, buildingId, areaTypeValue, storageConditionValue, isActive,
                PageRequest.of(page, cappedSize, sortObj));

        // バッチフェッチで N+1 を回避
        Set<Long> buildingIds = resultPage.getContent().stream()
                .map(Area::getBuildingId).collect(Collectors.toSet());
        Set<Long> warehouseIds = resultPage.getContent().stream()
                .map(Area::getWarehouseId).collect(Collectors.toSet());
        Map<Long, Building> buildingMap = buildingService.findByIds(buildingIds);
        Map<Long, Warehouse> warehouseMap = warehouseService.findByIds(warehouseIds);

        List<AreaListItem> items = resultPage.getContent().stream()
                .map(a -> toListItem(a, buildingMap.get(a.getBuildingId()), warehouseMap.get(a.getWarehouseId())))
                .toList();
        AreaPageResponse response = new AreaPageResponse()
                .content(items)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PostMapping
    public ResponseEntity<AreaDetail> createArea(
            @Valid @RequestBody CreateAreaRequest request) {
        Area area = new Area();
        area.setBuildingId(request.getBuildingId());
        area.setAreaCode(request.getAreaCode());
        area.setAreaName(request.getAreaName());
        area.setStorageCondition(request.getStorageCondition().getValue());
        area.setAreaType(request.getAreaType().getValue());

        Area created = areaService.create(area);
        log.info("Area created via API: id={}", created.getId());

        Building building = buildingService.findById(created.getBuildingId());
        Warehouse warehouse = warehouseService.findById(created.getWarehouseId());
        URI location = URI.create("/api/v1/master/areas/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created, building, warehouse));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<AreaDetail> getArea(@PathVariable Long id) {
        Area area = areaService.findById(id);
        Building building = buildingService.findById(area.getBuildingId());
        Warehouse warehouse = warehouseService.findById(area.getWarehouseId());
        return ResponseEntity.ok(toDetail(area, building, warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<AreaUpdateResponse> updateArea(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAreaRequest request) {
        Area updated = areaService.update(
                id, request.getAreaName(), request.getStorageCondition().getValue(), request.getVersion());
        log.info("Area updated via API: id={}", id);
        return ResponseEntity.ok(toUpdateResponse(updated));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<AreaToggleResponse> toggleAreaActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request) {
        Area updated = areaService.toggleActive(id, request.getIsActive(), request.getVersion());
        log.info("Area toggled via API: id={}, isActive={}", id, request.getIsActive());
        return ResponseEntity.ok(toToggleResponse(updated));
    }

    // --- Converters ---

    private AreaDetail toDetail(Area a, Building b, Warehouse w) {
        return new AreaDetail()
                .id(a.getId())
                .areaCode(a.getAreaCode())
                .areaName(a.getAreaName())
                .warehouseId(a.getWarehouseId())
                .warehouseCode(w.getWarehouseCode())
                .warehouseName(w.getWarehouseName())
                .buildingId(a.getBuildingId())
                .buildingCode(b.getBuildingCode())
                .buildingName(b.getBuildingName())
                .storageCondition(StorageCondition.fromValue(a.getStorageCondition()))
                .areaType(AreaType.fromValue(a.getAreaType()))
                .isActive(a.getIsActive())
                .version(a.getVersion())
                .createdAt(toLocalDateTime(a.getCreatedAt()))
                .updatedAt(toLocalDateTime(a.getUpdatedAt()));
    }

    private AreaListItem toListItem(Area a, Building b, Warehouse w) {
        return new AreaListItem()
                .id(a.getId())
                .areaCode(a.getAreaCode())
                .areaName(a.getAreaName())
                .warehouseId(a.getWarehouseId())
                .warehouseCode(w != null ? w.getWarehouseCode() : null)
                .buildingId(a.getBuildingId())
                .buildingCode(b != null ? b.getBuildingCode() : null)
                .storageCondition(StorageCondition.fromValue(a.getStorageCondition()))
                .areaType(AreaType.fromValue(a.getAreaType()))
                .isActive(a.getIsActive())
                .createdAt(toLocalDateTime(a.getCreatedAt()))
                .updatedAt(toLocalDateTime(a.getUpdatedAt()));
    }

    private AreaUpdateResponse toUpdateResponse(Area a) {
        return new AreaUpdateResponse()
                .id(a.getId())
                .areaCode(a.getAreaCode())
                .areaName(a.getAreaName())
                .storageCondition(StorageCondition.fromValue(a.getStorageCondition()))
                .areaType(AreaType.fromValue(a.getAreaType()))
                .isActive(a.getIsActive())
                .version(a.getVersion())
                .updatedAt(toLocalDateTime(a.getUpdatedAt()));
    }

    private AreaToggleResponse toToggleResponse(Area a) {
        return new AreaToggleResponse()
                .id(a.getId())
                .areaCode(a.getAreaCode())
                .areaName(a.getAreaName())
                .isActive(a.getIsActive())
                .version(a.getVersion())
                .updatedAt(toLocalDateTime(a.getUpdatedAt()));
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "areaCode";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
