package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inbound_slips")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundSlip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slip_number", nullable = false, unique = true, length = 50)
    private String slipNumber;

    @Column(name = "slip_type", nullable = false, length = 30)
    private String slipType;

    @Column(name = "transfer_slip_number", length = 50)
    private String transferSlipNumber;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "warehouse_name", nullable = false, length = 200)
    private String warehouseName;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "partner_code", length = 50)
    private String partnerCode;

    @Column(name = "partner_name", length = 200)
    private String partnerName;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Setter
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Setter
    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Setter
    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @OneToMany(mappedBy = "inboundSlip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<InboundSlipLine> lines = new ArrayList<>();

    public void addLine(InboundSlipLine line) {
        lines.add(line);
        line.setInboundSlip(this);
    }
}
