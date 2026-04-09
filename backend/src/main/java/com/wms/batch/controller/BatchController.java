package com.wms.batch.controller;

import com.wms.batch.service.DailyCloseService;
import com.wms.generated.api.BatchApi;
import com.wms.generated.model.BatchExecutionDetail;
import com.wms.generated.model.BatchExecutionPageResponse;
import com.wms.generated.model.BatchExecutionStatus;
import com.wms.generated.model.BatchStepStatus;
import com.wms.generated.model.DailyCloseRequest;
import com.wms.generated.model.DailyCloseResponse;
import com.wms.generated.model.DailyCloseStepResult;
import com.wms.report.entity.BatchExecutionLog;
import com.wms.report.repository.BatchExecutionLogRepository;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BatchController implements BatchApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "startedAt", "targetBusinessDate", "status");

    private final DailyCloseService dailyCloseService;
    private final BatchExecutionLogRepository logRepository;

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<DailyCloseResponse> executeDailyClose(
            DailyCloseRequest dailyCloseRequest) {
        DailyCloseService.DailyCloseResult result =
                dailyCloseService.execute(dailyCloseRequest.getTargetBusinessDate());
        return ResponseEntity.ok(toResponse(result));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<BatchExecutionPageResponse> listBatchExecutions(
            LocalDate executedDateFrom, LocalDate executedDateTo,
            LocalDate targetBusinessDate, BatchExecutionStatus status,
            Integer page, Integer size, String sort) {

        String statusStr = status != null ? status.getValue() : null;
        Sort sortObj = parseSort(sort);
        Page<BatchExecutionLog> resultPage = logRepository.search(
                executedDateFrom, executedDateTo, targetBusinessDate, statusStr,
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<BatchExecutionDetail> getBatchExecution(Long id) {
        BatchExecutionLog logEntry = logRepository.findById(id)
                .orElseThrow(() -> {
                    // SEC-R2-Min-1 (OWASP A09): 追跡用 id はログへ、クライアント向けメッセージから除去
                    log.info("BatchExecutionLog not found: id={}", id);
                    return new ResourceNotFoundException(
                            "BATCH_EXECUTION_NOT_FOUND",
                            "指定されたバッチ実行履歴が見つかりません");
                });
        return ResponseEntity.ok(toDetail(logEntry));
    }

    private DailyCloseResponse toResponse(DailyCloseService.DailyCloseResult result) {
        List<DailyCloseStepResult> steps = result.steps().stream()
                .map(s -> new DailyCloseStepResult()
                        .step(s.step())
                        .name(s.name())
                        .status(BatchStepStatus.fromValue(s.status()))
                        .errorMessage(s.errorMessage()))
                .toList();

        return new DailyCloseResponse()
                .executionId(result.executionId())
                .status(BatchExecutionStatus.fromValue(result.status()))
                .targetBusinessDate(result.targetBusinessDate())
                .startedAt(result.startedAt())
                .completedAt(result.completedAt())
                .steps(steps)
                .unreceivedCount(result.unreceivedCount())
                .unshippedCount(result.unshippedCount());
    }

    private BatchExecutionDetail toDetail(BatchExecutionLog log) {
        String executedByName = dailyCloseService.resolveUserName(log.getExecutedBy());

        return new BatchExecutionDetail()
                .id(log.getId())
                .targetBusinessDate(log.getTargetBusinessDate())
                .status(BatchExecutionStatus.fromValue(log.getStatus()))
                .step1Status(toBatchStepStatus(log.getStep1Status()))
                .step2Status(toBatchStepStatus(log.getStep2Status()))
                .step3Status(toBatchStepStatus(log.getStep3Status()))
                .step4Status(toBatchStepStatus(log.getStep4Status()))
                .step5Status(toBatchStepStatus(log.getStep5Status()))
                .step6Status(toBatchStepStatus(log.getStep6Status()))
                .errorMessage(log.getErrorMessage())
                .startedAt(log.getStartedAt())
                .completedAt(log.getCompletedAt())
                .executedByName(executedByName);
    }

    private BatchExecutionPageResponse toPageResponse(Page<BatchExecutionLog> page) {
        List<BatchExecutionDetail> items = page.getContent().stream()
                .map(this::toDetail)
                .toList();
        return new BatchExecutionPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private BatchStepStatus toBatchStepStatus(String status) {
        if (status == null) {
            return null;
        }
        return BatchStepStatus.fromValue(status);
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "startedAt";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
