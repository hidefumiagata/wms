package com.wms.shared.config;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OpenApiEnumConverterConfig.JsonCreatorEnumConverterFactory")
class OpenApiEnumConverterConfigTest {

    private OpenApiEnumConverterConfig.JsonCreatorEnumConverterFactory factory;

    @BeforeEach
    void setUp() {
        factory = new OpenApiEnumConverterConfig.JsonCreatorEnumConverterFactory();
    }

    @Nested
    @DisplayName("fromValue() を持つ列挙型")
    class WithFromValue {

        @Test
        @DisplayName("小文字の値をReportFormatに変換できる")
        void convert_lowercaseJson_returnsJsonEnum() {
            Converter<String, ReportFormat> converter = factory.getConverter(ReportFormat.class);
            assertThat(converter.convert("json")).isEqualTo(ReportFormat.JSON);
        }

        @Test
        @DisplayName("csv値をReportFormatに変換できる")
        void convert_csv_returnsCsvEnum() {
            Converter<String, ReportFormat> converter = factory.getConverter(ReportFormat.class);
            assertThat(converter.convert("csv")).isEqualTo(ReportFormat.CSV);
        }

        @Test
        @DisplayName("不正な値でIllegalArgumentExceptionをスローする")
        void convert_invalidValue_throwsException() {
            Converter<String, ReportFormat> converter = factory.getConverter(ReportFormat.class);
            assertThatThrownBy(() -> converter.convert("xml"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("UnitTypeの変換が正しく動作する")
        void convert_unitType_works() {
            Converter<String, UnitType> converter = factory.getConverter(UnitType.class);
            assertThat(converter.convert("CASE")).isEqualTo(UnitType.CASE);
            assertThat(converter.convert("PIECE")).isEqualTo(UnitType.PIECE);
        }

        @Test
        @DisplayName("StorageConditionの変換が正しく動作する")
        void convert_storageCondition_works() {
            Converter<String, StorageCondition> converter = factory.getConverter(StorageCondition.class);
            assertThat(converter.convert("AMBIENT")).isEqualTo(StorageCondition.AMBIENT);
        }
    }

    @Nested
    @DisplayName("fromValue() を持たない列挙型")
    class WithoutFromValue {

        enum PlainEnum { FOO, BAR }

        @Test
        @DisplayName("Enum.valueOf()にフォールバックする")
        void convert_plainEnum_usesValueOf() {
            Converter<String, PlainEnum> converter = factory.getConverter(PlainEnum.class);
            assertThat(converter.convert("FOO")).isEqualTo(PlainEnum.FOO);
        }

        @Test
        @DisplayName("不正な値でIllegalArgumentExceptionをスローする")
        void convert_plainEnum_invalidValue_throwsException() {
            Converter<String, PlainEnum> converter = factory.getConverter(PlainEnum.class);
            assertThatThrownBy(() -> converter.convert("foo"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
