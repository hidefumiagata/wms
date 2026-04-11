package com.wms.master.service;

import com.wms.inventory.service.InventoryService;
import com.wms.master.entity.Product;
import static com.wms.shared.util.LikeEscapeUtil.escape;
import com.wms.master.repository.ProductRepository;
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

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

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
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productRepository.findAllById(ids);
    }

    public record ProductWithInventory(Product product, boolean hasInventory) {}

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "PRODUCT_NOT_FOUND", "商品", id));
    }

    public ProductWithInventory findByIdWithInventoryCheck(Long id) {
        Product product = findById(id);
        boolean hasInventory = inventoryService.hasInventoryByProductId(id);
        return new ProductWithInventory(product, hasInventory);
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

        if (!Objects.equals(product.getVersion(), cmd.version())) {
            log.info("Product update version mismatch: id={}, expected={}, actual={}",
                    cmd.id(), cmd.version(), product.getVersion());
            throw OptimisticLockConflictException.standard();
        }

        boolean lotFlagChanged = !product.getLotManageFlag().equals(cmd.lotManageFlag());
        boolean expiryFlagChanged = !product.getExpiryManageFlag().equals(cmd.expiryManageFlag());
        if ((lotFlagChanged || expiryFlagChanged) && inventoryService.hasInventoryByProductId(cmd.id())) {
            if (lotFlagChanged) {
                // OWASP A09: 例外メッセージには内部 id を含めない。id は log 側に出力。
                log.info("Product lot manage flag change blocked by inventory: id={}", cmd.id());
                throw new BusinessRuleViolationException("CANNOT_CHANGE_LOT_MANAGE_FLAG",
                        "在庫が存在するためロット管理フラグを変更できません");
            }
            log.info("Product expiry manage flag change blocked by inventory: id={}", cmd.id());
            throw new BusinessRuleViolationException("CANNOT_CHANGE_EXPIRY_MANAGE_FLAG",
                    "在庫が存在するため期限管理フラグを変更できません");
        }

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
        // version は事前チェックで一致確認済みのため再代入不要。
        try {
            Product saved = productRepository.save(product);
            log.info("Product updated: id={}, name={}", cmd.id(), cmd.productName());
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Product update OL conflict detected at commit: id={}", cmd.id());
            throw OptimisticLockConflictException.standard();
        }
    }

    @Transactional
    public Product toggleActive(Long id, boolean isActive, Integer version) {
        Product product = findById(id);
        // API-04 §4 業務フロー: CHECK_VERSION → CHECK_SAME → BR check の順で評価する
        if (!Objects.equals(product.getVersion(), version)) {
            log.info("Product toggleActive version mismatch: id={}, expected={}, actual={}",
                    id, version, product.getVersion());
            throw OptimisticLockConflictException.standard();
        }
        if (Objects.equals(product.getIsActive(), isActive)) {
            log.info("Product toggleActive no-op: id={}, isActive={}", id, isActive);
            return product;
        }
        if (!isActive && inventoryService.hasInventoryByProductId(id)) {
            // OWASP A09: 例外メッセージには内部 id を含めない。id は log 側に出力。
            log.info("Product deactivate blocked by inventory: id={}", id);
            throw new BusinessRuleViolationException("CANNOT_DEACTIVATE_HAS_INVENTORY",
                    "在庫が存在するため商品を無効化できません");
        }
        if (isActive) {
            product.activate();
        } else {
            product.deactivate();
        }
        // version は事前チェックで一致確認済みのため再代入不要。
        try {
            Product saved = productRepository.save(product);
            log.info("Product toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Product toggleActive OL conflict detected at commit: id={}", id);
            throw OptimisticLockConflictException.standard();
        }
    }

    public boolean hasInventory(Long productId) {
        return inventoryService.hasInventoryByProductId(productId);
    }

    public boolean existsByCode(String productCode) {
        return productRepository.existsByProductCode(productCode);
    }
}
