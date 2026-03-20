package com.wms.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageResponseTest {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("from(): Spring Data Pageからコンテンツとページング情報が正しく変換される")
    void from_convertsPageCorrectly() {
        Page<Integer> springPage = mock(Page.class);
        when(springPage.getContent()).thenReturn(List.of(1, 2, 3));
        when(springPage.getNumber()).thenReturn(0);
        when(springPage.getSize()).thenReturn(10);
        when(springPage.getTotalElements()).thenReturn(25L);
        when(springPage.getTotalPages()).thenReturn(3);

        PageResponse<String> result = PageResponse.from(springPage, Object::toString);

        assertThat(result.content()).containsExactly("1", "2", "3");
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("from(): 空ページの場合、空のcontentが返される")
    void from_emptyPage_returnsEmptyContent() {
        Page<String> springPage = mock(Page.class);
        when(springPage.getContent()).thenReturn(List.of());
        when(springPage.getNumber()).thenReturn(0);
        when(springPage.getSize()).thenReturn(20);
        when(springPage.getTotalElements()).thenReturn(0L);
        when(springPage.getTotalPages()).thenReturn(0);

        PageResponse<String> result = PageResponse.from(springPage, s -> s.toUpperCase());

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("from(): converter関数が各要素に適用される")
    void from_appliesConverterToEachElement() {
        Page<String> springPage = mock(Page.class);
        when(springPage.getContent()).thenReturn(List.of("hello", "world"));
        when(springPage.getNumber()).thenReturn(1);
        when(springPage.getSize()).thenReturn(5);
        when(springPage.getTotalElements()).thenReturn(12L);
        when(springPage.getTotalPages()).thenReturn(3);

        PageResponse<Integer> result = PageResponse.from(springPage, String::length);

        assertThat(result.content()).containsExactly(5, 5);
        assertThat(result.page()).isEqualTo(1);
    }
}
