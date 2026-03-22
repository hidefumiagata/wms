package com.wms.outbound.repository;

import com.wms.outbound.entity.PickingInstructionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PickingInstructionLineRepository extends JpaRepository<PickingInstructionLine, Long> {

    List<PickingInstructionLine> findByOutboundSlipLineIdIn(List<Long> outboundSlipLineIds);

    @Modifying
    @Query("DELETE FROM PickingInstructionLine l WHERE l.outboundSlipLineId = :outboundSlipLineId")
    void deleteByOutboundSlipLineId(@Param("outboundSlipLineId") Long outboundSlipLineId);

    @Modifying
    @Query("DELETE FROM PickingInstructionLine l WHERE l.outboundSlipLineId IN :outboundSlipLineIds")
    void deleteByOutboundSlipLineIdIn(@Param("outboundSlipLineIds") List<Long> outboundSlipLineIds);
}
