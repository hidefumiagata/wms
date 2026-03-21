package com.wms.master.service;

import com.wms.master.entity.Building;
import com.wms.master.repository.AreaRepository;
import com.wms.master.repository.BuildingRepository;
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
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final AreaRepository areaRepository;

    public Page<Building> search(Long warehouseId, String buildingCode,
                                  String buildingName, Boolean isActive, Pageable pageable) {
        return buildingRepository.search(warehouseId, buildingCode, buildingName, isActive, pageable);
    }

    public Building findById(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("BUILDING_NOT_FOUND", "棟", id));
    }

    public Map<Long, Building> findByIds(Collection<Long> ids) {
        return buildingRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Building::getId, b -> b));
    }

    @Transactional
    public Building create(Building building) {
        if (buildingRepository.existsByWarehouseIdAndBuildingCode(
                building.getWarehouseId(), building.getBuildingCode())) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "棟コードが既に存在します: " + building.getBuildingCode());
        }
        try {
            Building created = buildingRepository.save(building);
            log.info("Building created: warehouseId={}, code={}", created.getWarehouseId(), created.getBuildingCode());
            return created;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "棟コードが既に存在します: " + building.getBuildingCode());
        }
    }

    @Transactional
    public Building update(Long id, String buildingName, Integer version) {
        Building building = findById(id);
        if (!building.getVersion().equals(version)) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        building.setBuildingName(buildingName);
        building.setVersion(version);
        try {
            Building saved = buildingRepository.save(building);
            log.info("Building updated: id={}, name={}", id, buildingName);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    @Transactional
    public Building toggleActive(Long id, boolean isActive, Integer version) {
        Building building = findById(id);
        if (!building.getVersion().equals(version)) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        if (!isActive && areaRepository.countByBuildingIdAndIsActiveTrue(id) > 0) {
            throw new BusinessRuleViolationException("CANNOT_DEACTIVATE_HAS_CHILDREN",
                    "配下に有効なエリアが存在するため無効化できません (id=" + id + ")");
        }
        if (building.getIsActive().equals(isActive)) {
            log.info("Building toggleActive no-op: id={}, isActive={}", id, isActive);
            return building;
        }
        if (isActive) {
            building.activate();
        } else {
            building.deactivate();
        }
        building.setVersion(version);
        try {
            Building saved = buildingRepository.save(building);
            log.info("Building toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    public boolean existsByWarehouseIdAndCode(Long warehouseId, String code) {
        return buildingRepository.existsByWarehouseIdAndBuildingCode(warehouseId, code);
    }
}
