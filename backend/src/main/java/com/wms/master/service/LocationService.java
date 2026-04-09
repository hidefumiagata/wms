package com.wms.master.service;

import com.wms.inventory.service.InventoryService;
import com.wms.master.entity.Location;
import static com.wms.shared.util.LikeEscapeUtil.escape;
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
    private final InventoryService inventoryService;

    /** INBOUND/OUTBOUND/RETURN エリアに登録可能なロケーション最大件数 */
    private static final long SINGLE_LOCATION_AREA_LIMIT = 1L;

    /** STOCK エリアのロケーションコード形式: 棟-フロア-エリア-棚-段-並び */
    private static final Pattern STOCK_LOCATION_CODE_PATTERN =
            Pattern.compile("^[A-Z]-\\d{2}-[A-Z]-\\d{2}-\\d{2}-\\d{2}$");

    public Page<Location> search(Long warehouseId, Long areaId,
                                  String codePrefix, Boolean isActive, Pageable pageable) {
        return locationRepository.search(warehouseId, areaId, escape(codePrefix), isActive, pageable);
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
            // OWASP A09: 例外メッセージには内部 id を含めない。追跡用の areaId は log 側に出力。
            log.info("Location create blocked by area limit: areaId={}, areaType={}",
                    area.getId(), area.getAreaType());
            throw new BusinessRuleViolationException("AREA_LOCATION_LIMIT_EXCEEDED",
                    area.getAreaType() + " エリアにはロケーションを1件のみ登録できます");
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
            log.info("Location update version mismatch: id={}, expected={}, actual={}",
                    id, version, location.getVersion());
            throw OptimisticLockConflictException.standard();
        }
        location.setLocationName(locationName);
        location.setVersion(version);
        try {
            Location saved = locationRepository.save(location);
            log.info("Location updated: id={}, name={}", id, locationName);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Location update OL conflict detected at commit: id={}", id);
            throw OptimisticLockConflictException.standard();
        }
    }

    @Transactional
    public Location toggleActive(Long id, boolean isActive, Integer version) {
        Location location = findById(id);
        if (!location.getVersion().equals(version)) {
            log.info("Location toggleActive version mismatch: id={}, expected={}, actual={}",
                    id, version, location.getVersion());
            throw OptimisticLockConflictException.standard();
        }
        // 冪等性のため、状態が実際に変化しない場合は早期 return（後続の在庫チェックも省略）
        if (location.getIsActive().equals(isActive)) {
            log.info("Location toggleActive no-op: id={}, isActive={}", id, isActive);
            return location;
        }
        // 無効化時のチェック順序は API-02 BR-003 / mermaid 準拠:
        // 在庫チェックを先に評価する。在庫なしで初めて棚卸中チェックに進む。
        // (上の no-op 判定により、状態が実際に変化する場合のみここに到達する)
        // 無効化時: 在庫が存在するロケーションは無効化不可 (BR: CANNOT_DEACTIVATE_HAS_INVENTORY)
        if (!isActive && inventoryService.hasInventoryByLocationId(id)) {
            // OWASP A09: 例外メッセージには内部 id を含めない。id は log 側に出力。
            log.info("Location deactivate blocked by inventory: id={}", id);
            throw new BusinessRuleViolationException(
                    "CANNOT_DEACTIVATE_HAS_INVENTORY",
                    "在庫が存在するため無効化できません");
        }
        // 無効化時: 棚卸中のロケーションは無効化不可 (BR: CANNOT_DEACTIVATE_STOCKTAKE_IN_PROGRESS)
        if (!isActive && location.getIsStocktakingLocked()) {
            log.info("Location deactivate blocked by stocktake lock: id={}", id);
            throw new BusinessRuleViolationException(
                    "CANNOT_DEACTIVATE_STOCKTAKE_IN_PROGRESS",
                    "棚卸中のため無効化できません");
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
            log.info("Location toggleActive OL conflict detected at commit: id={}", id);
            throw OptimisticLockConflictException.standard();
        }
    }
}
