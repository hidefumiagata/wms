package com.wms.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "daily_summary_records")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySummaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "inbound_count", nullable = false)
    private Integer inboundCount;

    @Column(name = "inbound_line_count", nullable = false)
    private Integer inboundLineCount;

    @Column(name = "inbound_quantity_total", nullable = false)
    private Long inboundQuantityTotal;

    @Column(name = "outbound_count", nullable = false)
    private Integer outboundCount;

    @Column(name = "outbound_line_count", nullable = false)
    private Integer outboundLineCount;

    @Column(name = "outbound_quantity_total", nullable = false)
    private Long outboundQuantityTotal;

    @Column(name = "return_count", nullable = false)
    private Integer returnCount;

    @Column(name = "return_quantity_total", nullable = false)
    private Integer returnQuantityTotal;

    @Column(name = "inventory_quantity_total", nullable = false)
    private Long inventoryQuantityTotal;

    @Column(name = "unreceived_count", nullable = false)
    private Integer unreceivedCount;

    @Column(name = "unshipped_count", nullable = false)
    private Integer unshippedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
