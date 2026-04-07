package com.wms.interfacing.model;

import java.util.List;

/**
 * CSVバリデーション結果。
 */
public record CsvValidationResult(int totalRows, int successCount, int errorCount, List<CsvRowError> rowErrors) {
}
