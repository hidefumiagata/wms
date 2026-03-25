package com.wms.master.controller;

import com.wms.generated.api.MasterAreaApi;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * エリアマスタ CRUD コントローラー。
 * OpenAPI生成の MasterAreaApi を実装する。
 */
@RestController
@RequiredArgsConstructor
public class AreaController implements MasterAreaApi {

    private final AreaService areaService;
    private final BuildingService buildingService;
    private final WarehouseService warehouseService;

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<AreaPageResponse> listAreas(
            Long warehouseId,
            Long buildingId,
            StorageCondition storageCondition,
            AreaType areaType,
            Boolean isActive,
            Integer page,
            Integer size) {

        String areaTypeValue = areaType != null ? areaType.getValue() : null;
        String storageConditionValue = storageCondition != null ? storageCondition.getValue() : null;

        Sort sortObj = Sort.by(Sort.Direction.ASC, "areaCode");
        Page<Area> resultPage = areaService.search(
                warehouseId, buildingId, areaTypeValue, storageConditionValue, isActive,
                PageRequest.of(page, size, sortObj));

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
    @Override
    public ResponseEntity<AreaDetail> createArea(
            CreateAreaRequest createAreaRequest) {
        Area area = new Area();
        area.setBuildingId(createAreaRequest.getBuildingId());
        area.setAreaCode(createAreaRequest.getAreaCode());
        area.setAreaName(createAreaRequest.getAreaName());
        area.setStorageCondition(createAreaRequest.getStorageCondition().getValue());
        area.setAreaType(createAreaRequest.getAreaType().getValue());

        Area created = areaService.create(area);

        Building building = buildingService.findById(created.getBuildingId());
        Warehouse warehouse = warehouseService.findById(created.getWarehouseId());
        URI location = URI.create("/api/v1/master/areas/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created, building, warehouse));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<AreaDetail> getArea(Long id) {
        Area area = areaService.findById(id);
        Building building = buildingService.findById(area.getBuildingId());
        Warehouse warehouse = warehouseService.findById(area.getWarehouseId());
        return ResponseEntity.ok(toDetail(area, building, warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<AreaUpdateResponse> updateArea(
            Long id,
            UpdateAreaRequest updateAreaRequest) {
        Area updated = areaService.update(
                id, updateAreaRequest.getAreaName(), updateAreaRequest.getStorageCondition().getValue(), updateAreaRequest.getVersion());
        return ResponseEntity.ok(toUpdateResponse(updated));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<AreaToggleResponse> toggleAreaActive(
            Long id,
            ToggleActiveRequest toggleActiveRequest) {
        Area updated = areaService.toggleActive(id, toggleActiveRequest.getIsActive(), toggleActiveRequest.getVersion());
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
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt());
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
                .version(a.getVersion())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt());
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
                .updatedAt(a.getUpdatedAt());
    }

    private AreaToggleResponse toToggleResponse(Area a) {
        return new AreaToggleResponse()
                .id(a.getId())
                .areaCode(a.getAreaCode())
                .areaName(a.getAreaName())
                .isActive(a.getIsActive())
                .version(a.getVersion())
                .updatedAt(a.getUpdatedAt());
    }

}
