package com.wms.master.service;

import com.wms.master.entity.Product;
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
        return productRepository.search(productCode, productName, storageCondition,
                isActive, shipmentStopFlag, pageable);
    }

    public List<Product> findAllSimple(Boolean isActive) {
        return productRepository.findAllSimple(isActive);
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

    // TODO: 引数が13個と多い。UpdateProductCommand 等の record DTO への移行を検討（Issue #71 パターン）
    @Transactional
    public Product update(Long id, String productName, String productNameKana,
                          Integer caseQuantity, Integer ballQuantity, String barcode,
                          String storageCondition, Boolean isHazardous,
                          Boolean lotManageFlag, Boolean expiryManageFlag,
                          Boolean shipmentStopFlag, Boolean isActive, Integer version) {
        Product product = findById(id);

        if (!product.getVersion().equals(version)) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }

        // TODO: 在庫テーブル実装後に lotManageFlag / expiryManageFlag 変更時の在庫存在チェックを追加
        //       在庫あり && フラグ変更 → CANNOT_CHANGE_LOT_MANAGE_FLAG / CANNOT_CHANGE_EXPIRY_MANAGE_FLAG (422)

        product.setProductName(productName);
        product.setProductNameKana(productNameKana);
        product.setCaseQuantity(caseQuantity);
        product.setBallQuantity(ballQuantity);
        product.setBarcode(barcode);
        product.setStorageCondition(storageCondition);
        product.setIsHazardous(isHazardous);
        product.setLotManageFlag(lotManageFlag);
        product.setExpiryManageFlag(expiryManageFlag);
        product.setShipmentStopFlag(shipmentStopFlag);
        if (Boolean.TRUE.equals(isActive)) {
            product.activate();
        } else {
            product.deactivate();
        }
        product.setVersion(version);
        try {
            Product saved = productRepository.save(product);
            log.info("Product updated: id={}, name={}", id, productName);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
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
