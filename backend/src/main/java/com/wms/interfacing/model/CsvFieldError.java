package com.wms.interfacing.model;

/**
 * CSVバリデーションのフィールド単位エラー。
 */
public record CsvFieldError(String column, String errorCode, String message) {
}
