package com.wms.interfacing.model;

/**
 * CSVバリデーションのフィールド単位エラー。
 */
public record FieldError(String column, String errorCode, String message) {
}
