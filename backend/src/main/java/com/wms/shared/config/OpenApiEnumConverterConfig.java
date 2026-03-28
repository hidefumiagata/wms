package com.wms.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * OpenAPI Generator が生成する列挙型の {@code @JsonCreator fromValue()} を
 * Spring MVC のクエリパラメータバインディングでも使えるようにするコンバーター設定。
 *
 * <p>デフォルトの {@code StringToEnumConverterFactory} は {@code Enum.valueOf()} を使うため、
 * 小文字の値（例: "json"）を大文字の列挙定数（例: JSON）に変換できない。
 * 本設定は {@code fromValue(String)} メソッドを持つ全列挙型に対して自動的にコンバーターを提供する。
 */
@Configuration
public class OpenApiEnumConverterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new JsonCreatorEnumConverterFactory());
    }

    /**
     * {@code fromValue(String)} static メソッドを持つ列挙型を自動検出し、
     * そのメソッドを使って文字列→列挙型変換を行うコンバーターファクトリー。
     * {@code fromValue} が存在しない列挙型は {@code Enum.valueOf()} にフォールバックする。
     */
    @SuppressWarnings("rawtypes")
    static class JsonCreatorEnumConverterFactory implements ConverterFactory<String, Enum> {

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
            Method fromValue;
            try {
                fromValue = targetType.getMethod("fromValue", String.class);
            } catch (NoSuchMethodException e) {
                return source -> (T) Enum.valueOf(targetType, source);
            }

            Method method = fromValue;
            return source -> {
                try {
                    return (T) method.invoke(null, source);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new IllegalArgumentException(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException(e);
                }
            };
        }
    }
}
