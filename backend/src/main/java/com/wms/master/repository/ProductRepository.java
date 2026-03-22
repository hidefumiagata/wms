package com.wms.master.repository;

import com.wms.master.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByProductCode(String productCode);

    /**
     * 商品一覧検索。productName はカナ名も対象にした部分一致。
     */
    @Query("SELECT p FROM Product p WHERE "
            + "(:productCode IS NULL OR p.productCode LIKE CONCAT(:productCode, '%') ESCAPE '\\') "
            + "AND (:productName IS NULL OR p.productName LIKE CONCAT('%', :productName, '%') ESCAPE '\\' "
            + "     OR p.productNameKana LIKE CONCAT('%', :productName, '%') ESCAPE '\\') "
            + "AND (:storageCondition IS NULL OR p.storageCondition = :storageCondition) "
            + "AND (:isActive IS NULL OR p.isActive = :isActive) "
            + "AND (:shipmentStopFlag IS NULL OR p.shipmentStopFlag = :shipmentStopFlag)")
    Page<Product> search(
            @Param("productCode") String productCode,
            @Param("productName") String productName,
            @Param("storageCondition") String storageCondition,
            @Param("isActive") Boolean isActive,
            @Param("shipmentStopFlag") Boolean shipmentStopFlag,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE (:isActive IS NULL OR p.isActive = :isActive) ORDER BY p.productCode ASC")
    List<Product> findAllSimple(@Param("isActive") Boolean isActive);
}
