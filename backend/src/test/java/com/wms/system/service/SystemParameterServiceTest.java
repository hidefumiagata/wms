package com.wms.system.service;

import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.SystemParameter;
import com.wms.system.repository.SystemParameterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
