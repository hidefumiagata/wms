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
@Table(name = "unreceived_list_records")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnreceivedListRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_business_date", nullable = false)
    private LocalDate batchBusinessDate;

    @Column(name = "inbound_slip_id", nullable = false)
    private Long inboundSlipId;

    @Column(name = "slip_number", nullable = false, length = 50)
    private String slipNumber;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "partner_code", length = 50)
    private String partnerCode;

    @Column(name = "partner_name", length = 200)
    private String partnerName;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_type", nullable = false, length = 10)
    private String unitType;

    @Column(name = "planned_qty", nullable = false)
    private Integer plannedQty;

    @Column(name = "current_status", nullable = false, length = 30)
    private String currentStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
