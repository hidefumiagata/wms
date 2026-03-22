package com.wms.system.service;

import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.SystemParameter;
import com.wms.system.repository.SystemParameterRepository;
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

        SystemParameter result = systemParameterService.updateValue("SESSION_TIMEOUT_MINUTES", "30");

        assertThat(result.getParamValue()).isEqualTo("30");
    }

    @Test
    void updateValue_notFound_throwsException() {
        when(systemParameterRepository.findByParamKey("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemParameterService.updateValue("UNKNOWN", "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateValue_optimisticLockConflict_throwsException() {
        SystemParameter param = SystemParameter.builder()
                .paramKey("KEY1").paramValue("V1").build();
        when(systemParameterRepository.findByParamKey("KEY1"))
                .thenReturn(Optional.of(param));
        when(systemParameterRepository.save(any(SystemParameter.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(SystemParameter.class.getName(), 1L));

        assertThatThrownBy(() -> systemParameterService.updateValue("KEY1", "V2"))
                .isInstanceOf(OptimisticLockConflictException.class);
    }
}
