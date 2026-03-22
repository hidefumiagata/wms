package com.wms.master.service;

import com.wms.master.entity.Location;
import com.wms.master.repository.AreaRepository;
import com.wms.master.repository.LocationRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final AreaRepository areaRepository;

    /** INBOUND/OUTBOUND/RETURN エリアに登録可能なロケーション最大件数 */
    private static final long SINGLE_LOCATION_AREA_LIMIT = 1L;

    /** STOCK エリアのロケーションコード形式: 棟-フロア-エリア-棚-段-並び */
    private static final Pattern STOCK_LOCATION_CODE_PATTERN =
            Pattern.compile("^[A-Z]-\\d{2}-[A-Z]-\\d{2}-\\d{2}-\\d{2}$");

    public Page<Location> search(Long warehouseId, Long areaId,
                                  String codePrefix, Boolean isActive, Pageable pageable) {
        return locationRepository.search(warehouseId, areaId, codePrefix, isActive, pageable);
    }

    public long count(Long warehouseId, Long buildingId, Long areaId, Boolean isActive) {
        return locationRepository.countFiltered(warehouseId, buildingId, areaId, isActive);
    }

    public Location findById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("LOCATION_NOT_FOUND", "ロケーション", id));
    }

    public Map<Long, Location> findByIds(Collection<Long> ids) {
        return locationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Location::getId, l -> l));
    }

    @Transactional
    public Location create(Location location) {
        // エリアの存在確認 & warehouseId 導出
        var area = areaRepository.findById(location.getAreaId())
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "AREA_NOT_FOUND", "エリア", location.getAreaId()));
        location.setWarehouseId(area.getWarehouseId());

        // STOCK エリアはロケーションコードの形式を強制
        if (area.getAreaType().equals("STOCK")
                && !STOCK_LOCATION_CODE_PATTERN.matcher(location.getLocationCode()).matches()) {
            throw new BusinessRuleViolationException("INVALID_LOCATION_CODE_FORMAT",
                    "在庫エリアのロケーションコードは棟-フロア-エリア-棚-段-並び形式である必要があります: " + location.getLocationCode());
        }

        // INBOUND/OUTBOUND/RETURN エリアはロケーション 1 件限定
        if (!area.getAreaType().equals("STOCK")
                && locationRepository.countByAreaId(area.getId()) >= SINGLE_LOCATION_AREA_LIMIT) {
            throw new BusinessRuleViolationException("AREA_LOCATION_LIMIT_EXCEEDED",
                    area.getAreaType() + " エリアにはロケーションを1件のみ登録できます (areaId=" + area.getId() + ")");
        }

        if (locationRepository.existsByWarehouseIdAndLocationCode(
                location.getWarehouseId(), location.getLocationCode())) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "ロケーションコードが既に存在します: " + location.getLocationCode());
        }
        try {
            Location created = locationRepository.save(location);
            log.info("Location created: warehouseId={}, areaId={}, code={}", created.getWarehouseId(), created.getAreaId(), created.getLocationCode());
            return created;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "ロケーションコードが既に存在します: " + location.getLocationCode());
        }
    }

    @Transactional
    public Location update(Long id, String locationName, Integer version) {
        Location location = findById(id);
        if (!location.getVersion().equals(version)) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        location.setLocationName(locationName);
        location.setVersion(version);
        try {
            Location saved = locationRepository.save(location);
            log.info("Location updated: id={}, name={}", id, locationName);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    @Transactional
    public Location toggleActive(Long id, boolean isActive, Integer version) {
        Location location = findById(id);
        if (!location.getVersion().equals(version)) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        if (!isActive) {
            // TODO: 在庫テーブル実装後に在庫存在チェックを追加
            //       在庫あり → CANNOT_DEACTIVATE_HAS_INVENTORY (422)
        }
        if (location.getIsActive().equals(isActive)) {
            log.info("Location toggleActive no-op: id={}, isActive={}", id, isActive);
            return location;
        }
        if (isActive) {
            location.activate();
        } else {
            location.deactivate();
        }
        location.setVersion(version);
        try {
            Location saved = locationRepository.save(location);
            log.info("Location toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }
}
