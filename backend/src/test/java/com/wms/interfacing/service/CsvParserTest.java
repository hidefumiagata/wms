package com.wms.interfacing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CsvParser")
class CsvParserTest {

    private final CsvParser csvParser = new CsvParser();

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("正常系 — ヘッダ+データ行のCSVをパースできる")
        void parse_validCsv_returnsHeaderAndRows() {
            String csv = "col1,col2,col3\nval1,val2,val3\nval4,val5,val6\n";
            CsvParser.CsvParseResult result = csvParser.parse(toInputStream(csv));

            assertThat(result.getHeader()).containsExactly("col1", "col2", "col3");
            assertThat(result.getDataRows()).hasSize(2);
            assertThat(result.getDataRows().get(0)).containsExactly("val1", "val2", "val3");
            assertThat(result.getDataRows().get(1)).containsExactly("val4", "val5", "val6");
        }

        @Test
        @DisplayName("正常系 — 空行をスキップする")
        void parse_blankLines_skipped() {
            String csv = "col1,col2\n\nval1,val2\n\n";
            CsvParser.CsvParseResult result = csvParser.parse(toInputStream(csv));

            assertThat(result.getDataRows()).hasSize(1);
        }

        @Test
        @DisplayName("正常系 — ダブルクォート囲みの値を正しくパースする")
        void parse_quotedValues_parsedCorrectly() {
            String csv = "col1,col2\n\"val,1\",\"val\"\"2\"\n";
            CsvParser.CsvParseResult result = csvParser.parse(toInputStream(csv));

            assertThat(result.getDataRows().get(0)).containsExactly("val,1", "val\"2");
        }

        @Test
        @DisplayName("異常系 — IOExceptionでWMS-E-IFX-001")
        void parse_ioError_throwsException() {
            InputStream failingStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Simulated IO error");
                }
            };
            assertThatThrownBy(() -> csvParser.parse(failingStream))
                    .isInstanceOf(CsvParser.CsvParseException.class)
                    .satisfies(e -> {
                        CsvParser.CsvParseException cpe = (CsvParser.CsvParseException) e;
                        assertThat(cpe.getErrorCode()).isEqualTo("WMS-E-IFX-001");
                    });
        }

        @Test
        @DisplayName("異常系 — ヘッダ行なしでCsvParseException")
        void parse_emptyFile_throwsException() {
            String csv = "";
            assertThatThrownBy(() -> csvParser.parse(toInputStream(csv)))
                    .isInstanceOf(CsvParser.CsvParseException.class)
                    .satisfies(e -> {
                        CsvParser.CsvParseException cpe = (CsvParser.CsvParseException) e;
                        assertThat(cpe.getErrorCode()).isEqualTo("WMS-E-IFX-002");
                    });
        }

        @Test
        @DisplayName("異常系 — データ行0件でCsvParseException")
        void parse_headerOnly_throwsException() {
            String csv = "col1,col2\n";
            assertThatThrownBy(() -> csvParser.parse(toInputStream(csv)))
                    .isInstanceOf(CsvParser.CsvParseException.class)
                    .satisfies(e -> {
                        CsvParser.CsvParseException cpe = (CsvParser.CsvParseException) e;
                        assertThat(cpe.getErrorCode()).isEqualTo("WMS-E-IFX-005");
                    });
        }

        @Test
        @DisplayName("異常系 — 10,001行超過でCsvParseException")
        void parse_exceeds10000Rows_throwsException() {
            StringBuilder csv = new StringBuilder("col1\n");
            for (int i = 0; i < 10_001; i++) {
                csv.append("val").append(i).append("\n");
            }
            assertThatThrownBy(() -> csvParser.parse(toInputStream(csv.toString())))
                    .isInstanceOf(CsvParser.CsvParseException.class)
                    .satisfies(e -> {
                        CsvParser.CsvParseException cpe = (CsvParser.CsvParseException) e;
                        assertThat(cpe.getErrorCode()).isEqualTo("WMS-E-IFX-006");
                    });
        }

        @Test
        @DisplayName("境界値 — ちょうど10,000行はパースできる")
        void parse_exactly10000Rows_succeeds() {
            StringBuilder csv = new StringBuilder("col1\n");
            for (int i = 0; i < 10_000; i++) {
                csv.append("val").append(i).append("\n");
            }
            CsvParser.CsvParseResult result = csvParser.parse(toInputStream(csv.toString()));
            assertThat(result.getDataRows()).hasSize(10_000);
        }
    }

    @Nested
    @DisplayName("parseLine")
    class ParseLine {

        @Test
        @DisplayName("カンマ区切りの行をフィールド配列にパースする")
        void parseLine_simpleValues() {
            String[] fields = csvParser.parseLine("a,b,c");
            assertThat(fields).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("空フィールドを正しく処理する")
        void parseLine_emptyFields() {
            String[] fields = csvParser.parseLine("a,,c,");
            assertThat(fields).containsExactly("a", "", "c", "");
        }

        @Test
        @DisplayName("ダブルクォートでエスケープされたカンマを正しく処理する")
        void parseLine_quotedComma() {
            String[] fields = csvParser.parseLine("\"a,b\",c");
            assertThat(fields).containsExactly("a,b", "c");
        }

        @Test
        @DisplayName("ダブルクォートのエスケープを正しく処理する")
        void parseLine_escapedQuotes() {
            String[] fields = csvParser.parseLine("\"a\"\"b\",c");
            assertThat(fields).containsExactly("a\"b", "c");
        }
    }

    @Nested
    @DisplayName("normalizeEmpty")
    class NormalizeEmpty {

        @Test
        @DisplayName("nullはnullを返す")
        void normalizeEmpty_null_returnsNull() {
            assertThat(CsvParser.normalizeEmpty(null)).isNull();
        }

        @Test
        @DisplayName("空文字列はnullを返す")
        void normalizeEmpty_empty_returnsNull() {
            assertThat(CsvParser.normalizeEmpty("")).isNull();
        }

        @Test
        @DisplayName("空白のみはnullを返す")
        void normalizeEmpty_whitespace_returnsNull() {
            assertThat(CsvParser.normalizeEmpty("  ")).isNull();
        }

        @Test
        @DisplayName("値がある場合はtrimして返す")
        void normalizeEmpty_value_returnsTrimmed() {
            assertThat(CsvParser.normalizeEmpty(" abc ")).isEqualTo("abc");
        }
    }
}
