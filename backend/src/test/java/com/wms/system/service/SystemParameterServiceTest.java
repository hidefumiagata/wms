package com.wms.system.service;

import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.SystemParameter;
import com.wms.system.repository.SystemParameterRepository;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    void findAll_returnsList() {
        SystemParameter p1 = SystemParameter.builder().paramKey("KEY1").paramValue("V1").build();
        SystemParameter p2 = SystemParameter.builder().paramKey("KEY2").paramValue("V2").build();
        when(systemParameterRepository.findAll()).thenReturn(List.of(p1, p2));

        List<SystemParameter> result = systemParameterService.findAll();

        assertThat(result).hasSize(2);
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
                .hasMessageContaining("KEY1");
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
