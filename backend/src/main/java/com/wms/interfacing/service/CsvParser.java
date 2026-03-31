package com.wms.interfacing.service;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSVファイルの共通パース処理。
 * RFC 4180に準拠したCSVパーサー（ダブルクォート囲み、エスケープ対応）。
 */
@Component
public class CsvParser {

    private static final int MAX_DATA_ROWS = 10_000;

    /**
     * CSVファイルをパースし、ヘッダと全データ行を返す。
     * 空行はスキップする。
     *
     * @param inputStream CSVファイルのInputStream
     * @return パース結果
     * @throws CsvParseException L1ファイルレベルエラー
     */
    public CsvParseResult parse(InputStream inputStream) {
        List<String> lines = readLines(inputStream);

        if (lines.isEmpty()) {
            throw new CsvParseException("WMS-E-IFX-002", "ヘッダ行が存在しません");
        }

        String[] header = parseLine(lines.get(0));

        List<String[]> dataRows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            dataRows.add(parseLine(lines.get(i)));
        }

        if (dataRows.isEmpty()) {
            throw new CsvParseException("WMS-E-IFX-005", "データ行が存在しません");
        }

        if (dataRows.size() > MAX_DATA_ROWS) {
            throw new CsvParseException("WMS-E-IFX-006",
                    "データ行数が上限（10,000件）を超えています（" + dataRows.size() + "件）");
        }

        return new CsvParseResult(header, dataRows);
    }

    private List<String> readLines(InputStream inputStream) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            throw new CsvParseException("WMS-E-IFX-001",
                    "ファイルの読み取りに失敗しました: " + e.getMessage());
        }
        return lines;
    }

    /**
     * CSV1行をフィールド配列にパースする。ダブルクォート囲みとエスケープに対応。
     */
    String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }

    /**
     * 空文字列をnullに変換する。
     */
    public static String normalizeEmpty(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Getter
    public static class CsvParseResult {
        private final String[] header;
        private final List<String[]> dataRows;

        public CsvParseResult(String[] header, List<String[]> dataRows) {
            this.header = header;
            this.dataRows = dataRows;
        }
    }

    public static class CsvParseException extends RuntimeException {
        @Getter
        private final String errorCode;

        public CsvParseException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
