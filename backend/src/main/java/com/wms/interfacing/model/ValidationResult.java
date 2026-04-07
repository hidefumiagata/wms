package com.wms.interfacing.model;

import lombok.Getter;

import java.util.List;

/**
 * CSVバリデーション結果。
 */
@Getter
public class ValidationResult {
    private final int totalRows;
    private final int successCount;
    private final int errorCount;
    private final List<RowError> rowErrors;

    public ValidationResult(int totalRows, int successCount, int errorCount,
                            List<RowError> rowErrors) {
        this.totalRows = totalRows;
        this.successCount = successCount;
        this.errorCount = errorCount;
        this.rowErrors = rowErrors;
    }
}
