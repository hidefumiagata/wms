package com.wms.master.service;

import com.wms.master.entity.Building;
import static com.wms.shared.util.LikeEscapeUtil.escape;
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
import java.util.Objects;
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
        return buildingRepository.search(warehouseId, escape(buildingCode), escape(buildingName), isActive, pageable);
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
        if (!Objects.equals(building.getVersion(), version)) {
            log.info("Building update version mismatch: id={}, expected={}, actual={}",
                    id, version, building.getVersion());
            throw OptimisticLockConflictException.standard();
        }
        building.setBuildingName(buildingName);
        // version は事前チェックで一致確認済みのため再代入不要。
        try {
            Building saved = buildingRepository.save(building);
            log.info("Building updated: id={}, name={}", id, buildingName);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Building update OL conflict detected at commit: id={}", id);
            throw OptimisticLockConflictException.standard();
        }
    }

    @Transactional
    public Building toggleActive(Long id, boolean isActive, Integer version) {
        Building building = findById(id);
        // API-02 §4 業務フロー: CHECK_VERSION → CHECK_SAME → BR check の順で評価する
        if (!Objects.equals(building.getVersion(), version)) {
            log.info("Building toggleActive version mismatch: id={}, expected={}, actual={}",
                    id, version, building.getVersion());
            throw OptimisticLockConflictException.standard();
        }
        if (Objects.equals(building.getIsActive(), isActive)) {
            log.info("Building toggleActive no-op: id={}, isActive={}", id, isActive);
            return building;
        }
        if (!isActive && areaRepository.countByBuildingId(id) > 0) {
            // OWASP A09: 例外メッセージには内部 id を含めない。id は log 側に出力。
            log.info("Building deactivate blocked by children areas: id={}", id);
            throw new BusinessRuleViolationException("CANNOT_DEACTIVATE_HAS_CHILDREN",
                    "配下にエリアが存在するため無効化できません");
        }
        if (isActive) {
            building.activate();
        } else {
            building.deactivate();
        }
        // version は事前チェックで一致確認済みのため再代入不要。
        try {
            Building saved = buildingRepository.save(building);
            log.info("Building toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Building toggleActive OL conflict detected at commit: id={}", id);
            throw OptimisticLockConflictException.standard();
        }
    }

    public boolean existsByWarehouseIdAndCode(Long warehouseId, String code) {
        return buildingRepository.existsByWarehouseIdAndBuildingCode(warehouseId, code);
    }
}
