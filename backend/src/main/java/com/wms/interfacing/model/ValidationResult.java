package com.wms.interfacing.model;

import java.util.List;

/**
 * CSVバリデーション結果。
 */
public record ValidationResult(int totalRows, int successCount, int errorCount, List<RowError> rowErrors) {
}
