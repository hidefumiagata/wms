package com.wms.report.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CsvGenerationServiceTest {

    private final CsvGenerationService service = new CsvGenerationService();

    private ReportMeta createMeta(String[] headers, Function<Object, String[]> rowMapper) {
        return new ReportMeta(
                "テストレポート", "test-template", "test_20260327",
                "テスト倉庫", "admin", "条件なし",
                headers, rowMapper
        );
    }

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("UTF-8 BOM が先頭に付与される")
        void shouldPrependUtf8Bom() {
            ReportMeta meta = createMeta(
                    new String[]{"名前"},
                    obj -> new String[]{obj.toString()}
            );
            byte[] result = service.generate(List.of("テスト"), meta);

            assertThat(result[0]).isEqualTo((byte) 0xEF);
            assertThat(result[1]).isEqualTo((byte) 0xBB);
            assertThat(result[2]).isEqualTo((byte) 0xBF);
        }

        @Test
        @DisplayName("ヘッダー行とデータ行が正しく生成される")
        void shouldGenerateHeaderAndDataRows() {
            ReportMeta meta = createMeta(
                    new String[]{"ID", "名前", "数量"},
                    obj -> {
                        String[] parts = ((String) obj).split(":");
                        return new String[]{parts[0], parts[1], parts[2]};
                    }
            );
            byte[] result = service.generate(List.of("1:商品A:100", "2:商品B:200"), meta);
            String csv = new String(result, 3, result.length - 3, StandardCharsets.UTF_8);

            String[] lines = csv.split("\r\n");
            assertThat(lines).hasSize(3);
            assertThat(lines[0]).isEqualTo("ID,名前,数量");
            assertThat(lines[1]).isEqualTo("1,商品A,100");
            assertThat(lines[2]).isEqualTo("2,商品B,200");
        }

        @Test
        @DisplayName("空リストの場合はヘッダー行のみ生成される")
        void shouldGenerateOnlyHeaderForEmptyList() {
            ReportMeta meta = createMeta(
                    new String[]{"ID", "名前"},
                    obj -> new String[]{}
            );
            byte[] result = service.generate(List.of(), meta);
            String csv = new String(result, 3, result.length - 3, StandardCharsets.UTF_8);

            assertThat(csv.trim()).isEqualTo("ID,名前");
        }

        @Test
        @DisplayName("null 値は em ダッシュに変換される")
        void shouldReplaceNullWithEmDash() {
            ReportMeta meta = createMeta(
                    new String[]{"値"},
                    obj -> new String[]{null}
            );
            byte[] result = service.generate(List.of("dummy"), meta);
            String csv = new String(result, 3, result.length - 3, StandardCharsets.UTF_8);

            String[] lines = csv.split("\r\n");
            assertThat(lines[1]).isEqualTo("\u2014");
        }
    }

    @Nested
    @DisplayName("escapeCsvField")
    class EscapeCsvField {

        @Test
        @DisplayName("カンマを含む値はダブルクォートで囲まれる")
        void shouldEscapeComma() {
            assertThat(service.escapeCsvField("A,B")).isEqualTo("\"A,B\"");
        }

        @Test
        @DisplayName("ダブルクォートを含む値はエスケープされる")
        void shouldEscapeDoubleQuote() {
            assertThat(service.escapeCsvField("He said \"hello\"")).isEqualTo("\"He said \"\"hello\"\"\"");
        }

        @Test
        @DisplayName("改行を含む値はダブルクォートで囲まれる")
        void shouldEscapeNewline() {
            assertThat(service.escapeCsvField("line1\nline2")).isEqualTo("\"line1\nline2\"");
        }

        @Test
        @DisplayName("CR を含む値はダブルクォートで囲まれる")
        void shouldEscapeCarriageReturn() {
            assertThat(service.escapeCsvField("line1\rline2")).isEqualTo("\"line1\rline2\"");
        }

        @Test
        @DisplayName("特殊文字を含まない値はそのまま返される")
        void shouldNotEscapePlainValue() {
            assertThat(service.escapeCsvField("plain text")).isEqualTo("plain text");
        }
    }

    @Nested
    @DisplayName("sanitizeFormulaInjection")
    class SanitizeFormulaInjection {

        @Test
        @DisplayName("= で始まる値にシングルクォートが前置される")
        void shouldSanitizeEquals() {
            assertThat(service.sanitizeFormulaInjection("=SUM(A1:A10)")).isEqualTo("'=SUM(A1:A10)");
        }

        @Test
        @DisplayName("+ で始まる値にシングルクォートが前置される")
        void shouldSanitizePlus() {
            assertThat(service.sanitizeFormulaInjection("+cmd|'/C calc'!A0")).isEqualTo("'+cmd|'/C calc'!A0");
        }

        @Test
        @DisplayName("- で始まる値にシングルクォートが前置される")
        void shouldSanitizeMinus() {
            assertThat(service.sanitizeFormulaInjection("-1+1")).isEqualTo("'-1+1");
        }

        @Test
        @DisplayName("@ で始まる値にシングルクォートが前置される")
        void shouldSanitizeAt() {
            assertThat(service.sanitizeFormulaInjection("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        }

        @Test
        @DisplayName("通常の値はそのまま返される")
        void shouldNotSanitizePlainValue() {
            assertThat(service.sanitizeFormulaInjection("normal value")).isEqualTo("normal value");
        }

        @Test
        @DisplayName("空文字はそのまま返される")
        void shouldNotSanitizeEmpty() {
            assertThat(service.sanitizeFormulaInjection("")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("escapeCsvRow")
    class EscapeCsvRow {

        @Test
        @DisplayName("null 値を含む行が em ダッシュに変換される")
        void shouldReplaceNullInRow() {
            String result = service.escapeCsvRow(new String[]{"A", null, "C"});
            assertThat(result).isEqualTo("A,\u2014,C");
        }
    }

    @Nested
    @DisplayName("静的フォーマットヘルパー")
    class FormatHelpers {

        @Test
        @DisplayName("fmtDate: 日付を yyyy-MM-dd 形式にフォーマットする")
        void fmtDate_formatsCorrectly() {
            assertThat(CsvGenerationService.fmtDate(LocalDate.of(2026, 3, 27)))
                    .isEqualTo("2026-03-27");
        }

        @Test
        @DisplayName("fmtDate: null は em ダッシュを返す")
        void fmtDate_nullReturnsDash() {
            assertThat(CsvGenerationService.fmtDate(null)).isEqualTo("\u2014");
        }

        @Test
        @DisplayName("fmtDateTime: 日時を yyyy-MM-dd HH:mm 形式にフォーマットする")
        void fmtDateTime_formatsCorrectly() {
            assertThat(CsvGenerationService.fmtDateTime(LocalDateTime.of(2026, 3, 27, 14, 30)))
                    .isEqualTo("2026-03-27 14:30");
        }

        @Test
        @DisplayName("fmtDateTime: null は em ダッシュを返す")
        void fmtDateTime_nullReturnsDash() {
            assertThat(CsvGenerationService.fmtDateTime(null)).isEqualTo("\u2014");
        }

        @Test
        @DisplayName("fmtInteger: 3桁カンマ区切りでフォーマットする")
        void fmtInteger_formatsWithComma() {
            assertThat(CsvGenerationService.fmtInteger(1234567)).isEqualTo("1,234,567");
        }

        @Test
        @DisplayName("fmtInteger: null は em ダッシュを返す")
        void fmtInteger_nullReturnsDash() {
            assertThat(CsvGenerationService.fmtInteger(null)).isEqualTo("\u2014");
        }

        @Test
        @DisplayName("fmtLong: 3桁カンマ区切りでフォーマットする")
        void fmtLong_formatsWithComma() {
            assertThat(CsvGenerationService.fmtLong(9876543210L)).isEqualTo("9,876,543,210");
        }

        @Test
        @DisplayName("fmtLong: null は em ダッシュを返す")
        void fmtLong_nullReturnsDash() {
            assertThat(CsvGenerationService.fmtLong(null)).isEqualTo("\u2014");
        }

        @Test
        @DisplayName("fmtPercent: 小数第1位まで % 付きでフォーマットする")
        void fmtPercent_formatsCorrectly() {
            assertThat(CsvGenerationService.fmtPercent(98.5)).isEqualTo("98.5%");
        }

        @Test
        @DisplayName("fmtPercent: null は em ダッシュを返す")
        void fmtPercent_nullReturnsDash() {
            assertThat(CsvGenerationService.fmtPercent(null)).isEqualTo("\u2014");
        }

        @Test
        @DisplayName("fmtOrDash: 値がある場合は文字列に変換する")
        void fmtOrDash_nonNullReturnsString() {
            assertThat(CsvGenerationService.fmtOrDash(42)).isEqualTo("42");
        }

        @Test
        @DisplayName("fmtOrDash: null は em ダッシュを返す")
        void fmtOrDash_nullReturnsDash() {
            assertThat(CsvGenerationService.fmtOrDash(null)).isEqualTo("\u2014");
        }
    }
}
