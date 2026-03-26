package com.wms.system.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemParameterTest {

    @Test
    @DisplayName("getIntValue: 文字列の数値をintに変換する")
    void getIntValue_returnsInteger() {
        SystemParameter param = SystemParameter.builder()
                .paramValue("42").build();

        assertThat(param.getIntValue()).isEqualTo(42);
    }

    @Test
    @DisplayName("getBooleanValue: 'true'をtrueに変換する")
    void getBooleanValue_trueString_returnsTrue() {
        SystemParameter param = SystemParameter.builder()
                .paramValue("true").build();

        assertThat(param.getBooleanValue()).isTrue();
    }

    @Test
    @DisplayName("getBooleanValue: 'false'をfalseに変換する")
    void getBooleanValue_falseString_returnsFalse() {
        SystemParameter param = SystemParameter.builder()
                .paramValue("false").build();

        assertThat(param.getBooleanValue()).isFalse();
    }

    @Test
    @DisplayName("getBooleanValue: 非boolean文字列でIllegalStateException")
    void getBooleanValue_nonBooleanString_throwsException() {
        SystemParameter param = SystemParameter.builder()
                .paramValue("42").build();

        assertThatThrownBy(param::getBooleanValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("42");
    }
}
