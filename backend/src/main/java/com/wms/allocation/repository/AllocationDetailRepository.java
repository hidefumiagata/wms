package com.wms.allocation.repository;

import com.wms.allocation.entity.AllocationDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AllocationDetailRepository extends JpaRepository<AllocationDetail, Long> {

    List<AllocationDetail> findByOutboundSlipId(Long outboundSlipId);

    List<AllocationDetail> findByOutboundSlipLineId(Long outboundSlipLineId);

    @Modifying
    @Query("DELETE FROM AllocationDetail a WHERE a.outboundSlipId = :slipId")
    void deleteByOutboundSlipId(@Param("slipId") Long slipId);
}
