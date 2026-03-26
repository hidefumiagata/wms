package com.wms.report.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * UTF-8 BOM 付き CSV 生成サービス。
 * RFC 4180 準拠のエスケープ処理を行い、null 値は em ダッシュに変換する。
 */
@Service
public class CsvGenerationService {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String EM_DASH = "\u2014";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * データリストを CSV バイト配列に変換する。
     */
    public <T> byte[] generate(List<T> data, ReportMeta meta) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(UTF8_BOM);
            OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);

            // ヘッダー行
            writer.write(escapeCsvRow(meta.csvHeaders()));
            writer.write("\r\n");

            // データ行
            for (T item : data) {
                String[] values = meta.csvRowMapper().apply(item);
                writer.write(escapeCsvRow(values));
                writer.write("\r\n");
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    String escapeCsvRow(String[] values) {
        return Arrays.stream(values)
                .map(v -> v == null ? EM_DASH : v)
                .map(this::escapeCsvField)
                .collect(Collectors.joining(","));
    }

    String escapeCsvField(String value) {
        // CSV injection 対策: スプレッドシートで数式として解釈される先頭文字を無害化
        String sanitized = sanitizeFormulaInjection(value);
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    /**
     * CSV injection (Formula injection) 対策。
     * 先頭が =, +, -, @, \t, \r の場合、シングルクォートを前置して数式実行を防止する。
     */
    String sanitizeFormulaInjection(String value) {
        if (value.isEmpty()) return value;
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    // --- 静的フォーマットヘルパー（個別レポートの csvRowMapper で使用） ---

    public static String fmtDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : EM_DASH;
    }

    public static String fmtDateTime(LocalDateTime dt) {
        return dt != null ? dt.format(DATETIME_FMT) : EM_DASH;
    }

    public static String fmtInteger(Integer value) {
        if (value == null) return EM_DASH;
        return NumberFormat.getIntegerInstance(Locale.JAPAN).format(value);
    }

    public static String fmtLong(Long value) {
        if (value == null) return EM_DASH;
        return NumberFormat.getIntegerInstance(Locale.JAPAN).format(value);
    }

    public static String fmtPercent(Double value) {
        if (value == null) return EM_DASH;
        return String.format("%.1f%%", value);
    }

    public static String fmtOrDash(Object value) {
        return value != null ? String.valueOf(value) : EM_DASH;
    }
}
