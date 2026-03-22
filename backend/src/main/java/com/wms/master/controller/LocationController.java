package com.wms.master.controller;

import com.wms.generated.api.MasterLocationApi;
import com.wms.generated.model.AreaType;
import com.wms.generated.model.CountResponse;
import com.wms.generated.model.CreateLocationRequest;
import com.wms.generated.model.LocationDetail;
import com.wms.generated.model.LocationFullDetail;
import com.wms.generated.model.LocationListItem;
import com.wms.generated.model.LocationPageResponse;
import com.wms.generated.model.LocationToggleResponse;
import com.wms.generated.model.LocationUpdateResponse;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateLocationRequest;
import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.entity.Location;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
import com.wms.master.service.BuildingService;
import com.wms.master.service.LocationService;
import com.wms.master.service.WarehouseService;
import lombok.RequiredArgsConstructor;
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
 * ロケーションマスタ CRUD コントローラー。
 * OpenAPI生成の MasterLocationApi を実装する。
 */
@RestController
@RequiredArgsConstructor
public class LocationController implements MasterLocationApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "locationCode", "locationName", "isActive", "createdAt", "updatedAt");
    private final LocationService locationService;
    private final AreaService areaService;
    private final BuildingService buildingService;
    private final WarehouseService warehouseService;

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<LocationPageResponse> listLocations(
            Long warehouseId,
            String codePrefix,
            Long areaId,
            Boolean isActive,
            Integer page,
            Integer size,
            String sort) {

        Sort sortObj = parseSort(sort);
        Page<Location> resultPage = locationService.search(
                warehouseId, areaId, codePrefix, isActive,
                PageRequest.of(page, size, sortObj));

        // バッチフェッチで N+1 を回避
        Set<Long> areaIds = resultPage.getContent().stream()
                .map(Location::getAreaId).collect(Collectors.toSet());
        Set<Long> warehouseIds = resultPage.getContent().stream()
                .map(Location::getWarehouseId).collect(Collectors.toSet());
        Map<Long, Area> areaMap = areaService.findByIds(areaIds);
        Map<Long, Warehouse> warehouseMap = warehouseService.findByIds(warehouseIds);

        List<LocationListItem> items = resultPage.getContent().stream()
                .map(l -> toListItem(l, areaMap.get(l.getAreaId()), warehouseMap.get(l.getWarehouseId())))
                .toList();
        LocationPageResponse response = new LocationPageResponse()
                .content(items)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<CountResponse> countLocations(
            Long warehouseId,
            Long buildingId,
            Long areaId,
            Boolean isActive) {
        Boolean effectiveIsActive = isActive != null ? isActive : Boolean.TRUE;
        long total = locationService.count(warehouseId, buildingId, areaId, effectiveIsActive);
        return ResponseEntity.ok(new CountResponse().count((int) total));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<LocationDetail> createLocation(
            CreateLocationRequest createLocationRequest) {
        Location location = new Location();
        location.setAreaId(createLocationRequest.getAreaId());
        location.setLocationCode(createLocationRequest.getLocationCode());
        location.setLocationName(createLocationRequest.getLocationName());

        Location created = locationService.create(location);

        Area area = areaService.findById(created.getAreaId());
        Warehouse warehouse = warehouseService.findById(created.getWarehouseId());
        URI locationUri = URI.create("/api/v1/master/locations/" + created.getId());
        return ResponseEntity.created(locationUri).body(toDetail(created, area, warehouse));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<LocationFullDetail> getLocation(Long id) {
        Location location = locationService.findById(id);
        Area area = areaService.findById(location.getAreaId());
        Building building = buildingService.findById(area.getBuildingId());
        Warehouse warehouse = warehouseService.findById(location.getWarehouseId());
        return ResponseEntity.ok(toFullDetail(location, area, building, warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<LocationUpdateResponse> updateLocation(
            Long id,
            UpdateLocationRequest updateLocationRequest) {
        Location updated = locationService.update(id, updateLocationRequest.getLocationName(), updateLocationRequest.getVersion());
        return ResponseEntity.ok(toUpdateResponse(updated));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<LocationToggleResponse> toggleLocationActive(
            Long id,
            ToggleActiveRequest toggleActiveRequest) {
        Location updated = locationService.toggleActive(id, toggleActiveRequest.getIsActive(), toggleActiveRequest.getVersion());
        return ResponseEntity.ok(toToggleResponse(updated));
    }

    // --- Converters ---

    private LocationDetail toDetail(Location l, Area a, Warehouse w) {
        return new LocationDetail()
                .id(l.getId())
                .locationCode(l.getLocationCode())
                .locationName(l.getLocationName())
                .warehouseId(l.getWarehouseId())
                .warehouseCode(w.getWarehouseCode())
                .areaId(l.getAreaId())
                .areaCode(a.getAreaCode())
                .areaType(AreaType.fromValue(a.getAreaType()))
                .isActive(l.getIsActive())
                .version(l.getVersion())
                .createdAt(toLocalDateTime(l.getCreatedAt()))
                .updatedAt(toLocalDateTime(l.getUpdatedAt()));
    }

    private LocationFullDetail toFullDetail(Location l, Area a, Building b, Warehouse w) {
        return new LocationFullDetail()
                .id(l.getId())
                .locationCode(l.getLocationCode())
                .locationName(l.getLocationName())
                .warehouseId(l.getWarehouseId())
                .warehouseCode(w.getWarehouseCode())
                .warehouseName(w.getWarehouseName())
                .areaId(l.getAreaId())
                .areaCode(a.getAreaCode())
                .areaName(a.getAreaName())
                .areaType(AreaType.fromValue(a.getAreaType()))
                .storageCondition(StorageCondition.fromValue(a.getStorageCondition()))
                .buildingId(b.getId())
                .buildingCode(b.getBuildingCode())
                .buildingName(b.getBuildingName())
                .isActive(l.getIsActive())
                .version(l.getVersion())
                .createdAt(toLocalDateTime(l.getCreatedAt()))
                .updatedAt(toLocalDateTime(l.getUpdatedAt()));
    }

    private LocationListItem toListItem(Location l, Area a, Warehouse w) {
        return new LocationListItem()
                .id(l.getId())
                .locationCode(l.getLocationCode())
                .locationName(l.getLocationName())
                .warehouseId(l.getWarehouseId())
                .warehouseCode(w != null ? w.getWarehouseCode() : null)
                .areaId(l.getAreaId())
                .areaCode(a != null ? a.getAreaCode() : null)
                .areaType(a != null ? AreaType.fromValue(a.getAreaType()) : null)
                .isActive(l.getIsActive())
                .createdAt(toLocalDateTime(l.getCreatedAt()))
                .updatedAt(toLocalDateTime(l.getUpdatedAt()));
    }

    private LocationUpdateResponse toUpdateResponse(Location l) {
        return new LocationUpdateResponse()
                .id(l.getId())
                .locationCode(l.getLocationCode())
                .locationName(l.getLocationName())
                .isActive(l.getIsActive())
                .version(l.getVersion())
                .updatedAt(toLocalDateTime(l.getUpdatedAt()));
    }

    private LocationToggleResponse toToggleResponse(Location l) {
        return new LocationToggleResponse()
                .id(l.getId())
                .locationCode(l.getLocationCode())
                .locationName(l.getLocationName())
                .isActive(l.getIsActive())
                .version(l.getVersion())
                .updatedAt(toLocalDateTime(l.getUpdatedAt()));
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "locationCode";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
