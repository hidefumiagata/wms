package com.wms.outbound.entity;

import jakarta.persistence.*;
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
@Table(name = "picking_instruction_lines")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingInstructionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "picking_instruction_id", nullable = false)
    private PickingInstruction pickingInstruction;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "outbound_slip_line_id", nullable = false)
    private Long outboundSlipLineId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "location_code", nullable = false, length = 50)
    private String locationCode;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_type", nullable = false, length = 10)
    private String unitType;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "qty_to_pick", nullable = false)
    private Integer qtyToPick;

    @Setter
    @Column(name = "qty_picked", nullable = false)
    private Integer qtyPicked;

    @Setter
    @Column(name = "line_status", nullable = false, length = 20)
    private String lineStatus;

    @Setter
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Setter
    @Column(name = "completed_by")
    private Long completedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
