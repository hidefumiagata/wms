package com.wms.allocation.repository;

import com.wms.allocation.entity.UnpackInstruction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UnpackInstructionRepository extends JpaRepository<UnpackInstruction, Long> {

    @Query("""
            SELECT u FROM UnpackInstruction u
            WHERE (:outboundSlipId IS NULL OR u.outboundSlipId = :outboundSlipId)
              AND (:status IS NULL OR u.status = :status)
            ORDER BY u.createdAt DESC
            """)
    Page<UnpackInstruction> search(
            @Param("outboundSlipId") Long outboundSlipId,
            @Param("status") String status,
            Pageable pageable);

    List<UnpackInstruction> findByOutboundSlipIdAndStatus(Long outboundSlipId, String status);

    @Modifying
    @Query("DELETE FROM UnpackInstruction u WHERE u.outboundSlipId = :slipId AND u.status = 'INSTRUCTED'")
    void deleteInstructedByOutboundSlipId(@Param("slipId") Long slipId);

    @Modifying
    @Query("DELETE FROM UnpackInstruction u WHERE u.outboundSlipId = :slipId")
    void deleteByOutboundSlipId(@Param("slipId") Long slipId);
}
