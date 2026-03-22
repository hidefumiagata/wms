package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundSlipLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboundSlipLineRepository extends JpaRepository<InboundSlipLine, Long> {

    List<InboundSlipLine> findByInboundSlipIdOrderByLineNoAsc(Long inboundSlipId);

    long countByInboundSlipId(Long inboundSlipId);
}
