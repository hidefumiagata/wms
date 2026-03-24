package com.wms.master.service;

import com.wms.master.entity.Product;
import static com.wms.shared.util.LikeEscapeUtil.escape;
import com.wms.master.repository.ProductRepository;
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

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public Page<Product> search(String productCode, String productName,
                                String storageCondition, Boolean isActive,
                                Boolean shipmentStopFlag, Pageable pageable) {
        return productRepository.search(escape(productCode), escape(productName), storageCondition,
                isActive, shipmentStopFlag, pageable);
    }

    private static final int FIND_ALL_SIMPLE_LIMIT = 1000;

    public List<Product> findAllSimple(Boolean isActive) {
        List<Product> all = productRepository.findAllSimple(isActive);
        if (all.size() > FIND_ALL_SIMPLE_LIMIT) {
            log.warn("findAllSimple: 商品件数が上限を超過しています (count={}, limit={})", all.size(), FIND_ALL_SIMPLE_LIMIT);
            return all.subList(0, FIND_ALL_SIMPLE_LIMIT);
        }
        return all;
    }

    public List<Product> findAllByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return productRepository.findAllById(ids);
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "PRODUCT_NOT_FOUND", "商品", id));
    }

    @Transactional
    public Product create(Product product) {
        if (productRepository.existsByProductCode(product.getProductCode())) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "商品コードが既に存在します: " + product.getProductCode());
        }
        try {
            Product created = productRepository.save(product);
            log.info("Product created: code={}, storageCondition={}",
                    created.getProductCode(), created.getStorageCondition());
            return created;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "商品コードが既に存在します: " + product.getProductCode());
        }
    }

    @Transactional
    public Product update(UpdateProductCommand cmd) {
        Product product = findById(cmd.id());

        if (!product.getVersion().equals(cmd.version())) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + cmd.id() + ")");
        }

        // TODO: 在庫テーブル実装後に lotManageFlag / expiryManageFlag 変更時の在庫存在チェックを追加
        //       在庫あり && フラグ変更 → CANNOT_CHANGE_LOT_MANAGE_FLAG / CANNOT_CHANGE_EXPIRY_MANAGE_FLAG (422)

        product.setProductName(cmd.productName());
        product.setProductNameKana(cmd.productNameKana());
        product.setCaseQuantity(cmd.caseQuantity());
        product.setBallQuantity(cmd.ballQuantity());
        product.setBarcode(cmd.barcode());
        product.setStorageCondition(cmd.storageCondition());
        product.setIsHazardous(cmd.isHazardous());
        product.setLotManageFlag(cmd.lotManageFlag());
        product.setExpiryManageFlag(cmd.expiryManageFlag());
        product.setShipmentStopFlag(cmd.shipmentStopFlag());
        if (Boolean.TRUE.equals(cmd.isActive())) {
            product.activate();
        } else {
            product.deactivate();
        }
        product.setVersion(cmd.version());
        try {
            Product saved = productRepository.save(product);
            log.info("Product updated: id={}, name={}", cmd.id(), cmd.productName());
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + cmd.id() + ")");
        }
    }

    @Transactional
    public Product toggleActive(Long id, boolean isActive, Integer version) {
        Product product = findById(id);

        if (!product.getVersion().equals(version)) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }

        if (!isActive) {
            // TODO: 在庫テーブル実装後に在庫存在チェックを追加
            //       在庫あり → CANNOT_DEACTIVATE_HAS_INVENTORY (422)
        }
        if (product.getIsActive().equals(isActive)) {
            log.info("Product toggleActive no-op: id={}, isActive={}", id, isActive);
            return product;
        }
        if (isActive) {
            product.activate();
        } else {
            product.deactivate();
        }
        product.setVersion(version);
        try {
            Product saved = productRepository.save(product);
            log.info("Product toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    public boolean existsByCode(String productCode) {
        return productRepository.existsByProductCode(productCode);
    }
}
