package com.wms.master.controller;

import com.wms.generated.model.BuildingDetail;
import com.wms.generated.model.BuildingListItem;
import com.wms.generated.model.BuildingPageResponse;
import com.wms.generated.model.BuildingToggleResponse;
import com.wms.generated.model.CreateBuildingRequest;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateBuildingRequest;
import com.wms.master.entity.Building;
import com.wms.master.entity.Warehouse;
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
 * 棟マスタ CRUD コントローラー。
 * OpenAPI生成の MasterFacilityApi は全施設を含むため個別コントローラーとして定義する。
 * 全施設の実装完了後に MasterFacilityApi implements へのリファクタリングを検討する。
 */
@RestController
@RequestMapping("/api/v1/master/buildings")
@RequiredArgsConstructor
@Validated
@Slf4j
public class BuildingController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "buildingCode", "buildingName", "createdAt", "updatedAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final BuildingService buildingService;
    private final WarehouseService warehouseService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<BuildingPageResponse> listBuildings(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String buildingCode,
            @RequestParam(required = false) String buildingName,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            @RequestParam(defaultValue = "buildingCode,asc") String sort) {

        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Sort sortObj = parseSort(sort);
        Page<Building> resultPage = buildingService.search(
                warehouseId, buildingCode, buildingName, isActive,
                PageRequest.of(page, cappedSize, sortObj));

        // バッチフェッチで N+1 を回避
        Set<Long> warehouseIds = resultPage.getContent().stream()
                .map(Building::getWarehouseId).collect(Collectors.toSet());
        Map<Long, Warehouse> warehouseMap = warehouseService.findByIds(warehouseIds);

        List<BuildingListItem> items = resultPage.getContent().stream()
                .map(b -> toListItem(b, warehouseMap.get(b.getWarehouseId())))
                .toList();
        BuildingPageResponse response = new BuildingPageResponse()
                .content(items)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PostMapping
    public ResponseEntity<BuildingDetail> createBuilding(
            @Valid @RequestBody CreateBuildingRequest request) {
        // 倉庫の存在確認
        Warehouse warehouse = warehouseService.findById(request.getWarehouseId());
        Building building = new Building();
        building.setWarehouseId(request.getWarehouseId());
        building.setBuildingCode(request.getBuildingCode());
        building.setBuildingName(request.getBuildingName());

        Building created = buildingService.create(building);
        log.info("Building created via API: id={}", created.getId());
        URI location = URI.create("/api/v1/master/buildings/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created, warehouse));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<BuildingDetail> getBuilding(@PathVariable Long id) {
        Building building = buildingService.findById(id);
        Warehouse warehouse = warehouseService.findById(building.getWarehouseId());
        return ResponseEntity.ok(toDetail(building, warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<BuildingDetail> updateBuilding(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBuildingRequest request) {
        Building updated = buildingService.update(id, request.getBuildingName(), request.getVersion());
        Warehouse warehouse = warehouseService.findById(updated.getWarehouseId());
        log.info("Building updated via API: id={}", id);
        return ResponseEntity.ok(toDetail(updated, warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<BuildingToggleResponse> toggleBuildingActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request) {
        Building updated = buildingService.toggleActive(id, request.getIsActive(), request.getVersion());
        log.info("Building toggled via API: id={}, isActive={}", id, request.getIsActive());
        return ResponseEntity.ok(toToggleResponse(updated));
    }

    // --- Converters ---

    private BuildingDetail toDetail(Building b, Warehouse w) {
        return new BuildingDetail()
                .id(b.getId())
                .buildingCode(b.getBuildingCode())
                .buildingName(b.getBuildingName())
                .warehouseId(b.getWarehouseId())
                .warehouseCode(w.getWarehouseCode())
                .warehouseName(w.getWarehouseName())
                .isActive(b.getIsActive())
                .version(b.getVersion())
                .createdAt(toLocalDateTime(b.getCreatedAt()))
                .updatedAt(toLocalDateTime(b.getUpdatedAt()));
    }

    private BuildingListItem toListItem(Building b, Warehouse w) {
        return new BuildingListItem()
                .id(b.getId())
                .buildingCode(b.getBuildingCode())
                .buildingName(b.getBuildingName())
                .warehouseId(b.getWarehouseId())
                .warehouseCode(w != null ? w.getWarehouseCode() : null)
                .isActive(b.getIsActive())
                .createdAt(toLocalDateTime(b.getCreatedAt()))
                .updatedAt(toLocalDateTime(b.getUpdatedAt()));
    }

    private BuildingToggleResponse toToggleResponse(Building b) {
        return new BuildingToggleResponse()
                .id(b.getId())
                .buildingCode(b.getBuildingCode())
                .buildingName(b.getBuildingName())
                .isActive(b.getIsActive())
                .version(b.getVersion())
                .updatedAt(toLocalDateTime(b.getUpdatedAt()));
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "buildingCode";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
