package com.wms.report.repository;

import com.wms.report.entity.UnreceivedListRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UnreceivedListRecordRepository extends JpaRepository<UnreceivedListRecord, Long> {

    @Query("""
            SELECT r FROM UnreceivedListRecord r
            WHERE r.batchBusinessDate = :batchBusinessDate
              AND r.warehouseCode = :warehouseCode
            ORDER BY r.partnerName ASC, r.plannedDate ASC, r.slipNumber ASC
            """)
    List<UnreceivedListRecord> findByBatchBusinessDateAndWarehouseCode(
            @Param("batchBusinessDate") LocalDate batchBusinessDate,
            @Param("warehouseCode") String warehouseCode);
}
