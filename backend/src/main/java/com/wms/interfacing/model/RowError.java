package com.wms.interfacing.model;

import java.util.List;

/**
 * CSVバリデーションの行単位エラー。
 */
public record RowError(int rowNumber, List<FieldError> errors) {
}
