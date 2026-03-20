package com.wms.shared.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * ページング一覧レスポンスの共通ラッパー。
 * Spring Data の Page を本DTOに変換して返す。
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /**
     * Spring Data Page からファクトリ変換する。
     *
     * @param springPage Spring Data の Page オブジェクト
     * @param converter  Entity → ResponseDTO の変換関数
     */
    public static <E, T> PageResponse<T> from(Page<E> springPage, Function<E, T> converter) {
        List<T> content = springPage.getContent().stream()
                .map(converter)
                .toList();
        return new PageResponse<>(
                content,
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages()
        );
    }
}
