package com.wms.master.repository;

import com.wms.master.entity.Partner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    boolean existsByPartnerCode(String partnerCode);

    /**
     * 取引先一覧検索。partnerType フィルタは以下のロジックで処理する:
     * - SUPPLIER 指定時: SUPPLIER または BOTH を対象
     * - CUSTOMER 指定時: CUSTOMER または BOTH を対象
     * - BOTH 指定時: BOTH のみを対象
     * - null の場合: 全種別対象
     */
    // TODO: #72 partnerCode / partnerName の LIKE 検索で % / _ がエスケープされていない
    @Query("SELECT p FROM Partner p WHERE "
            + "(:partnerCode IS NULL OR p.partnerCode LIKE CONCAT(:partnerCode, '%')) "
            + "AND (:partnerName IS NULL OR p.partnerName LIKE CONCAT('%', :partnerName, '%') "
            + "     OR p.partnerNameKana LIKE CONCAT('%', :partnerName, '%')) "
            + "AND (:partnerType IS NULL "
            + "     OR (:partnerType = 'SUPPLIER' AND p.partnerType IN ('SUPPLIER', 'BOTH')) "
            + "     OR (:partnerType = 'CUSTOMER' AND p.partnerType IN ('CUSTOMER', 'BOTH')) "
            + "     OR (:partnerType = 'BOTH' AND p.partnerType = 'BOTH')) "
            + "AND (:isActive IS NULL OR p.isActive = :isActive)")
    Page<Partner> search(
            @Param("partnerCode") String partnerCode,
            @Param("partnerName") String partnerName,
            @Param("partnerType") String partnerType,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    // TODO: #73 件数増大時の上限設定を検討（例: 1000件超で警告ログ）
    @Query("SELECT p FROM Partner p WHERE (:isActive IS NULL OR p.isActive = :isActive) ORDER BY p.partnerCode ASC")
    List<Partner> findAllSimple(@Param("isActive") Boolean isActive);
}
