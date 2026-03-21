package com.wms.master.service;

import com.wms.master.entity.Area;
import com.wms.master.repository.AreaRepository;
import com.wms.master.repository.BuildingRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AreaService {

    private final AreaRepository areaRepository;
    private final BuildingRepository buildingRepository;
    private final LocationRepository locationRepository;

    public Page<Area> search(Long warehouseId, Long buildingId, String areaType,
                              String storageCondition, Boolean isActive, Pageable pageable) {
        return areaRepository.search(warehouseId, buildingId, areaType, storageCondition, isActive, pageable);
    }

    public Area findById(Long id) {
        return areaRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AREA_NOT_FOUND", "エリア", id));
    }

    public Map<Long, Area> findByIds(Collection<Long> ids) {
        return areaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Area::getId, a -> a));
    }

    @Transactional
    public Area create(Area area) {
        // 棟の存在確認 & warehouseId 導出
        var building = buildingRepository.findById(area.getBuildingId())
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "BUILDING_NOT_FOUND", "棟", area.getBuildingId()));
        area.setWarehouseId(building.getWarehouseId());

        if (areaRepository.existsByBuildingIdAndAreaCode(area.getBuildingId(), area.getAreaCode())) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "エリアコードが既に存在します: " + area.getAreaCode());
        }
        try {
            Area created = areaRepository.save(area);
            log.info("Area created: buildingId={}, code={}, type={}", created.getBuildingId(), created.getAreaCode(), created.getAreaType());
            return created;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "エリアコードが既に存在します: " + area.getAreaCode());
        }
    }

    @Transactional
    public Area update(Long id, String areaName, String storageCondition, Integer version) {
        Area area = findById(id);
        if (!area.getVersion().equals(version)) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        area.setAreaName(areaName);
        area.setStorageCondition(storageCondition);
        area.setVersion(version);
        try {
            Area saved = areaRepository.save(area);
            log.info("Area updated: id={}, name={}", id, areaName);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    @Transactional
    public Area toggleActive(Long id, boolean isActive, Integer version) {
        Area area = findById(id);
        if (!area.getVersion().equals(version)) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        if (!isActive && locationRepository.countByAreaIdAndIsActiveTrue(id) > 0) {
            throw new BusinessRuleViolationException("CANNOT_DEACTIVATE_HAS_CHILDREN",
                    "配下に有効なロケーションが存在するため無効化できません (id=" + id + ")");
        }
        if (area.getIsActive().equals(isActive)) {
            log.info("Area toggleActive no-op: id={}, isActive={}", id, isActive);
            return area;
        }
        if (isActive) {
            area.activate();
        } else {
            area.deactivate();
        }
        area.setVersion(version);
        try {
            Area saved = areaRepository.save(area);
            log.info("Area toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }
}
