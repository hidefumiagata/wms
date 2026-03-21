package com.wms.master.controller;

import com.wms.generated.model.AreaType;
import com.wms.generated.model.CountResponse;
import com.wms.generated.model.CreateLocationRequest;
import com.wms.generated.model.LocationDetail;
import com.wms.generated.model.LocationListItem;
import com.wms.generated.model.LocationPageResponse;
import com.wms.generated.model.LocationToggleResponse;
import com.wms.generated.model.LocationUpdateResponse;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateLocationRequest;
import com.wms.master.entity.Area;
import com.wms.master.entity.Location;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
import com.wms.master.service.LocationService;
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
 * ロケーションマスタ CRUD コントローラー。
 * OpenAPI生成の MasterFacilityApi は全施設を含むため個別コントローラーとして定義する。
 * 全施設の実装完了後に MasterFacilityApi implements へのリファクタリングを検討する。
 */
@RestController
@RequestMapping("/api/v1/master/locations")
@RequiredArgsConstructor
@Validated
@Slf4j
public class LocationController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "locationCode", "locationName", "isActive", "createdAt", "updatedAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final LocationService locationService;
    private final AreaService areaService;
    private final WarehouseService warehouseService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<LocationPageResponse> listLocations(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) String codePrefix,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            @RequestParam(defaultValue = "locationCode,asc") String sort) {

        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Sort sortObj = parseSort(sort);
        Page<Location> resultPage = locationService.search(
                warehouseId, areaId, codePrefix, isActive,
                PageRequest.of(page, cappedSize, sortObj));

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
    @GetMapping("/count")
    public ResponseEntity<CountResponse> countLocations(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Boolean isActive) {
        Boolean effectiveIsActive = isActive != null ? isActive : Boolean.TRUE;
        long total = locationService.count(warehouseId, areaId, effectiveIsActive);
        return ResponseEntity.ok(new CountResponse().count((int) total));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PostMapping
    public ResponseEntity<LocationDetail> createLocation(
            @Valid @RequestBody CreateLocationRequest request) {
        Location location = new Location();
        location.setAreaId(request.getAreaId());
        location.setLocationCode(request.getLocationCode());
        location.setLocationName(request.getLocationName());

        Location created = locationService.create(location);
        log.info("Location created via API: id={}", created.getId());

        Area area = areaService.findById(created.getAreaId());
        Warehouse warehouse = warehouseService.findById(created.getWarehouseId());
        URI locationUri = URI.create("/api/v1/master/locations/" + created.getId());
        return ResponseEntity.created(locationUri).body(toDetail(created, area, warehouse));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<LocationDetail> getLocation(@PathVariable Long id) {
        Location location = locationService.findById(id);
        Area area = areaService.findById(location.getAreaId());
        Warehouse warehouse = warehouseService.findById(location.getWarehouseId());
        return ResponseEntity.ok(toDetail(location, area, warehouse));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<LocationUpdateResponse> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLocationRequest request) {
        Location updated = locationService.update(id, request.getLocationName(), request.getVersion());
        log.info("Location updated via API: id={}", id);
        return ResponseEntity.ok(toUpdateResponse(updated));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<LocationToggleResponse> toggleLocationActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request) {
        Location updated = locationService.toggleActive(id, request.getIsActive(), request.getVersion());
        log.info("Location toggled via API: id={}, isActive={}", id, request.getIsActive());
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
