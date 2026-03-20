package com.wms.system.service;

import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.SystemParameter;
import com.wms.system.repository.SystemParameterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemParameterService {

    private final SystemParameterRepository systemParameterRepository;

    public SystemParameter findByKey(String paramKey) {
        return systemParameterRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SYSTEM_PARAMETER_NOT_FOUND",
                        "システムパラメータが見つかりません: " + paramKey));
    }

    public int getIntValue(String paramKey) {
        return findByKey(paramKey).getIntValue();
    }
}
