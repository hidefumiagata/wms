package com.wms.interfacing.model;

import java.util.List;

/**
 * CSVバリデーションの行単位エラー。
 */
public record CsvRowError(int rowNumber, List<CsvFieldError> errors) {
}
