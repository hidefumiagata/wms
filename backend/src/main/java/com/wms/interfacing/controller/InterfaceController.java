package com.wms.interfacing.controller;

import com.wms.generated.api.InterfaceApi;
import com.wms.generated.model.IfExecutionItem;
import com.wms.generated.model.IfExecutionPageResponse;
import com.wms.generated.model.IfExecutionStatus;
import com.wms.generated.model.ImportMode;
import com.wms.generated.model.InterfaceFileError;
import com.wms.generated.model.InterfaceFileInfo;
import com.wms.generated.model.InterfaceFileListResponse;
import com.wms.generated.model.InterfaceImportRequest;
import com.wms.generated.model.InterfaceImportResult;
import com.wms.generated.model.InterfaceType;
import com.wms.generated.model.InterfaceValidateRequest;
import com.wms.generated.model.InterfaceValidationError;
import com.wms.generated.model.InterfaceValidationResult;
import com.wms.generated.model.InterfaceValidationRow;
import com.wms.interfacing.blob.BlobStorageClient;
import com.wms.interfacing.entity.IfExecution;
import com.wms.interfacing.service.InboundPlanCsvProcessor;
import com.wms.interfacing.service.InterfaceService;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class InterfaceController implements InterfaceApi {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("executedAt", "fileName", "status", "ifType");

    private static final Map<String, String> IF_TYPE_TO_DB = Map.of(
            "IFX-001", "INBOUND_PLAN",
            "IFX-002", "ORDER"
    );

    private final InterfaceService interfaceService;
    private final UserRepository userRepository;

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<IfExecutionPageResponse> listInterfaceExecutions(
            InterfaceType ifType, LocalDate dateFrom, LocalDate dateTo,
            IfExecutionStatus status, String fileName,
            Integer page, Integer size, String sort) {

        String dbIfType = ifType != null ? IF_TYPE_TO_DB.get(ifType.getValue()) : null;
        String dbStatus = status != null ? status.getValue() : null;

        OffsetDateTime dateFromOdt = dateFrom != null
                ? dateFrom.atStartOfDay(JST).toOffsetDateTime() : null;
        OffsetDateTime dateToOdt = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay(JST).toOffsetDateTime() : null;

        Sort sortObj = parseSort(sort);
        Page<IfExecution> resultPage = interfaceService.listExecutions(
                dbIfType, dateFromOdt, dateToOdt, dbStatus, fileName,
                PageRequest.of(page, size, sortObj));

        return ResponseEntity.ok(toExecutionPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<InterfaceFileListResponse> listInterfaceFiles(InterfaceType ifId) {
        List<BlobStorageClient.BlobFileInfo> files =
                interfaceService.listFiles(ifId.getValue());

        InterfaceFileListResponse response = new InterfaceFileListResponse()
                .files(files.stream().map(this::toFileInfo).toList())
                .totalCount(files.size());

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<InterfaceValidationResult> validateInterfaceFile(
            InterfaceType ifId,
            InterfaceValidateRequest interfaceValidateRequest) {

        InterfaceService.InterfaceValidationResponse result = interfaceService.validate(
                ifId.getValue(),
                interfaceValidateRequest.getFileName(),
                interfaceValidateRequest.getWarehouseId());

        return ResponseEntity.ok(toValidationResult(result));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @Override
    public ResponseEntity<InterfaceImportResult> importInterfaceFile(
            InterfaceType ifId,
            InterfaceImportRequest interfaceImportRequest) {

        InterfaceService.InterfaceImportResponse result = interfaceService.importFile(
                ifId.getValue(),
                interfaceImportRequest.getFileName(),
                interfaceImportRequest.getWarehouseId(),
                interfaceImportRequest.getMode().getValue());

        InterfaceImportResult response = new InterfaceImportResult()
                .successCount(result.successCount())
                .errorCount(result.errorCount())
                .mode(ImportMode.fromValue(result.mode()))
                .status(InterfaceImportResult.StatusEnum.fromValue(result.status()));

        return ResponseEntity.ok(response);
    }

    // --- Converters ---

    private InterfaceFileInfo toFileInfo(BlobStorageClient.BlobFileInfo info) {
        return new InterfaceFileInfo()
                .fileName(info.fileName())
                .fileSize(info.fileSize())
                .createdAt(info.createdAt());
    }

    private InterfaceValidationResult toValidationResult(
            InterfaceService.InterfaceValidationResponse resp) {
        InterfaceValidationResult result = new InterfaceValidationResult()
                .fileName(resp.fileName())
                .totalRows(resp.totalRows())
                .successCount(resp.successCount())
                .errorCount(resp.errorCount());

        if (resp.hasFileError()) {
            result.fileError(new InterfaceFileError()
                    .errorCode(resp.fileErrorCode())
                    .message(resp.fileErrorMessage()));
            result.rows(List.of());
        } else {
            result.rows(resp.rowErrors().stream()
                    .map(this::toValidationRow)
                    .toList());
        }

        return result;
    }

    private InterfaceValidationRow toValidationRow(InboundPlanCsvProcessor.RowError rowError) {
        return new InterfaceValidationRow()
                .rowNumber(rowError.rowNumber())
                .errors(rowError.errors().stream()
                        .map(e -> new InterfaceValidationError()
                                .column(e.column())
                                .errorCode(e.errorCode())
                                .message(e.message()))
                        .toList());
    }

    private IfExecutionPageResponse toExecutionPageResponse(Page<IfExecution> page) {
        // Batch fetch user names (N+1回避)
        Set<Long> userIds = page.getContent().stream()
                .map(IfExecution::getExecutedBy)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return new IfExecutionPageResponse()
                .content(page.getContent().stream()
                        .map(e -> toExecutionItem(e, userMap))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private IfExecutionItem toExecutionItem(IfExecution e, Map<Long, User> userMap) {
        User user = userMap.get(e.getExecutedBy());
        String userName = user != null ? user.getFullName() : String.valueOf(e.getExecutedBy());

        return new IfExecutionItem()
                .id(e.getId())
                .ifType(e.getIfType())
                .fileName(e.getFileName())
                .totalCount(e.getTotalCount())
                .successCount(e.getSuccessCount())
                .errorCount(e.getErrorCount())
                .mode(ImportMode.fromValue(e.getMode()))
                .status(IfExecutionStatus.fromValue(e.getStatus()))
                .blobMoveFailed(e.getBlobMoveFailed())
                .warehouseId(e.getWarehouseId())
                .executedAt(e.getExecutedAt())
                .executedBy(e.getExecutedBy())
                .executedByName(userName);
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "executedAt";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
