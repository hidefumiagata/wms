package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundSlipLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundSlipLineRepository extends JpaRepository<InboundSlipLine, Long> {

    long countByInboundSlipId(Long inboundSlipId);
}
