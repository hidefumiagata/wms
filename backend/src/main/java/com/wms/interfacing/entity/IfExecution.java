package com.wms.interfacing.entity;

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
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Entity
@Table(name = "if_executions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "if_type", nullable = false, length = 30)
    private String ifType;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "blob_path", length = 1000)
    private String blobPath;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    @Column(name = "error_count", nullable = false)
    private Integer errorCount;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Setter
    @ColumnDefault("true")
    @Column(name = "blob_move_failed", nullable = false)
    private Boolean blobMoveFailed;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    @Column(name = "executed_by", nullable = false)
    private Long executedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
