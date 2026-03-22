package com.wms.master.controller;

import com.wms.generated.api.MasterBuildingApi;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * OpenAPI生成の MasterBuildingApi を実装する。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class BuildingController implements MasterBuildingApi {

    private final BuildingService buildingService;
    private final WarehouseService warehouseService;

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<BuildingPageResponse> listBuildings(
            Long warehouseId,
            String buildingCode,
            Boolean isActive,
            Integer page,
            Integer size) {

        Sort sortObj = Sort.by(Sort.Direction.ASC, "buildingCode");
        Page<Building> resultPage = buildingService.search(
                warehouseId, buildingCode, null, isActive,
                PageRequest.of(page, size, sortObj));

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
    @Override
    public ResponseEntity<BuildingDetail> createBuilding(
            CreateBuildingRequest createBuildingRequest) {
        // 倉庫の存在確認
        Warehouse warehouse = warehouseService.findById(createBuildingRequest.getWarehouseId());
        Building building = new Building();
        building.setWarehouseId(createBuildingRequest.getWarehouseId());
        building.setBuildingCode(createBuildingRequest.getBuildingCode());
        building.setBuildingName(createBuildingRequest.getBuildingName());

        Building created = buildingService.create(building);
        log.info("Building created via API: id={}", created.getId());
        URI location = URI.create("/api/v1/master/buildings/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created, warehouse));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<BuildingDetail> getBuilding(Long id) {
        Building building = buildingService.findById(id);
        Warehouse warehouse = warehouseService.findById(building.getWarehouseId());
        return ResponseEntity.ok(toDetail(building, warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<BuildingDetail> updateBuilding(
            Long id,
            UpdateBuildingRequest updateBuildingRequest) {
        Building updated = buildingService.update(id, updateBuildingRequest.getBuildingName(), updateBuildingRequest.getVersion());
        Warehouse warehouse = warehouseService.findById(updated.getWarehouseId());
        log.info("Building updated via API: id={}", id);
        return ResponseEntity.ok(toDetail(updated, warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<BuildingToggleResponse> toggleBuildingActive(
            Long id,
            ToggleActiveRequest toggleActiveRequest) {
        Building updated = buildingService.toggleActive(id, toggleActiveRequest.getIsActive(), toggleActiveRequest.getVersion());
        log.info("Building toggled via API: id={}, isActive={}", id, toggleActiveRequest.getIsActive());
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
}
