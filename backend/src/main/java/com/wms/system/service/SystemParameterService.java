package com.wms.system.service;

import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.SystemParameter;
import com.wms.system.repository.SystemParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SystemParameterService {

    private final SystemParameterRepository systemParameterRepository;

    public List<SystemParameter> findAll() {
        return systemParameterRepository.findAll();
    }

    public SystemParameter findByKey(String paramKey) {
        return systemParameterRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SYSTEM_PARAMETER_NOT_FOUND",
                        "システムパラメータが見つかりません: " + paramKey));
    }

    public int getIntValue(String paramKey) {
        return findByKey(paramKey).getIntValue();
    }

    @Transactional
    public SystemParameter updateValue(String paramKey, String paramValue) {
        SystemParameter param = findByKey(paramKey);
        param.setParamValue(paramValue);
        try {
            SystemParameter saved = systemParameterRepository.save(param);
            log.info("SystemParameter updated: key={}, value={}", paramKey, paramValue);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (key=" + paramKey + ")");
        }
    }
}
