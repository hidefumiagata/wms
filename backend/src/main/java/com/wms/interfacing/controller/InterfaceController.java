package com.wms.interfacing.controller;

import com.wms.generated.api.InterfaceApi;
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
import com.wms.interfacing.service.InboundPlanCsvProcessor;
import com.wms.interfacing.service.InterfaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class InterfaceController implements InterfaceApi {

    private final InterfaceService interfaceService;

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
}
