package com.wms.inbound.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inbound_slip_lines")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundSlipLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_slip_id", nullable = false)
    private InboundSlip inboundSlip;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_type", nullable = false, length = 10)
    private String unitType;

    @Column(name = "planned_qty", nullable = false)
    private Integer plannedQty;

    @Setter
    @Column(name = "inspected_qty")
    private Integer inspectedQty;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Setter
    @Column(name = "putaway_location_id")
    private Long putawayLocationId;

    @Setter
    @Column(name = "putaway_location_code", length = 50)
    private String putawayLocationCode;

    @Setter
    @Column(name = "line_status", nullable = false, length = 20)
    private String lineStatus;

    @Setter
    @Column(name = "inspected_at")
    private OffsetDateTime inspectedAt;

    @Setter
    @Column(name = "inspected_by")
    private Long inspectedBy;

    @Setter
    @Column(name = "stored_at")
    private OffsetDateTime storedAt;

    @Setter
    @Column(name = "stored_by")
    private Long storedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
