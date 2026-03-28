package com.wms.shared.config;

import com.wms.generated.model.ReportFormat;
import com.wms.generated.model.ReturnReason;
import com.wms.generated.model.ReturnType;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.UnitType;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * OpenAPI Generator が生成する列挙型の {@code @JsonCreator fromValue()} を
 * Spring MVC のクエリパラメータバインディングでも使えるようにするコンバーター設定。
 *
 * <p>デフォルトの {@code StringToEnumConverterFactory} は {@code Enum.valueOf()} を使うため、
 * 小文字の値（例: "json"）を大文字の列挙定数（例: JSON）に変換できない。
 */
@Configuration
public class OpenApiEnumConverterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ReportFormat.class, ReportFormat::fromValue);
        registry.addConverter(String.class, UnitType.class, UnitType::fromValue);
        registry.addConverter(String.class, StorageCondition.class, StorageCondition::fromValue);
        registry.addConverter(String.class, ReturnType.class, ReturnType::fromValue);
        registry.addConverter(String.class, ReturnReason.class, ReturnReason::fromValue);
    }
}
