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
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "batch_execution_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_business_date", nullable = false)
    private LocalDate targetBusinessDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "step1_status", length = 20)
    private String step1Status;

    @Column(name = "step2_status", length = 20)
    private String step2Status;

    @Column(name = "step3_status", length = 20)
    private String step3Status;

    @Column(name = "step4_status", length = 20)
    private String step4Status;

    @Column(name = "step5_status", length = 20)
    private String step5Status;

    @Column(name = "step6_status", length = 20)
    private String step6Status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "executed_by", nullable = false)
    private Long executedBy;

    public String getStepStatus(int step) {
        return switch (step) {
            case 1 -> step1Status;
            case 2 -> step2Status;
            case 3 -> step3Status;
            case 4 -> step4Status;
            case 5 -> step5Status;
            case 6 -> step6Status;
            default -> throw new IllegalArgumentException("Invalid step: " + step);
        };
    }

    public void setStepStatus(int step, String status) {
        switch (step) {
            case 1 -> this.step1Status = status;
            case 2 -> this.step2Status = status;
            case 3 -> this.step3Status = status;
            case 4 -> this.step4Status = status;
            case 5 -> this.step5Status = status;
            case 6 -> this.step6Status = status;
            default -> throw new IllegalArgumentException("Invalid step: " + step);
        }
    }
}
