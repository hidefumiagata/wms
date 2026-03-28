package com.wms.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
            MethodHandle mh;
            try {
                Method fromValue = targetType.getMethod("fromValue", String.class);
                mh = MethodHandles.lookup().unreflect(fromValue);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return source -> (T) Enum.valueOf(targetType, source);
            }

            return source -> {
                try {
                    return (T) mh.invoke(source);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new IllegalArgumentException(e);
                }
            };
        }
    }
}
