package com.wms.outbound.repository;

import com.wms.outbound.entity.OutboundSlipLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundSlipLineRepository extends JpaRepository<OutboundSlipLine, Long> {
}
