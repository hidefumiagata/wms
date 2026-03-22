package com.wms.master.service;

import com.wms.master.entity.Warehouse;
import static com.wms.shared.util.LikeEscapeUtil.escape;
import com.wms.master.repository.WarehouseRepository;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public Page<Warehouse> search(String warehouseCode, String warehouseName,
                                  Boolean isActive, Pageable pageable) {
        return warehouseRepository.search(escape(warehouseCode), escape(warehouseName), isActive, pageable);
    }

    private static final int FIND_ALL_SIMPLE_LIMIT = 1000;

    public List<Warehouse> findAllSimple(Boolean isActive) {
        List<Warehouse> all = warehouseRepository.findAllSimple(isActive);
        if (all.size() > FIND_ALL_SIMPLE_LIMIT) {
            log.warn("findAllSimple: 倉庫件数が上限を超過しています (count={}, limit={})", all.size(), FIND_ALL_SIMPLE_LIMIT);
            return all.subList(0, FIND_ALL_SIMPLE_LIMIT);
        }
        return all;
    }

    public Warehouse findById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "WAREHOUSE_NOT_FOUND", "倉庫", id));
    }

    public Map<Long, Warehouse> findByIds(Collection<Long> ids) {
        return warehouseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));
    }

    @Transactional
    public Warehouse create(Warehouse warehouse) {
        if (warehouseRepository.existsByWarehouseCode(warehouse.getWarehouseCode())) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "倉庫コードが既に存在します: " + warehouse.getWarehouseCode());
        }
        try {
            Warehouse created = warehouseRepository.save(warehouse);
            log.info("Warehouse created: code={}", created.getWarehouseCode());
            return created;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "倉庫コードが既に存在します: " + warehouse.getWarehouseCode());
        }
    }

    @Transactional
    public Warehouse update(Long id, String warehouseName, String warehouseNameKana,
                            String address, Integer version) {
        Warehouse warehouse = findById(id);
        if (!warehouse.getVersion().equals(version)) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        warehouse.setWarehouseName(warehouseName);
        warehouse.setWarehouseNameKana(warehouseNameKana);
        warehouse.setAddress(address);
        warehouse.setVersion(version);
        try {
            Warehouse saved = warehouseRepository.save(warehouse);
            log.info("Warehouse updated: id={}, name={}", id, warehouseName);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    @Transactional
    public Warehouse toggleActive(Long id, boolean isActive, Integer version) {
        Warehouse warehouse = findById(id);
        if (!warehouse.getVersion().equals(version)) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        // TODO: 在庫テーブル実装後に在庫存在チェックを追加（無効化時）
        if (warehouse.getIsActive().equals(isActive)) {
            log.info("Warehouse toggleActive no-op: id={}, isActive={}", id, isActive);
            return warehouse;
        }
        if (isActive) {
            warehouse.activate();
        } else {
            warehouse.deactivate();
        }
        warehouse.setVersion(version);
        try {
            Warehouse saved = warehouseRepository.save(warehouse);
            log.info("Warehouse toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    public boolean existsByCode(String warehouseCode) {
        return warehouseRepository.existsByWarehouseCode(warehouseCode);
    }
}
