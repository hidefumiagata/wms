package com.wms.system.service;

import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.SystemParameter;
import com.wms.system.repository.SystemParameterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemParameterServiceTest {

    @Mock private SystemParameterRepository systemParameterRepository;

    @InjectMocks private SystemParameterService systemParameterService;

    @Test
    void findByKey_exists_returnsParameter() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("LOGIN_FAILURE_LOCK_COUNT")
                .paramValue("5")
                .build();
        when(systemParameterRepository.findByParamKey("LOGIN_FAILURE_LOCK_COUNT"))
                .thenReturn(Optional.of(param));

        SystemParameter result = systemParameterService.findByKey("LOGIN_FAILURE_LOCK_COUNT");
        assertThat(result.getParamValue()).isEqualTo("5");
    }

    @Test
    void findByKey_notExists_throwsResourceNotFound() {
        when(systemParameterRepository.findByParamKey("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemParameterService.findByKey("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getIntValue_returnsIntegerValue() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("LOGIN_FAILURE_LOCK_COUNT")
                .paramValue("5")
                .build();
        when(systemParameterRepository.findByParamKey("LOGIN_FAILURE_LOCK_COUNT"))
                .thenReturn(Optional.of(param));

        assertThat(systemParameterService.getIntValue("LOGIN_FAILURE_LOCK_COUNT")).isEqualTo(5);
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("正常系: ソート済みリストを返す")
        void findAll_returnsSortedList() {
            SystemParameter p1 = SystemParameter.builder()
                    .paramKey("KEY1").paramValue("V1").category("INVENTORY").displayOrder(1).build();
            SystemParameter p2 = SystemParameter.builder()
                    .paramKey("KEY2").paramValue("V2").category("INVENTORY").displayOrder(2).build();
            SystemParameter p3 = SystemParameter.builder()
                    .paramKey("KEY3").paramValue("V3").category("SYSTEM").displayOrder(1).build();
            when(systemParameterRepository.findAllByOrderByCategoryAscDisplayOrderAsc())
                    .thenReturn(List.of(p1, p2, p3));

            List<SystemParameter> result = systemParameterService.findAll();

            assertThat(result).containsExactly(p1, p2, p3);
            verify(systemParameterRepository).findAllByOrderByCategoryAscDisplayOrderAsc();
        }

        @Test
        @DisplayName("正常系: 空リストを返す")
        void findAll_empty_returnsEmptyList() {
            when(systemParameterRepository.findAllByOrderByCategoryAscDisplayOrderAsc())
                    .thenReturn(List.of());

            List<SystemParameter> result = systemParameterService.findAll();

            assertThat(result).isEmpty();
        }
    }

    @Test
    void updateValue_success() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("SESSION_TIMEOUT_MINUTES").paramValue("60").build();
        when(systemParameterRepository.findByParamKey("SESSION_TIMEOUT_MINUTES"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemParameter result = systemParameterService.updateValue("SESSION_TIMEOUT_MINUTES", "30", 0);

        assertThat(result.getParamValue()).isEqualTo("30");
    }

    @Test
    void updateValue_notFound_throwsException() {
        when(systemParameterRepository.findByParamKey("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemParameterService.updateValue("UNKNOWN", "x", 0))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateValue_versionMismatch_throwsOptimisticLockConflictException() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("KEY1").paramValue("V1").build();
        param.setVersion(5);
        when(systemParameterRepository.findByParamKey("KEY1"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("KEY1", "V2", 3))
                .isInstanceOf(OptimisticLockConflictException.class)
                .hasMessage("他のユーザーによる更新が先行しました")
                .hasMessageNotContaining("key=")
                .hasMessageNotContaining("KEY1");
    }

    @Test
    void updateValue_integerType_invalidValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("TIMEOUT").paramValue("60").valueType("INTEGER").build();
        when(systemParameterRepository.findByParamKey("TIMEOUT"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("TIMEOUT", "not_a_number", 0))
                .isInstanceOf(com.wms.shared.exception.BusinessRuleViolationException.class)
                .hasMessageContaining("not_a_number");
    }

    @Test
    void updateValue_integerType_validValue_succeeds() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("TIMEOUT").paramValue("60").valueType("INTEGER").build();
        when(systemParameterRepository.findByParamKey("TIMEOUT"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemParameter result = systemParameterService.updateValue("TIMEOUT", "30", 0);

        assertThat(result.getParamValue()).isEqualTo("30");
    }

    @Test
    void updateValue_nonIntegerType_skipsValidation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("NAME").paramValue("old").valueType("STRING").build();
        when(systemParameterRepository.findByParamKey("NAME"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemParameter result = systemParameterService.updateValue("NAME", "new_value", 0);

        assertThat(result.getParamValue()).isEqualTo("new_value");
    }

    @Test
    @DisplayName("updateValue: BOOLEAN型 — trueで更新成功")
    void updateValue_booleanType_trueValue_succeeds() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("FEATURE_FLAG").paramValue("false").valueType("BOOLEAN").build();
        when(systemParameterRepository.findByParamKey("FEATURE_FLAG"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemParameter result = systemParameterService.updateValue("FEATURE_FLAG", "true", 0);

        assertThat(result.getParamValue()).isEqualTo("true");
    }

    @Test
    @DisplayName("updateValue: BOOLEAN型 — FALSE（大文字）で更新成功")
    void updateValue_booleanType_uppercaseValue_succeeds() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("FEATURE_FLAG").paramValue("true").valueType("BOOLEAN").build();
        when(systemParameterRepository.findByParamKey("FEATURE_FLAG"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemParameter result = systemParameterService.updateValue("FEATURE_FLAG", "FALSE", 0);

        assertThat(result.getParamValue()).isEqualTo("false");
    }

    @Test
    @DisplayName("updateValue: BOOLEAN型 — 不正値でBusinessRuleViolationException")
    void updateValue_booleanType_invalidValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("FEATURE_FLAG").paramValue("true").valueType("BOOLEAN").build();
        when(systemParameterRepository.findByParamKey("FEATURE_FLAG"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("FEATURE_FLAG", "yes", 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("yes");
    }

    @Test
    @DisplayName("updateValue: STRING型 — 空文字でBusinessRuleViolationException")
    void updateValue_stringType_emptyValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("NAME").paramValue("old").valueType("STRING").build();
        when(systemParameterRepository.findByParamKey("NAME"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("NAME", "", 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("STRING");
    }

    @Test
    @DisplayName("updateValue: STRING型 — nullでBusinessRuleViolationException")
    void updateValue_stringType_nullValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("NAME").paramValue("old").valueType("STRING").build();
        when(systemParameterRepository.findByParamKey("NAME"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("NAME", null, 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("updateValue: INTEGER型 — nullでBusinessRuleViolationException")
    void updateValue_integerType_nullValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("TIMEOUT").paramValue("60").valueType("INTEGER").build();
        when(systemParameterRepository.findByParamKey("TIMEOUT"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("TIMEOUT", null, 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("updateValue: BOOLEAN型 — nullでBusinessRuleViolationException")
    void updateValue_booleanType_nullValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("FEATURE_FLAG").paramValue("true").valueType("BOOLEAN").build();
        when(systemParameterRepository.findByParamKey("FEATURE_FLAG"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("FEATURE_FLAG", null, 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("updateValue: STRING型 — 501文字でBusinessRuleViolationException")
    void updateValue_stringType_over500chars_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("NAME").paramValue("old").valueType("STRING").build();
        when(systemParameterRepository.findByParamKey("NAME"))
                .thenReturn(Optional.of(param));

        String longValue = "a".repeat(501);
        assertThatThrownBy(() -> systemParameterService.updateValue("NAME", longValue, 0))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("updateValue: STRING型 — 500文字ちょうどは成功")
    void updateValue_stringType_exactly500chars_succeeds() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("NAME").paramValue("old").valueType("STRING").build();
        when(systemParameterRepository.findByParamKey("NAME"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String exactValue = "a".repeat(500);
        SystemParameter result = systemParameterService.updateValue("NAME", exactValue, 0);
        assertThat(result.getParamValue()).isEqualTo(exactValue);
    }

    @Test
    @DisplayName("updateValue: INTEGER型 — 負値(-1)でBusinessRuleViolationException")
    void updateValue_integerType_negativeValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("TIMEOUT").paramValue("60").valueType("INTEGER").build();
        when(systemParameterRepository.findByParamKey("TIMEOUT"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("TIMEOUT", "-1", 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("-1")
                .satisfies(e -> assertThat(((BusinessRuleViolationException) e).getErrorCode())
                        .isEqualTo("INVALID_PARAM_VALUE"));
    }

    @Test
    @DisplayName("updateValue: INTEGER型 — Integer.MIN_VALUE境界でBusinessRuleViolationException")
    void updateValue_integerType_minIntValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("TIMEOUT").paramValue("60").valueType("INTEGER").build();
        when(systemParameterRepository.findByParamKey("TIMEOUT"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("TIMEOUT", "-2147483648", 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("-2147483648")
                .satisfies(e -> assertThat(((BusinessRuleViolationException) e).getErrorCode())
                        .isEqualTo("INVALID_PARAM_VALUE"));
    }

    @Test
    @DisplayName("updateValue: INTEGER型 — 小数値(1.5)でBusinessRuleViolationException")
    void updateValue_integerType_decimalValue_throwsBusinessRuleViolation() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("TIMEOUT").paramValue("60").valueType("INTEGER").build();
        when(systemParameterRepository.findByParamKey("TIMEOUT"))
                .thenReturn(Optional.of(param));

        assertThatThrownBy(() -> systemParameterService.updateValue("TIMEOUT", "1.5", 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("1.5")
                .satisfies(e -> assertThat(((BusinessRuleViolationException) e).getErrorCode())
                        .isEqualTo("INVALID_PARAM_VALUE"));
    }

    @Test
    @DisplayName("updateValue: INTEGER型 — 0は正常に通る(境界値)")
    void updateValue_integerType_zeroValue_succeeds() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("TIMEOUT").paramValue("60").valueType("INTEGER").build();
        when(systemParameterRepository.findByParamKey("TIMEOUT"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemParameter result = systemParameterService.updateValue("TIMEOUT", "0", 0);

        assertThat(result.getParamValue()).isEqualTo("0");
    }

    @Test
    void updateValue_optimisticLockConflict_throwsException() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("KEY1").paramValue("V1").build();
        when(systemParameterRepository.findByParamKey("KEY1"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(SystemParameter.class.getName(), 1L));

        assertThatThrownBy(() -> systemParameterService.updateValue("KEY1", "V2", 0))
                .isInstanceOf(OptimisticLockConflictException.class);
    }
}
