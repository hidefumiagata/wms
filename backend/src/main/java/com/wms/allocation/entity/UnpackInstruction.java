package com.wms.allocation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Entity
@Table(name = "unpack_instructions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnpackInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbound_slip_id", nullable = false)
    private Long outboundSlipId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "from_unit_type", nullable = false, length = 10)
    private String fromUnitType;

    @Column(name = "from_qty", nullable = false)
    private Integer fromQty;

    @Column(name = "to_unit_type", nullable = false, length = 10)
    private String toUnitType;

    @Column(name = "to_qty", nullable = false)
    private Integer toQty;

    @Setter
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "source_inventory_id")
    private Long sourceInventoryId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Setter
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Setter
    @Column(name = "completed_by")
    private Long completedBy;
}
